"""Prepare/recheck SQL artifacts offline; this script never opens a DB connection.

Usage: python prepare_constraint_repair.py --package <reviewed package directory>
       python prepare_constraint_repair.py --package <directory> --check
The only files written are generated artifacts in this script's directory.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

TARGET = "performance_assessment_v3_test"
ROOT = Path(__file__).resolve().parents[2]
OUTPUT = Path(__file__).resolve().parent
SOURCE = ROOT / "docs/recovery/performance_assessment_v3_schema_recovery.sql"
RULES = [
    ("questions", "chk_questions_expected_response_count", "hardening"),
    ("answer_sheet_regions", "chk_answer_sheet_regions_expected_count", "hardening"),
    ("answer_sheet_regions", "chk_answer_sheet_regions_line_count", "hardening"),
    ("answer_sheet_regions", "chk_answer_sheet_regions_response_shape", "hardening"),
    ("answer_attachments", "chk_answer_attachments_normalized_source", "hardening"),
    ("answer_attachments", "chk_answer_attachments_hold", "hardening"),
    ("answer_attachments", "chk_answer_attachments_purge_state", "hardening"),
    ("answer_attachments", "chk_answer_attachments_held_not_purged", "hardening"),
    ("answer_sheet_pages", "chk_answer_sheet_pages_qr_payload", "hardening"),
    ("answer_sheet_pages", "chk_answer_sheet_pages_qr_hash", "hardening"),
    ("scan_pages", "chk_scan_pages_qr_payload", "hardening"),
    ("scan_pages", "chk_scan_pages_qr_hash", "hardening"),
    ("term_periods", "chk_term_periods_order", "calendar"),
    ("class_assignment_schedules", "chk_class_assignment_schedules_day", "calendar"),
    ("class_assignment_schedules", "chk_class_assignment_schedules_time", "calendar"),
    ("class_assignment_schedules", "chk_class_assignment_schedules_dates", "calendar"),
    ("class_assignment_schedules", "chk_class_assignment_schedules_timezone", "calendar"),
    ("class_assignment_schedules", "chk_class_assignment_schedules_archive", "calendar"),
    ("test_assignments", "chk_test_assignments_outside_schedule_confirmation", "calendar"),
]
MIGRATION_NUMBERS = [0, 1, 2, 3, 6, 9, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]


def expression_at(text: str, offset: int) -> str:
    depth = 1
    quote = None
    position = offset
    while depth:
        character = text[position]
        if quote:
            if character == quote:
                quote = None
        elif character in "'`\"":
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
        position += 1
    return normalized(text[offset:position - 1])


def reconstruct_named_checks() -> tuple[list[dict], list[dict], list[dict]]:
    """Replay CHECK metadata from SQL text, including replacements; execute no SQL."""
    constraints = {}
    history = []
    sources = []
    identifier = r"`?([A-Za-z_][A-Za-z0-9_]*)`?"
    event_pattern = re.compile(
        r"(?P<table_ddl>^[ \t]*(?:CREATE|ALTER)\s+TABLE\s+(?:IF NOT EXISTS\s+)?" + identifier + r")"
        r"|(?P<table_drop>^[ \t]*DROP\s+TABLE\s+(?:IF EXISTS\s+)?" + identifier + r")"
        r"|(?P<constraint_drop>DROP\s+(?:CONSTRAINT|CHECK)\s+" + identifier + r")"
        r"|(?P<constraint_add>\bCONSTRAINT\s+" + identifier + r"\s+CHECK\s*\()",
        re.M | re.I,
    )
    for number in MIGRATION_NUMBERS:
        files = list((ROOT / "docs/migrations/v3").glob(f"V3_{number:03d}_*.sql"))
        files = [path for path in files if "smoke_test" not in path.name and "negative_fixtures" not in path.name]
        assert len(files) == 1, (number, files)
        path = files[0]
        raw = path.read_text(encoding="utf-8-sig")
        # Blank comments preserve offsets and source line numbers.
        text = re.sub(r"(?m)^\s*--[^\n]*", lambda match: re.sub(r"[^\n]", " ", match[0]), raw)
        text = re.sub(r"/\*.*?\*/", lambda match: re.sub(r"[^\n]", " ", match[0]), text, flags=re.S)
        table = None
        for event in event_pattern.finditer(text):
            if event["table_ddl"]:
                table = event[2]
            elif event["table_drop"]:
                dropped = event[4]
                constraints = {name: rule for name, rule in constraints.items() if rule["table"] != dropped}
            elif event["constraint_drop"]:
                name = event[6]
                # These files drop foreign keys too; only present CHECKs enter our metadata history.
                if name in constraints:
                    history.append({"action": "drop", "name": name, "table": table,
                                    "source_file": str(path.relative_to(ROOT)).replace("\\", "/")})
                    del constraints[name]
            else:
                name = event[8]
                assert table and name not in constraints, (path.name, name, "unreviewed duplicate")
                expression = expression_at(text, event.end())
                group = next((group for candidate_table, candidate_name, group in RULES
                              if candidate_name == name and candidate_table == table), "other_release")
                constraints[name] = {
                    "table": table, "name": name, "group": group,
                    "source_file": str(path.relative_to(ROOT)).replace("\\", "/"),
                    "source_line": text[:event.start()].count("\n") + 1,
                    "source_expression": expression,
                }
                history.append({"action": "add", "name": name, "table": table,
                                "source_file": str(path.relative_to(ROOT)).replace("\\", "/")})
        sources.append({"file": str(path.relative_to(ROOT)).replace("\\", "/"),
                        "sha256": digest(path), "named_checks_after_migration": len(constraints)})
        if number == 14:
            assert len(constraints) == 80
            recovery_names = set(re.findall(r"CONSTRAINT `([^`]+)`\s+CHECK\s*\(", SOURCE.read_text(encoding="utf-8-sig")))
            assert recovery_names == set(constraints), "Recovery/migration baseline names differ"
    assert len(constraints) == 124
    replacement = constraints["chk_student_answers_response"]
    assert replacement["source_file"].endswith("V3_020_mobile_written_verification_DRAFT.sql")
    assert "response_evidence_attachment_id IS NOT NULL" in replacement["source_expression"]
    assert "chk_answer_keys_option" not in constraints and "chk_student_answers_option" not in constraints
    return list(constraints.values()), sources, history


def validate_referenced_columns(schema: str, rules: list[dict]) -> dict[str, list[str]]:
    tables = {}
    for match in re.finditer(r"(?ms)^CREATE TABLE `([^`]+)`.*?(?=^CREATE TABLE|\Z)", schema):
        tables[match[1]] = dict(re.findall(r"(?m)^\s*`([^`]+)`\s+([^\n]+)", match[0]))
    sql_words = {"and", "or", "not", "is", "null", "true", "false", "between", "in", "as",
                 "char", "binary", "character", "set", "decimal", "signed", "unsigned", "regexp"}
    references = {}
    for rule in rules:
        expression = re.sub(r"'(?:[^']|'')*'", " ", rule["cloud_expression"])
        function_names = set(re.findall(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(", expression.lower()))
        identifiers = set(re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", expression.lower()))
        columns = identifiers - function_names - sql_words
        assert rule["table"] in tables, rule["table"]
        unknown = columns - set(tables[rule["table"]])
        assert not unknown, (rule["name"], sorted(unknown))
        assert all("AUTO_INCREMENT" not in tables[rule["table"]][column].upper() for column in columns), rule["name"]
        references[rule["name"]] = sorted(columns)
    return references


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalized(expression: str) -> str:
    return re.sub(r"\s+", " ", expression).strip()


def split_sql(text: str, delimiter: str = ";") -> list[str]:
    """Lexical splitting only. SQL is never interpreted or executed."""
    parts, buffer = [], []
    quote = None
    depth = 0
    index = 0
    while index < len(text):
        character = text[index]
        if quote:
            buffer.append(character)
            if character == "\\" and quote != "`" and index + 1 < len(text):
                index += 1
                buffer.append(text[index])
            elif character == quote:
                if index + 1 < len(text) and text[index + 1] == quote:
                    index += 1
                    buffer.append(text[index])
                else:
                    quote = None
        elif character in "'`\"":
            quote = character
            buffer.append(character)
        elif text.startswith("--", index) and (index + 2 == len(text) or text[index + 2].isspace()):
            newline = text.find("\n", index)
            index = len(text) if newline < 0 else newline
            buffer.append("\n")
            continue
        elif character == "(":
            depth += 1
            buffer.append(character)
        elif character == ")":
            depth -= 1
            assert depth >= 0
            buffer.append(character)
        elif character == delimiter and (delimiter == ";" or depth == 0):
            part = "".join(buffer).strip()
            if part:
                parts.append(part)
            buffer = []
        else:
            buffer.append(character)
        index += 1
    assert depth == 0 and quote is None
    part = "".join(buffer).strip()
    if part:
        parts.append(part)
    return parts


def reference_seed_counts(seed_path: Path) -> dict[str, int]:
    selected = {"paper_sizes", "question_types", "omr_templates", "omr_template_regions", "performance_rule_sets"}
    rows = {name: [] for name in selected}
    for statement in split_sql(seed_path.read_text(encoding="utf-8-sig")):
        match = re.fullmatch(r"INSERT INTO `(\w+)`\s*\((.*?)\)\s*VALUES\s*(.*)", statement, re.S)
        if not match or match[1] not in selected:
            continue
        columns = re.findall(r"`(\w+)`", match[2])
        for row in split_sql(match[3], ","):
            assert row.startswith("(") and row.endswith(")")
            values = split_sql(row[1:-1], ",")
            assert len(columns) == len(values)
            # Selected count fields contain only unescaped numbers or simple labels.
            rows[match[1]].append(dict(zip(columns, (value.strip("'") for value in values))))
    templates = rows["omr_templates"]
    paper_codes = {row["paper_size_id"]: row["paper_size_code"] for row in rows["paper_sizes"]}
    active_templates = [row for row in templates if row["template_status"] == "active"]
    approved_templates = [row for row in active_templates if
                          paper_codes[row["paper_size_id"]] == "A4" and (
                              (row["template_code"], row["template_version"]) == ("OMR-A4-10-MC-CTX-V2", "2")
                              or ((row["template_code"], row["template_version"], row["qr_payload_version"])
                                  == ("OMR-A4-DYNAMIC-CTX-V3", "3", "3")))]
    validated = {row["omr_template_id"] for row in templates
                 if (row["template_code"], row["template_version"]) == ("OMR-A4-10-MC-CTX-V2", "2")}
    expected_papers = {("A4", "595.276", "841.890"), ("US_LETTER", "612.000", "792.000"),
                       ("US_LEGAL", "612.000", "1008.000")}
    return {
        "paper_sizes": len(rows["paper_sizes"]),
        "required_paper_size_seeds": sum((row["paper_size_code"], row["width_points"], row["height_points"])
                                          in expected_papers for row in rows["paper_sizes"]),
        "validated_v2_regions": sum(row["omr_template_id"] in validated for row in rows["omr_template_regions"]),
        "active_question_types": sum(row["is_active"] == "1" for row in rows["question_types"]),
        "active_templates": len(active_templates),
        "approved_active_templates": len(approved_templates),
        "unapproved_active_templates": len(active_templates) - len(approved_templates),
        "active_performance_rule_sets": sum(row["rule_status"] == "active" for row in rows["performance_rule_sets"]),
    }


def qualified(table: str) -> str:
    return f"`{TARGET}`.`{table}`"


def expected_union(rules: list[dict]) -> str:
    return "\nUNION ALL\n".join(
        f"SELECT '{rule['table']}' AS table_name, '{rule['name']}' AS constraint_name"
        for rule in rules
    )


def violations(rules: list[dict]) -> str:
    # SQL CHECK rejects only FALSE, so NOT(expr) deliberately does not reject UNKNOWN.
    return "\nUNION ALL\n".join(
        f"SELECT '{rule['name']}' AS check_name, COUNT(*) AS violating_rows\n"
        f"FROM {qualified(rule['table'])}\nWHERE NOT ({rule['cloud_expression']})"
        for rule in rules
    ) + ";\n"


def header(purpose: str) -> str:
    return (
        f"-- {purpose}\n-- Target is exactly {TARGET} on the reviewed TiDB cluster.\n"
        "-- OFFLINE PREPARATION ONLY: this file has not been executed.\n"
        "-- Never run against the working local database or another cloud database.\n\n"
    )


def identity() -> str:
    return (
        f"SELECT VERSION() AS server_version, DATABASE() AS selected_database,\n"
        f"       DATABASE() = '{TARGET}' AS correct_selected_database,\n"
        "       @@GLOBAL.tidb_enable_check_constraint AS check_feature_enabled,\n"
        "       @@SESSION.foreign_key_checks AS foreign_key_checks_enabled;\n\n"
    )


def count_summary(check_count: int) -> str:
    return (
        "SELECT 'table_count' AS check_name, 79 AS expected, COUNT(*) AS actual\n"
        f"FROM information_schema.tables WHERE table_schema = '{TARGET}' AND table_type = 'BASE TABLE'\n"
        "UNION ALL\nSELECT 'foreign_key_count', 200, COUNT(*)\n"
        f"FROM information_schema.table_constraints WHERE constraint_schema = '{TARGET}' AND constraint_type = 'FOREIGN KEY'\n"
        f"UNION ALL\nSELECT 'check_count', {check_count}, COUNT(*)\n"
        f"FROM information_schema.check_constraints WHERE constraint_schema = '{TARGET}'\n"
        "UNION ALL\nSELECT 'unique_count', 118, COUNT(*)\n"
        f"FROM information_schema.table_constraints WHERE constraint_schema = '{TARGET}' AND constraint_type = 'UNIQUE';\n\n"
    )


def prepare(package: Path) -> dict[str, str]:
    manifest_path = package / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    assert manifest["target_database"] == TARGET
    assert manifest["tables"] == 79 and manifest["foreign_keys"] == 200
    assert manifest["check_constraints"] == 7
    schema_path = package / "01_schema.sql"
    foreign_path = package / "03_foreign_keys.sql"
    schema = schema_path.read_text(encoding="utf-8-sig")
    foreign = foreign_path.read_text(encoding="utf-8-sig")
    assert digest(schema_path) == manifest["files"]["01_schema.sql"]
    assert digest(foreign_path) == manifest["files"]["03_foreign_keys.sql"]
    assert digest(package / "02_seed.sql") == manifest["files"]["02_seed.sql"]
    seed_counts = reference_seed_counts(package / "02_seed.sql")
    assert seed_counts == {
        "paper_sizes": 3, "required_paper_size_seeds": 3, "validated_v2_regions": 15,
        "active_question_types": 5, "active_templates": 2, "approved_active_templates": 2,
        "unapproved_active_templates": 0, "active_performance_rule_sets": 4,
    }, seed_counts
    assert len(re.findall(r"\bCHECK\s*\(", schema, re.I)) == 7
    assert len(re.findall(r"\bUNIQUE KEY\b", schema)) == 118
    original = Path(manifest["source_export"])
    assert digest(original) == manifest["source_sha256"]
    original_text = original.read_text(encoding="utf-8-sig")
    assert len(re.findall(r"\bCHECK\s*\(", original_text, re.I)) == 7
    assert not re.search(r"CONSTRAINT\s+`[^`]+`\s+CHECK", original_text, re.I)

    reconstructed, migration_sources, migration_history = reconstruct_named_checks()
    rules = []
    for recovered in reconstructed:
        table, name, group = recovered["table"], recovered["name"], recovered["group"]
        assert name not in schema and name not in foreign
        expression = recovered["source_expression"]
        adaptation = None
        cloud = expression
        if name.endswith("_qr_hash"):
            assert expression == "BINARY qr_payload_hash REGEXP '^[0-9a-f]{64}$' AND qr_payload_hash = SHA2(qr_payload, 256)"
            cloud = (
                "REGEXP_LIKE(`qr_payload_hash`, '^[0-9a-f]{64}$', 'c') "
                "AND `qr_payload_hash` = SHA2(`qr_payload`,256)"
            )
            adaptation = (
                "TiDB rejects binary-string regex arguments. Use a character-string regex "
                "with explicit case-sensitive flag; retain lowercase 64-hex and exact SHA-256 checks. "
                "Pending TiDB DDL and negative-write acceptance."
            )
        rules.append({
            "table": table, "name": name, "group": group,
            "source_file": recovered["source_file"],
            "source_line": recovered["source_line"], "source_expression": expression,
            "cloud_expression": cloud, "adaptation": adaptation,
            "live_verification": "not_run",
        })
    assert len(rules) == 124
    assert sum(rule["adaptation"] is not None for rule in rules) == 2
    assert sum(rule["group"] == "hardening" for rule in rules) == 12
    assert sum(rule["group"] == "calendar" for rule in rules) == 7
    referenced_columns = validate_referenced_columns(schema, rules)
    union = expected_union(rules)

    precheck = header("Read-only precheck: SELECT and SHOW only; require every expected result before repair.")
    precheck += identity()
    precheck += "-- Before first application: 79 / 200 / 7 / 118. Any different state requires review.\n"
    precheck += count_summary(7)
    precheck += "-- All 124 rows must report present = 0 before first application.\n"
    precheck += (
        "SELECT expected.table_name, expected.constraint_name, COUNT(actual.constraint_name) AS present\n"
        f"FROM (\n{union}\n) expected\n"
        "LEFT JOIN information_schema.tidb_check_constraints actual\n"
        f"  ON actual.constraint_schema = '{TARGET}'\n"
        " AND actual.table_name = expected.table_name AND actual.constraint_name = expected.constraint_name\n"
        "GROUP BY expected.table_name, expected.constraint_name ORDER BY expected.table_name, expected.constraint_name;\n\n"
    )
    precheck += (
        "-- Read-only expression compatibility probe; expected values: 1, 0, 64, 2, 256.\n"
        "-- This checks scalar function execution, not whether ALTER CHECK accepts the expression.\n"
        "SELECT REGEXP_LIKE(SHA2('abc', 256), '^[0-9a-f]{64}$', 'c') AS valid_lowercase_hash,\n"
        "       REGEXP_LIKE(UPPER(SHA2('abc', 256)), '^[0-9a-f]{64}$', 'c') AS uppercase_hash_rejected,\n"
        "       CHAR_LENGTH(SHA2('abc', 256)) AS hash_length,\n"
        "       OCTET_LENGTH('ab') AS payload_minimum, OCTET_LENGTH(REPEAT('a', 256)) AS payload_maximum;\n\n"
        "-- All violating_rows must be zero. NULL/UNKNOWN follows the original SQL CHECK semantics.\n"
    )
    precheck += violations(rules)

    repair = header("PENDING APPROVAL AND LIVE PRECHECK: additive constraint-only repair candidate.")
    repair += (
        "-- Run only after reviewing 00_precheck.sql results, confirming TiDB identity,\n"
        "-- and approving these 124 ALTER statements. Each is fully qualified to the target.\n"
        "-- DDL commits independently; stop on the first error. Never ignore an error or rerun blindly.\n"
        "-- No transaction wrapper, row rewrite, column change, seed operation, or global setting change.\n"
        "-- The repair restores all 124 named release CHECKs; preserve the 7 original JSON CHECKs.\n"
        "-- Retain term_order 1..4 to support legacy quarters; application validates 3-term years.\n"
        "-- Cloud QR-regex variants below require TiDB acceptance; never replace with NOT ENFORCED.\n\n"
    )
    for rule in rules:
        repair += f"-- Source: {rule['source_file']}:{rule['source_line']}\n"
        if rule["adaptation"]:
            repair += "-- TiDB adaptation: replace binary REGEXP with case-sensitive REGEXP_LIKE.\n"
        repair += (
            f"ALTER TABLE {qualified(rule['table'])}\n"
            f"    ADD CONSTRAINT `{rule['name']}` CHECK (\n"
            f"        {rule['cloud_expression']}\n    ) ENFORCED;\n\n"
        )

    verify = header("Read-only post-repair verification: metadata and row-count checks; no test writes.")
    verify += identity()
    verify += count_summary(131)
    verify += (
        "-- All 124 constraints must appear on the expected table. Inspect their exact clauses.\n"
        "SELECT expected.table_name, expected.constraint_name, actual.check_clause,\n"
        "       actual.constraint_name IS NOT NULL AS present\n"
        f"FROM (\n{union}\n) expected\n"
        "LEFT JOIN information_schema.tidb_check_constraints actual\n"
        f"  ON actual.constraint_schema = '{TARGET}'\n"
        " AND actual.table_name = expected.table_name AND actual.constraint_name = expected.constraint_name\n"
        "ORDER BY expected.table_name, expected.constraint_name;\n\n"
        "-- CHECK_CONSTRAINTS exposes definitions, not enforcement. Verify SHOW CREATE has\n"
        "-- no NOT ENFORCED marker for any original or restored check; keep feature enabled.\n"
    )
    original_check_tables = [match[1] for match in re.finditer(
        r"(?ms)^CREATE TABLE `([^`]+)`.*?(?=^CREATE TABLE|\Z)", schema) if re.search(r"\bCHECK\s*\(", match[0], re.I)]
    for table in dict.fromkeys([rule["table"] for rule in rules] + original_check_tables):
        verify += f"SHOW CREATE TABLE {qualified(table)};\n"
    verify += "\n-- The original seven JSON-validity checks must also remain present and enforced.\n"
    verify += (
        "SELECT table_name, constraint_name, check_clause FROM information_schema.tidb_check_constraints\n"
        f"WHERE constraint_schema = '{TARGET}'\n"
        "ORDER BY table_name, constraint_name;\n\n"
        "-- All counts must remain zero; this query shows no row contents.\n"
    )
    verify += violations(rules)
    verify += (
        "\n-- Reference-seed readiness counts inferred from the reviewed package, to recheck live.\n"
        f"SELECT 'paper_sizes' AS check_name, 3 AS expected, COUNT(*) AS actual FROM {qualified('paper_sizes')}\n"
        f"UNION ALL SELECT 'active_question_types', 5, COUNT(*) FROM {qualified('question_types')} WHERE is_active = 1\n"
        f"UNION ALL SELECT 'active_templates', 2, COUNT(*) FROM {qualified('omr_templates')} WHERE template_status = 'active'\n"
        f"UNION ALL SELECT 'active_rule_sets', 4, COUNT(*) FROM {qualified('performance_rule_sets')} WHERE rule_status = 'active'\n"
        f"UNION ALL SELECT 'validated_v2_regions', 15, COUNT(*) FROM {qualified('omr_template_regions')} region\n"
        f"JOIN {qualified('omr_templates')} template ON template.omr_template_id = region.omr_template_id\n"
        "WHERE template.template_code = 'OMR-A4-10-MC-CTX-V2' AND template.template_version = '2';\n"
    )

    audit = {
        "status": "offline_reviewed_live_acceptance_pending",
        "target_database": TARGET,
        "database_connections": 0,
        "sql_statements_executed": 0,
        "rules_added_if_applied": 124,
        "hardening_checks_restored": 12,
        "calendar_checks_restored": 7,
        "existing_export_checks": 7,
        "other_release_checks_restored": 105,
        "expected_total_checks_after_repair": 131,
        "local_release_expected_checks": 131,
        "full_release_named_check_set_reconstructed": True,
        "full_release_named_check_gap_after_repair": 0,
        "semantic_equivalence_status": "Original expressions retained except two documented QR-regex adaptations; live verification pending",
        "source_export_sha256": manifest["source_sha256"],
        "package_schema_sha256": digest(schema_path),
        "package_foreign_keys_sha256": digest(foreign_path),
        "repository_schema_source_sha256": digest(SOURCE),
        "migration_sources": migration_sources,
        "check_add_drop_history": migration_history,
        "referenced_columns_validated_against_export": referenced_columns,
        "offline_reference_seed_counts": seed_counts,
        "expected_readiness_after_repair": {
            "tables": 79, "foreign_keys": 200, "checks": 131, "unique_constraints": 118,
            "required_dynamic_tables": 6, "required_hardening_columns": 16,
            "required_hardening_constraints": 15, "school_scoped_section_rules": 3,
            "academic_calendar_columns": 20, "academic_calendar_constraints": 16,
            "paper_sizes": 3, "required_paper_size_seeds": 3, "validated_v2_regions": 15,
            "active_question_types": 5, "active_templates": 2, "approved_active_templates": 2,
            "unapproved_active_templates": 0, "active_performance_rule_sets": 4,
        },
        "sources": [
            "https://docs.pingcap.com/tidb/stable/constraints/",
            "https://docs.pingcap.com/tidb/stable/string-functions/",
            "https://docs.pingcap.com/tidb/stable/encryption-and-compression-functions/",
            "https://docs.pingcap.com/tidb/stable/information-schema-check-constraints/",
            "https://docs.pingcap.com/tidb/stable/information-schema-tidb-check-constraints/",
        ],
        "rules": rules,
    }

    for sql in (precheck, verify):
        without_comments = re.sub(r"(?m)^--.*$", "", sql)
        statements = [part.strip() for part in without_comments.split(";") if part.strip()]
        assert all(re.match(r"(?:SELECT|SHOW CREATE TABLE)\b", statement) for statement in statements)
        assert not re.search(r"\b(?:UPDATE|DELETE|INSERT|ALTER|DROP|CREATE|TRUNCATE|SET)\b", without_comments.replace("SHOW CREATE TABLE", "SHOW_TABLE"), re.I)
    repair_statements = [part.strip() for part in re.sub(r"(?m)^--.*$", "", repair).split(";") if part.strip()]
    assert len(repair_statements) == 124
    assert all(statement.startswith(f"ALTER TABLE `{TARGET}`.") for statement in repair_statements)
    assert all(statement.endswith(") ENFORCED") for statement in repair_statements)
    assert not re.search(r"\b(?:DROP|INSERT|UPDATE|DELETE|TRUNCATE|MODIFY|RENAME|SET|USE)\b", "\n".join(repair_statements), re.I)
    assert "CHARACTER SET binary" not in "\n".join(repair_statements)
    repair = repair.rstrip() + "\n"
    outputs = {"00_precheck.sql": precheck, "01_add_required_checks.sql": repair, "02_verify.sql": verify}
    audit["sql_file_sha256"] = {name: hashlib.sha256(value.encode()).hexdigest() for name, value in outputs.items()}
    outputs["constraint-plan.json"] = json.dumps(audit, indent=2) + "\n"
    return outputs


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True, type=Path)
    parser.add_argument("--check", action="store_true", help="Validate existing artifacts without writes")
    arguments = parser.parse_args()
    artifacts = prepare(arguments.package)
    for name, contents in artifacts.items():
        destination = OUTPUT / name
        if arguments.check:
            assert destination.read_text(encoding="utf-8") == contents, f"Artifact drift: {name}"
        else:
            destination.write_text(contents, encoding="utf-8", newline="\n")
    print(json.dumps({"offline_validation": "passed", "checks_planned": 124,
                      "expected_total_checks": 131, "sql_executed": 0,
                      "mode": "check" if arguments.check else "prepare"}))
