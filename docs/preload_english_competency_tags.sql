-- Preload standardized English competency tags for assessment mapping.
-- Root tags carry descriptions; competency tags remain short phrases.
-- Skills make each tag selectable for Grade 7 English in every configured term.

USE performance_assessment_v2_db;

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

START TRANSACTION;

INSERT INTO root_tags (
    curriculum_id,
    root_tag_name,
    description,
    status
)
SELECT
    curriculum.curriculum_id,
    seed.root_tag_name,
    seed.description,
    'active'
FROM curriculums curriculum
CROSS JOIN (
    SELECT
        _utf8mb4'Grammar' COLLATE utf8mb4_general_ci AS root_tag_name,
        _utf8mb4'Sentence structure, grammar rules, and language conventions.'
            COLLATE utf8mb4_general_ci AS description
    UNION ALL
    SELECT
        _utf8mb4'Reading Comprehension' COLLATE utf8mb4_general_ci,
        _utf8mb4'Understanding, interpreting, and analyzing written texts.'
            COLLATE utf8mb4_general_ci
    UNION ALL
    SELECT
        _utf8mb4'Vocabulary Development' COLLATE utf8mb4_general_ci,
        _utf8mb4'Word meaning, word formation, and contextual usage.'
            COLLATE utf8mb4_general_ci
) seed
WHERE curriculum.status = 'active'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    status = VALUES(status);

INSERT INTO competency_tags (
    root_tag_id,
    competency_name
)
SELECT
    root.root_tag_id,
    seed.competency_name
FROM root_tags root
JOIN (
    SELECT
        _utf8mb4'Grammar' COLLATE utf8mb4_general_ci AS root_tag_name,
        _utf8mb4'Subject-Verb Agreement' COLLATE utf8mb4_general_ci AS competency_name
    UNION ALL SELECT 'Grammar', 'Verb Tenses'
    UNION ALL SELECT 'Grammar', 'Parts of Speech'
    UNION ALL SELECT 'Reading Comprehension', 'Main Idea'
    UNION ALL SELECT 'Reading Comprehension', 'Supporting Details'
    UNION ALL SELECT 'Reading Comprehension', 'Making Inferences'
    UNION ALL SELECT 'Vocabulary Development', 'Context Clues'
    UNION ALL SELECT 'Vocabulary Development', 'Affixes'
    UNION ALL SELECT 'Vocabulary Development', 'Denotation and Connotation'
) seed
    ON root.root_tag_name = seed.root_tag_name COLLATE utf8mb4_general_ci
WHERE root.status = 'active'
ON DUPLICATE KEY UPDATE
    competency_name = VALUES(competency_name);

INSERT INTO skills (
    competency_id,
    term_period_id,
    grade_level_id,
    subject_id
)
SELECT
    competency.competency_id,
    term.term_period_id,
    grade.grade_level_id,
    subject.subject_id
FROM competency_tags competency
JOIN root_tags root
    ON root.root_tag_id = competency.root_tag_id
JOIN academic_years academic_year
    ON academic_year.curriculum_id = root.curriculum_id
JOIN term_periods term
    ON term.academic_year_id = academic_year.academic_year_id
JOIN grade_levels grade
    ON grade.grade_level_name = 'Grade 7'
JOIN subjects subject
    ON subject.subject_name = 'English'
WHERE root.status = 'active'
  AND root.root_tag_name IN (
      'Grammar',
      'Reading Comprehension',
      'Vocabulary Development'
  )
ON DUPLICATE KEY UPDATE
    subject_id = VALUES(subject_id);

COMMIT;

SELECT
    root.root_tag_name,
    root.description,
    competency.competency_name,
    grade.grade_level_name,
    subject.subject_name,
    term.term_name,
    skill.skill_id
FROM skills skill
JOIN competency_tags competency
    ON competency.competency_id = skill.competency_id
JOIN root_tags root
    ON root.root_tag_id = competency.root_tag_id
JOIN grade_levels grade
    ON grade.grade_level_id = skill.grade_level_id
JOIN subjects subject
    ON subject.subject_id = skill.subject_id
JOIN term_periods term
    ON term.term_period_id = skill.term_period_id
WHERE root.root_tag_name IN (
    'Grammar',
    'Reading Comprehension',
    'Vocabulary Development'
)
ORDER BY
    term.term_order,
    root.root_tag_name,
    competency.competency_name;
