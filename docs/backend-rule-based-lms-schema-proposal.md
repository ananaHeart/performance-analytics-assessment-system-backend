# Backend Rule-Based LMS Schema Proposal

Documentation timeline note: This design note was drafted around late June 2026 during the deeper LMS planning work and updated in July 2026 to reflect the implemented database naming, backend behavior, TiDB-compatible SQL adjustments, July 27 database polishing notes, and the July 29 range-only mapping cleanup.

This document records the database and analytics design decision for deeper rule-based Least Mastered Skills (LMS) analytics. The active backend design uses `parent_competency_id` and `part_skill_mapping`. The earlier `skill_item` and `CUSTOM` mapping design is preserved below as historical design context, but it was superseded on July 29, 2026 by a range-only mapping decision. Mobile sync remains unchanged because deeper LMS is computed on the backend after uploaded checking results are stored.

July 12, 2026 deployment note:

During TiDB Cloud deployment testing, LMS-related SQL that used subqueries inside `JOIN ON` conditions was rewritten using normal joins and derived tables. This preserved the same analytics computation while making the deployed backend compatible with TiDB SQL limitations.

## 1. Scope and Restrictions

The analytics enhancement must keep the system as a paper-based assessment checking system with rule-based analytics only.

The design must not add AI suggestions, AI-generated lesson plans, OCR, a student portal, or an online exam feature.

Quarter support is intentionally excluded for now. Current LMS analytics can be computed using grade level, subject, class, assessment, test part, root competency, branch skill mapping, and item-level correctness. Lesson pacing may vary by teacher, section, school, and grading period, so quarter-topic relationships should not be hardcoded at this stage.

## 2. Current Tables Affected

The current schema already supports the protected core flow through these tables:

- `test`: stores the whole assessment.
- `test_part`: stores assessment sections, answer keys, item counts, and one root `competency_id`.
- `competency_tags`: stores competency references by grade level and subject.
- `test_result`: stores one checked result per test and student.
- `test_item_result`: stores item-level correctness per result and test part.
- `class`: connects teacher, subject, section, and academic year.
- `student`: stores learner identity.
- `user`: stores teacher and principal accounts.
- `sync_log`: stores mobile upload activity.

The proposed schema changes directly affect only:

- `competency_tags`
- `test_part`
- New table: `part_skill_mapping`
- Historical proposed table: `skill_item` for custom item mapping. This was later removed from the active design by the July 29, 2026 range-only cleanup.

No direct change is proposed for `test_result`, `test_item_result`, `class`, `student`, `user`, or `sync_log`.

## 3. Design Decision

Each `test_part` continues to have exactly one root competency.

Example:

- Test: English Quiz 1
- Test Part: Grammar, 20 items
- Root competency: Grammar
- Existing link: `test_part.competency_id = Grammar`

Branch skills or subcompetencies are modeled inside the existing `competency_tags` table through a nullable parent reference.

Example:

- Root: Grammar, `parent_competency_id = NULL`
- Branch: Verb, `parent_competency_id = Grammar competency_id`
- Branch: Noun, `parent_competency_id = Grammar competency_id`
- Branch: Conjunction, `parent_competency_id = Grammar competency_id`
- Branch: Subject-Verb Agreement, `parent_competency_id = Grammar competency_id`

The system should not create separate `root_competency`, `branch_competency`, `sub_skill`, or `skill_category` tables.

## 4. Exact Proposed Columns and Tables

### 4.1 `competency_tags.parent_competency_id`

Add:

- `parent_competency_id INT NULL`

Reason:

This allows the existing competency table to represent both root competencies and branch skills without introducing duplicate competency tables.

Relationships:

- `competency_tags.parent_competency_id` references `competency_tags.competency_id`
- Root competencies have `parent_competency_id = NULL`
- Branch skills have `parent_competency_id` pointing to the root competency

### 4.2 `part_skill_mapping`

Add a new table:

- `mapping_id`: primary key
- `test_part_id`: references `test_part.test_part_id`
- `competency_id`: references `competency_tags.competency_id`; this must be a branch skill
- `mapping_mode`: `RANGE` or `CUSTOM`
- `item_count`: number of covered items for the branch skill
- `start_item`: first item number for range mapping; nullable for custom mapping
- `end_item`: last item number for range mapping; nullable for custom mapping
- `created_at`: audit timestamp
- `updated_at`: audit timestamp

Reason:

This table maps a test part to branch skills or subcompetencies so LMS can be computed below the broad root competency level.

Important meaning:

- `test_part.competency_id` is the root competency.
- `part_skill_mapping.competency_id` is the branch skill competency.

### 4.3 `skill_item`

Add a new child table:

- `mapping_item_id`: primary key
- `mapping_id`: references `part_skill_mapping.mapping_id`
- `item_number`: exact item number assigned to the mapping
- `created_at`: audit timestamp

Reason:

Custom item mapping needs exact item numbers. Storing item numbers as comma-separated text like `1,4,9,13` is not normalized and makes validation and analytics queries harder. A child row per item keeps the database queryable and consistent.

## 5. Mapping Modes

### 5.1 Auto Range Mapping

Auto range mapping is the default mode. It is used when the test part is organized by skill groups.

Example for a 20-item Grammar test part:

- Verb: items 1-5
- Noun: items 6-10
- Conjunction: items 11-15
- Subject-Verb Agreement: items 16-20

For `RANGE` mode:

- `item_count` must be greater than 0.
- `start_item` is required.
- `end_item` is required.
- The backend can generate ranges from branch item counts.

### 5.2 Custom Item Mapping

Custom item mapping is optional. It is used when a teacher's test is mixed and skills are not arranged sequentially.

Example:

- Verb: items 1, 4, 9, 13
- Noun: items 2, 7, 10, 15
- Conjunction: items 3, 5, 8, 12

For `CUSTOM` mode:

- `item_count` must be greater than 0.
- `start_item` may be null.
- `end_item` may be null.
- Exact item numbers are stored in `skill_item`.

## 6. Validation Rules

General validation:

- Each test part must still have one root competency through `test_part.competency_id`.
- At least one branch skill must be covered for deeper LMS analytics.
- Branch skills with 0 items are considered Not Covered.
- Not Covered skills must not be included in LMS computation.
- Not Covered skills must not be shown as weak or least mastered.
- Saved mappings should have `item_count > 0`.
- A branch mapping competency should belong under the root competency of the same test part.

Auto range validation:

- Total mapped item count must equal `test_part.number_of_items`.
- Generated ranges must not overlap.
- Generated ranges must not exceed `test_part.number_of_items`.
- `start_item` must be greater than or equal to 1.
- `end_item` must be greater than or equal to `start_item`.

Custom item validation:

- Item numbers must be within 1 and `test_part.number_of_items`.
- The same item number must not be assigned to more than one branch skill within the same test part.
- Each custom mapping must have at least one item.
- Custom mapped item count must equal the number of rows saved in `skill_item` for that mapping.
- Unmapped items should be flagged before save when full branch-level LMS coverage is expected.

## 7. LMS Computation Direction

For branch-level LMS, mastery rate is computed as:

```text
Mastery Rate =
total correct answers for mapped items /
total possible answers for mapped items * 100
```

For each mapped branch skill:

1. Find the `part_skill_mapping` rows for the test part.
2. Determine covered item numbers from either range fields or `skill_item`.
3. Count `test_item_result` rows within the mapped item numbers.
4. Count correct answers where `is_correct = 1`.
5. Compute mastery percentage.
6. Rank lower mastery rates as LMS candidates.

Branch skills with 0 items are excluded from computation and may be shown only as Not Covered if the frontend requests that context later.

## 8. Approved Dynamic Analytics Outputs

The backend can later compute these dynamically from source data and mappings:

- Evidence-Based LMS Explanation
- Affected Students under each LMS
- Suggested Intervention Label
- Class Performance Heatmap
- Trend Across Assessments

These outputs should not be stored in permanent analytics tables.

Suggested intervention labels:

- 80-100%: Maintain
- 60-79%: Review
- 40-59%: Reteach
- Below 40%: Priority Intervention

Affected students can be identified as students whose branch skill mastery is below a mastery threshold, initially 75%. This threshold can become configurable later.

## 9. Backward Compatibility Plan

Old tests without branch mappings must continue to work.

If a test has no `part_skill_mapping` rows:

- Use the current part-level competency analytics.
- Treat `test_part.competency_id` as the analytics grouping.

If a test has branch mappings:

- Use branch-level LMS analytics.
- Keep the root competency available for summary grouping.

This prevents older assessment data and uploaded checked results from breaking.

## 10. Backend, Web, and Mobile Sync Risks

Backend risks:

- Analytics logic must choose between branch-level analytics and current part-level fallback.
- Validation must prevent impossible mappings, overlapping ranges, and duplicate custom item assignment.
- Existing tests may not have mappings, so queries must be null-safe and fallback-aware.

Web risks:

- The frontend must not assume every test has branch mapping.
- The assessment setup UI will eventually need a way to choose root competency branches and either range or custom item mapping.
- Analytics screens must label branch-level LMS separately from root competency summaries.

Mobile sync risks:

- Mobile download currently does not include mapping data.
- SQLite currently has no local equivalent for branch mappings.
- Upload payload should not change unless explicitly approved later.
- Download/upload/restore must remain untouched until mobile offline requirements are finalized.

For now, mobile sync remains unchanged.

## 11. Proposed SQL Draft

This SQL is a draft only. Do not apply it until migration is explicitly approved.

```sql
ALTER TABLE competency_tags
ADD COLUMN parent_competency_id INT NULL AFTER competency_id,
ADD INDEX idx_competency_tags_parent_id (parent_competency_id),
ADD CONSTRAINT fk_competency_tags_parent
    FOREIGN KEY (parent_competency_id)
    REFERENCES competency_tags (competency_id);
```

```sql
CREATE TABLE part_skill_mapping (
    mapping_id INT NOT NULL AUTO_INCREMENT,
    test_part_id INT NOT NULL,
    competency_id INT NOT NULL,
    mapping_mode ENUM('RANGE', 'CUSTOM') NOT NULL DEFAULT 'RANGE',
    item_count INT NOT NULL,
    start_item INT NULL,
    end_item INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_part_skill_mapping PRIMARY KEY (mapping_id),

    CONSTRAINT fk_part_skill_mapping_test_part
        FOREIGN KEY (test_part_id)
        REFERENCES test_part (test_part_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_part_skill_mapping_competency
        FOREIGN KEY (competency_id)
        REFERENCES competency_tags (competency_id),

    CONSTRAINT uk_part_skill_mapping_competency
        UNIQUE (test_part_id, competency_id),

    INDEX idx_part_skill_mapping_test_part_id (test_part_id),
    INDEX idx_part_skill_mapping_competency_id (competency_id),
    INDEX idx_part_skill_mapping_mode (mapping_mode),
    INDEX idx_part_skill_mapping_range (test_part_id, start_item, end_item),

    CONSTRAINT chk_part_skill_mapping_item_count
        CHECK (item_count > 0),

    CONSTRAINT chk_part_skill_mapping_range_values
        CHECK (
            (mapping_mode = 'RANGE' AND start_item IS NOT NULL AND end_item IS NOT NULL AND start_item >= 1 AND end_item >= start_item)
            OR
            (mapping_mode = 'CUSTOM' AND start_item IS NULL AND end_item IS NULL)
        )
);
```

```sql
CREATE TABLE skill_item (
    mapping_item_id INT NOT NULL AUTO_INCREMENT,
    mapping_id INT NOT NULL,
    item_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_skill_item PRIMARY KEY (mapping_item_id),

    CONSTRAINT fk_skill_item_mapping
        FOREIGN KEY (mapping_id)
        REFERENCES part_skill_mapping (mapping_id)
        ON DELETE CASCADE,

    CONSTRAINT uk_skill_item_mapping_item
        UNIQUE (mapping_id, item_number),

    INDEX idx_skill_item_mapping_id (mapping_id),
    INDEX idx_skill_item_item_number (item_number),

    CONSTRAINT chk_skill_item_number
        CHECK (item_number >= 1)
);
```

Database-level note:

The rule "same item number must not be assigned to multiple mappings under the same test part" is difficult to enforce cleanly with only the current normalized columns because `skill_item` does not directly store `test_part_id`. The safer first implementation is to enforce that rule in backend validation before saving. If stronger database protection is required later, `test_part_id` can be redundantly stored in `skill_item` with additional constraints, but that is not recommended unless the validation-only approach proves insufficient.

## 12. Tables Not Created

The following tables are intentionally not proposed now:

- `root_competency`: not needed because root competencies remain in `competency_tags`.
- `branch_competency`: not needed because branch skills remain in `competency_tags`.
- `sub_skill`: not needed because branch skills/subcompetencies are modeled through `parent_competency_id`.
- `skill_category`: not needed for the current LMS computation.
- `quarter`: not needed because quarter filtering is out of scope.
- `blueprint_template`: deferred because templates are a setup convenience, not required for LMS computation.
- `blueprint_template_skill_mapping`: deferred with blueprint templates.
- `lms_report`: not needed because LMS should be computed dynamically.
- `analytics_summary`: not needed because summaries should be computed from source data.
- `intervention_report`: not needed because intervention labels are computed classifications.
- `affected_students`: not needed because affected students are computed from item results.
- `trend_report`: not needed because trends are computed from assessment result history.
- `heatmap`: not needed because heatmaps are computed output, not source data.
- `chart_data`: not needed because charts should consume computed API output.
- `evidence_explanation`: not needed because explanation text is rule-based output generated from computed values.

## 13. Implementation Hold

Before implementation, the next approval should explicitly confirm whether to:

1. Add the proposed schema migration to `schema.sql`.
2. Add backend validation and repository/service support.
3. Add proposed API endpoints or extend existing endpoints.
4. Coordinate future web UI support.
5. Coordinate future mobile sync and SQLite support only if offline mapping data is required.

Until then, no migration or backend behavior change should be made.

## 14. Dated Implementation Status

| Date / Period | Status Update |
| --- | --- |
| Late June 2026 | Rule-based LMS schema design was drafted for deeper branch-skill analytics. |
| Late June 2026 | The selected naming settled on `parent_competency_id`, `part_skill_mapping`, and `skill_item`. |
| Late June to Early July 2026 | Backend endpoints for previewing, saving, and retrieving part skill mappings were implemented. |
| July 12, 2026 | TiDB-compatible SQL rewrite was applied for LMS and branch skill analytics queries. |
| July 13, 2026 | Documentation was updated to align the Gantt, API notes, and backend reference docs with the latest deployed backend workflow. |
| July 27, 2026 | Database redesign and polishing notes were appended for curriculum, intervention, answer key, term period, enrollment, and item analytics meaning. |
| July 29, 2026 | Part-skill mapping was simplified to range-only and the active backend dependency on `mapping_mode`, `CUSTOM`, and `skill_item` was removed. |

## 15. July 27, 2026 Database Polishing Notes

This section records the July 27, 2026 database redesign and polishing discussion. These notes are appended as design documentation and do not delete the earlier LMS schema decisions above.

### Academic Year and Term Period

The former `grading_period` concept is being reviewed as `term_period` in the data dictionary. The preferred design is to keep `status` as the main workflow control and make `start_date` and `end_date` supporting information.

Recommended rule:

- `academic_year.status` identifies the active school year.
- `term_period.status` identifies the active term or grading period.
- `start_date` and `end_date` should be nullable when the school cannot confidently predict exact dates.
- Only one academic year should be active at a time.
- Only one term period under the same academic year should be active at a time.

`period_order` and `period_name` are intentionally kept together:

- `period_order` supports sorting and system logic, such as 1, 2, 3, and 4.
- `period_name` supports readable UI labels, such as First Grading or Second Grading.
- A uniqueness rule such as `UNIQUE (academic_year_id, period_order)` should prevent duplicate period numbers in the same academic year.

### Curriculum Entity

A `curriculum` entity is recommended because it identifies the curriculum version used by the school system. This supports future changes in learning competencies without mixing old and new curriculum references.

Suggested table:

```text
curriculum
----------
curriculum_id
curriculum_name
curriculum_version
description
status
created_at
```

Recommended relationship direction:

- Link `curriculum` to `competency_tags` because competencies and skills depend on the curriculum version.
- Optionally link `academic_year` to `curriculum` if the school year needs to explicitly state which curriculum it follows.

### Intervention Entity

The current backend can generate teacher-facing intervention recommendations dynamically from assessment analytics. For database completeness, an `intervention` master list may be added as reference data.

Suggested table:

```text
intervention
------------
intervention_id
intervention_name
intervention_type
description
mastery_status
status
```

This should remain a master/reference table first. A separate intervention tracking table should only be added later if the system must record whether a teacher actually performed the intervention.

### Student Enrollment Relationship

`student_enrollment` should keep `section_id`, not `class_id`.

Reason:

- Student enrollment means a student belongs to a section for an academic year.
- A class in this system means teacher + subject + section + academic year.
- If enrollment used `class_id`, the same student would need duplicate enrollment rows for English, Math, Science, and other subjects.

Recommended relationship:

```text
student_enrollment -> section -> grade_level
class -> section
```

No `grade_level_id` is required in `student_enrollment` if `section` already stores `grade_level_id`.

### Test Result and Item Result Meaning

`test_result` stores the overall checked result for one student in one test.

Example meaning:

```text
Juan took Test 1 and received a total score.
```

`test_item_result` stores per-student, per-item correctness.

Example meaning:

```text
Juan, Part 1, Item 1, correct or wrong.
```

Analytics aggregates `test_item_result` to produce item-level outputs such as:

```text
Item 1: 12/60 students answered correctly.
```

This supports item analytics, LMS computation, affected learner identification, and teacher intervention recommendations.

### Answer Key Design Review

The current system stores answer keys in `test_part.answer_key` as text. A normalized `answer_key` table was reviewed for long-term database quality.

Suggested normalized table:

```text
answer_key
----------
answer_key_id
test_part_id
item_number
correct_answer
points
```

Important clarification:

- `item_number` identifies which question inside the test part the answer belongs to.
- A 30-item test part would have 30 answer key rows, but this is not heavy compared with `test_item_result`.
- `answer_key` rows are per test item, not per student.
- `test_item_result` rows are the ones that grow per student and per item.

This design is better for long-term support of multiple choice, true or false, identification, and enumeration item types. It should only be implemented after checking backend, frontend, mobile sync, and existing SQL impact.

## 16. July 29, 2026 Range-Only Mapping Finalization

This section records the approved cleanup after comparing the final data dictionary with the implemented backend and database.

### Final Decision

`part_skill_mapping` is range-only.

The active table design is:

```text
part_skill_mapping
------------------
mapping_id
test_part_id
competency_id
item_count
start_item
end_item
created_at
updated_at
```

Removed from the active design:

- `mapping_mode`
- `CUSTOM` mapping behavior
- `skill_item`

### Reason

The final data dictionary uses item ranges for mapping a test part to branch skills. This is simpler for teachers, easier to explain during defense, and sufficient for the intended assessment workflow where a test part is organized by skill ranges.

Example:

```text
Items 1-5   -> Active and Passive Voice
Items 6-10  -> Past Tense
Items 11-15 -> Past Perfect Tense
```

### Backend Update Summary

The backend was updated so:

- Part-skill preview/save accepts range mapping only.
- Existing compatibility fields such as `mappingMode: "RANGE"` may still appear in API response DTOs for frontend compatibility.
- Analytics no longer joins `skill_item`.
- LMS, affected learners, intervention, and student skill mastery now compute branch skill performance using `tir.item_number BETWEEN psm.start_item AND psm.end_item`.

### Database Update Summary

Local MySQL and TiDB cleanup direction:

- Remove `skill_item`.
- Remove `part_skill_mapping.mapping_mode`.
- Keep `item_count`, `start_item`, and `end_item`.
- Do not recreate the database.
- Do not delete assessment, student, result, or enrollment records.

### Verified Local API Tests

The following local API checks passed after the range-only cleanup:

- `POST /api/part-skill-mappings/preview`
- `POST /api/part-skill-mappings/save`
- `GET /api/part-skill-mappings/test-parts/1`
- `GET /api/analytics/lms?testId=1`

This confirms that the range-only backend path still supports part-skill mapping and LMS analytics.
