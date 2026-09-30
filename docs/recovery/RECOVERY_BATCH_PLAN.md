# Recovery batch plan — 66 remaining tables

Status: PLAN ONLY. Nothing below has been executed. `roles` is already recovered
and verified in `performance_assessment_v3_db` and is excluded from everything
below.

## Architecture (per your instruction)

For each table: create a PK-only shell in `recovery_stage` →
`DISCARD TABLESPACE` → copy the real `.ibd` from `recovery_source` (never the
original `data` folder) → `IMPORT TABLESPACE` → verify row count and sample
rows in staging → `INSERT INTO` the real, fully-structured table in
`performance_assessment_v3_db` with an explicit column list → fix
`AUTO_INCREMENT` → verify final count matches staging count → move to the next
table. The real database's tables are never touched structurally — only
`recovery_stage`'s throwaway shells get their indexes stripped.

`recovery_stage` schema file: `docs/recovery/recovery_stage_schema.sql` (66
tables, verified to exactly match the 66 real `.ibd` names minus `roles`).

## Dependency order (66 tables, topologically valid — every table appears after everything it references)

**Phase 1 — pure lookup/reference (no foreign keys at all):**
1. addresses
2. genders
3. grade_levels
4. educational_attainments
5. majors
6. suffixes
7. statuses
8. subjects
9. curriculums
10. question_types
11. paper_sizes

**Phase 2 — first-level dependents:**
12. root_tags (→curriculums)
13. school_profiles (→addresses)

**Phase 3:**
14. competency_tags (→root_tags)
15. academic_years (→curriculums, school_profiles)
16. sections (→grade_levels, school_profiles)

**Phase 4 — core parent entities:**
17. classes (→academic_years, sections)
18. users (→addresses, genders, majors, educational_attainments, roles✓, statuses, school_profiles, suffixes)

**Phase 5 — user-dependent tables:**
19. audit_logs
20. login_attempts
21. notifications
22. verification_challenges
23. user_mfa_factors
24. auth_sessions (→users, user_mfa_factors)
25. mfa_recovery_codes (→user_mfa_factors)
26. mfa_authentication_challenges (→users, user_mfa_factors)
27. students
28. class_assignments (→classes, subjects, users)
29. class_assignment_schedules (→class_assignments, users)
30. term_periods (→academic_years, users)

**Phase 6:**
31. skills (→competency_tags, term_periods, grade_levels, subjects)
32. interventions (→skills)
33. sf1_imports (→school_profiles, academic_years, classes, users)

**Phase 7 — child/transaction tables (enrollment, rubrics, assessment definition):**
34. class_lists (→classes, students, academic_years, sf1_imports, users; self-ref) ⚠
35. sf1_import_items (→sf1_imports, students, class_lists)
36. performance_rule_sets (→school_profiles, users)
37. rubrics (→school_profiles, users)
38. rubric_criteria (→rubrics)
39. tests (→term_periods, school_profiles, users; self-ref) ⚠
40. test_assignments (→tests, class_assignments, users)
41. test_parts (→tests, question_types)
42. questions (→test_parts, question_types, rubrics)
43. question_options (→questions)
44. accepted_answers (→questions)
45. answer_keys (→questions, question_options, rubrics)
46. part_skill_mappings (→test_parts, skills)

**Phase 8 — answer-sheet/OMR structure:**
47. omr_templates (→question_types, users, paper_sizes)
48. omr_template_regions (→omr_templates, question_types)
49. answer_sheet_versions (→test_assignments, paper_sizes, users; self-ref) ⚠
50. answer_sheet_pages (→answer_sheet_versions, omr_templates)
51. answer_sheet_regions (→answer_sheet_versions, answer_sheet_pages, omr_template_regions, questions, test_parts, question_types)

**Phase 9 — scanning/detection (junction/detail tables):**
52. scan_sessions (→users, omr_templates, test_assignments, class_lists, answer_sheet_versions; self-ref) ⚠
53. scan_pages (→scan_sessions, answer_sheet_pages, omr_templates; self-ref) ⚠
54. omr_detections (→questions, scan_sessions, scan_pages, answer_sheet_regions)

**Phase 10 — results and scoring:**
55. test_results (→class_lists, test_assignments, performance_rule_sets)
56. intervention_results (→test_results, interventions, performance_rule_sets, users)
57. student_intervention_cases (→school_profiles, students, academic_years, term_periods, class_lists, skills, test_results, performance_rule_sets, users)
58. student_intervention_updates (→student_intervention_cases, users)
59. test_result_scans (→users, scan_sessions, test_results)
60. student_answers (→questions, test_results, users, question_options)
61. answer_attachments (→student_answers, scan_sessions, scan_pages, answer_sheet_regions)
62. scan_verifications (→scan_sessions, scan_pages, users)
63. answer_verifications (→student_answers, users, answer_attachments)
64. answer_rubric_scores (→student_answers, rubric_criteria, answer_verifications, users)
65. syncs (→test_assignments, users)
66. sync_items (→syncs, test_results)

⚠ = self-referencing foreign key (see risk notes below).

## Risk flags

**Self-referencing foreign keys (5 tables):** `class_lists.previous_class_list_id`,
`tests.source_test_id`, `answer_sheet_versions.source_answer_sheet_version_id`,
`scan_sessions.supersedes_scan_session_id`, `scan_pages.supersedes_scan_page_id`.
A row can reference another row in the *same* table that may not exist yet
mid-transfer. Plan: run each of these five tables' `INSERT ... SELECT` transfer
with `SET FOREIGN_KEY_CHECKS=0` for that single statement, then immediately
re-enable and run an orphan check (query for any self-reference pointing to a
non-existent ID) before moving on.

**Virtual generated columns (7 tables):** `academic_years.active_school_id`,
`term_periods.active_academic_year_id`, `class_lists.active_academic_year_id`,
`user_mfa_factors.active_user_id`, `scan_pages.current_page_number`,
`omr_detections.legacy_scan_session_id`/`legacy_question_id`,
`test_result_scans.selected_result_id`. These are VIRTUAL (not stored), so they
take zero space in the `.ibd` and are computed automatically — they must be
**excluded** from every `INSERT` column list (you cannot assign a value to a
virtual generated column directly; MariaDB computes it from the other columns
you do insert).

**Highest index-count tables (most to double-check when stripping):**
`scan_pages` (4 unique keys), `test_result_scans` (3 unique keys),
`academic_years`, `term_periods`, `user_mfa_factors`, `omr_detections` (2 unique
keys each plus FK-support indexes).

**Files modified closest to whatever caused the corruption (elevated risk of a
partially-flushed page):** `audit_logs`, `login_attempts`,
`mfa_authentication_challenges`, `user_mfa_factors`, `users` (all Sep 14
17:35), `mfa_recovery_codes` (Sep 14 14:18), `auth_sessions` (Sep 14 18:39,
the single most recent write in the whole database). These are worth extra
scrutiny after import — compare recovered row count against what you remember
being reasonable, and spot-check a few rows for obviously truncated/garbled
values before trusting them fully.

**`audit_logs.details` column** uses a non-default collation
(`utf8mb4_bin` vs. the table's `utf8mb4_general_ci`) — preserved exactly in
the staging schema; flagging so it isn't "corrected" to the table default by
mistake if this is ever hand-edited.

## Command template (shown for one plain table, one self-ref table, one generated-column table)

**Plain table, e.g. `addresses`:**
```
mysql -h 127.0.0.1 -P 3307 -u root recovery_stage -e "ALTER TABLE addresses DISCARD TABLESPACE;"
cp "C:\xampp2\mysql\recovery_source\performance_assessment_v3_db\addresses.ibd" "C:\xampp2\mysql\data_clean\recovery_stage\addresses.ibd"
mysql -h 127.0.0.1 -P 3307 -u root recovery_stage -e "ALTER TABLE addresses IMPORT TABLESPACE; SHOW WARNINGS;"
mysql -h 127.0.0.1 -P 3307 -u root recovery_stage -e "SELECT COUNT(*) FROM addresses;"
-- after verification, transfer (all columns except none excluded here):
mysql -h 127.0.0.1 -P 3307 -u root -e "
INSERT INTO performance_assessment_v3_db.addresses
  (address_id, country_code, region_code, region_name, province_code, province_name,
   city_municipality_code, city_municipality_name, barangay_code, barangay_name,
   address_line, postal_code, address_source, created_at, updated_at)
SELECT address_id, country_code, region_code, region_name, province_code, province_name,
       city_municipality_code, city_municipality_name, barangay_code, barangay_name,
       address_line, postal_code, address_source, created_at, updated_at
  FROM recovery_stage.addresses;
ALTER TABLE performance_assessment_v3_db.addresses AUTO_INCREMENT =
  (SELECT MAX(address_id)+1 FROM performance_assessment_v3_db.addresses);
"
mysql -h 127.0.0.1 -P 3307 -u root -e "SELECT COUNT(*) FROM performance_assessment_v3_db.addresses;"
```

**Self-referencing table, e.g. `tests`:** identical shape, but the transfer step is:
```
mysql -h 127.0.0.1 -P 3307 -u root -e "
SET FOREIGN_KEY_CHECKS=0;
INSERT INTO performance_assessment_v3_db.tests (test_id, test_uuid, school_id, created_by_user_id,
  version_number, source_test_id, term_period_id, test_name, test_type, instructions, total_items,
  status, published_at, content_locked_at, completed_at, archived_at, created_at, updated_at)
SELECT test_id, test_uuid, school_id, created_by_user_id, version_number, source_test_id,
       term_period_id, test_name, test_type, instructions, total_items, status, published_at,
       content_locked_at, completed_at, archived_at, created_at, updated_at
  FROM recovery_stage.tests;
SET FOREIGN_KEY_CHECKS=1;
SELECT COUNT(*) AS orphaned_self_refs FROM performance_assessment_v3_db.tests t
  WHERE t.source_test_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM performance_assessment_v3_db.tests p WHERE p.test_id = t.source_test_id);
"
```

**Generated-column table, e.g. `academic_years`:** the transfer column list
excludes `active_school_id` entirely (it's computed automatically once
`school_id` and `status` are inserted):
```
INSERT INTO performance_assessment_v3_db.academic_years
  (academic_year_id, school_id, curriculum_id, year_name, start_date, end_date,
   status, created_at, updated_at)
SELECT academic_year_id, school_id, curriculum_id, year_name, start_date, end_date,
       status, created_at, updated_at
  FROM recovery_stage.academic_years;
```

The full, literal command set for all 66 tables follows this same three
patterns (plain / self-ref / generated-column) with each table's real column
list substituted in. I have not generated all 66 in full yet — I can generate
the complete literal script next if you approve this plan and order, since it's
mechanical once the plan itself is confirmed correct.

## Recovery log format (per your spec)

A CSV will be produced with these columns, one row per table, updated live as
each table completes:

```
table_name,source_ibd_size,import_status,recovered_row_count,final_row_count,verification_status,error_message
```

## What I need from you before running anything

1. Confirm the phase order above is acceptable (or flag any table you want moved).
2. Confirm the self-referencing-FK handling (temporary `FOREIGN_KEY_CHECKS=0` per table, immediately re-enabled, with an orphan check).
3. Confirm you want the full literal 66-table script generated next, or want to review/approve phase-by-phase (e.g., approve Phase 1's 11 tables, see results, then approve Phase 2, etc.) rather than all-at-once.
