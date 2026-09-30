-- Preload standardized Grade 10 Science competency tags.
-- Source: [G10] Science (alt ver).pdf, last updated April 10, 2026.
-- Root tags carry descriptions; competency tags remain short noun phrases.
-- Skills make each tag selectable only in its documented term context.

USE performance_assessment_v2_db;

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

START TRANSACTION;

CREATE TEMPORARY TABLE grade10_science_seed (
    root_tag_name VARCHAR(100) COLLATE utf8mb4_general_ci NOT NULL,
    root_description TEXT COLLATE utf8mb4_general_ci NOT NULL,
    competency_name VARCHAR(255) COLLATE utf8mb4_general_ci NOT NULL,
    term_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (root_tag_name, competency_name, term_order)
);

INSERT INTO grade10_science_seed (
    root_tag_name,
    root_description,
    competency_name,
    term_order
)
VALUES
    -- First Term: Chemical Reactions
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Reaction Indicators', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Acids, Bases, and Salts', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Chemical Indicators', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Reaction Types', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Environmental Chemical Reactions', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Chemical Equations', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Word Equations', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Formula Equations', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Atom Rearrangement', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Conservation of Mass', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Equation Balancing', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Reaction Rate Factors', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Exothermic Reactions', 1),
    ('Chemical Reactions',
     'Chemical changes, reaction patterns, equations, energy changes, and reaction rates',
     'Endothermic Reactions', 1),

    -- First Term: Homeostasis
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Homeostatic Balance', 1),
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Body Temperature', 1),
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Blood Glucose', 1),
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Blood Pressure', 1),
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Feedback Mechanisms', 1),
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Positive Feedback', 1),
    ('Homeostasis',
     'Biological balance, body-system regulation, and feedback mechanisms',
     'Negative Feedback', 1),

    -- First Term: Evolution
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Natural Selection', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Variation', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Heredity', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Isolation', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Adaptation', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Fossil Evidence', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Biogeography', 1),
    ('Evolution',
     'Natural selection, evolutionary processes, and supporting evidence',
     'Comparative Morphology', 1),

    -- Second Term: Population Ecology
    ('Population Ecology',
     'Population growth, resource limits, and ecosystem carrying capacity',
     'Limiting Factors', 2),
    ('Population Ecology',
     'Population growth, resource limits, and ecosystem carrying capacity',
     'Carrying Capacity', 2),
    ('Population Ecology',
     'Population growth, resource limits, and ecosystem carrying capacity',
     'Population Growth', 2),

    -- Second Term: Biotechnology
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Traditional Biotechnology', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Fermentation', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Modern Biotechnology', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Genetically Modified Organisms', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'In Vitro Fertilization', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Societal Implications', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Environmental Implications', 2),
    ('Biotechnology',
     'Traditional and modern biotechnology with societal, environmental, and ethical contexts',
     'Ethical Implications', 2),

    -- Second Term: Climate and Sustainability
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Global Warming Evidence', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Climate Change Evidence', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Greenhouse Gases', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Enhanced Global Warming', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'El Nino Southern Oscillation', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Weather System Impacts', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Local Climate Impacts', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Individual Climate Action', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Renewable Energy', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Climate Mitigation', 2),
    ('Climate and Sustainability',
     'Global climate change, weather interactions, mitigation, and sustainable resource use',
     'Sustainable Natural Resources', 2),

    -- Third Term: Plate Tectonics
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Plate Displacement', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Plate Measurement', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Plate Boundaries', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Philippine Tectonic Features', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Plate Motion Prediction', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Plate Movement Mechanisms', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Asthenosphere', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Oceanic Subduction', 3),
    ('Plate Tectonics',
     'Plate motion, boundaries, geological features, and tectonic mechanisms',
     'Mountain Formation', 3),

    -- Third Term: Motion and Collisions
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Projectile Variables', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Launch Angle', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Launch Velocity', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Projectile Height', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Projectile Range', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Elastic Collisions', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Inelastic Collisions', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Collision Forces', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Momentum', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Mass and Velocity', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Force-Time Relationship', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Collision Safety', 3),
    ('Motion and Collisions',
     'Projectile motion, momentum, collisions, forces, and safety applications',
     'Momentum Conservation', 3);

INSERT INTO root_tags (
    curriculum_id,
    root_tag_name,
    description,
    status
)
SELECT DISTINCT
    curriculum.curriculum_id,
    seed.root_tag_name,
    seed.root_description,
    'active'
FROM curriculums curriculum
CROSS JOIN grade10_science_seed seed
WHERE curriculum.status = 'active'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    status = VALUES(status);

INSERT INTO competency_tags (
    root_tag_id,
    competency_name
)
SELECT DISTINCT
    root.root_tag_id,
    seed.competency_name
FROM grade10_science_seed seed
JOIN root_tags root
    ON root.root_tag_name = seed.root_tag_name
WHERE root.status = 'active'
ON DUPLICATE KEY UPDATE
    competency_name = VALUES(competency_name);

INSERT INTO skills (
    competency_id,
    term_period_id,
    grade_level_id,
    subject_id
)
SELECT DISTINCT
    competency.competency_id,
    term.term_period_id,
    grade.grade_level_id,
    subject.subject_id
FROM grade10_science_seed seed
JOIN root_tags root
    ON root.root_tag_name = seed.root_tag_name
JOIN competency_tags competency
    ON competency.root_tag_id = root.root_tag_id
   AND competency.competency_name = seed.competency_name
JOIN academic_years academic_year
    ON academic_year.curriculum_id = root.curriculum_id
JOIN term_periods term
    ON term.academic_year_id = academic_year.academic_year_id
   AND term.term_order = seed.term_order
JOIN grade_levels grade
    ON grade.grade_level_name = 'Grade 10'
JOIN subjects subject
    ON subject.subject_name = 'Science'
WHERE root.status = 'active'
ON DUPLICATE KEY UPDATE
    subject_id = VALUES(subject_id);

DROP TEMPORARY TABLE grade10_science_seed;

COMMIT;

SELECT
    term.term_order,
    term.term_name,
    root.root_tag_name,
    root.description,
    competency.competency_name,
    grade.grade_level_name,
    subject.subject_name,
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
WHERE grade.grade_level_name = 'Grade 10'
  AND subject.subject_name = 'Science'
  AND root.root_tag_name IN (
      'Chemical Reactions',
      'Homeostasis',
      'Evolution',
      'Population Ecology',
      'Biotechnology',
      'Climate and Sustainability',
      'Plate Tectonics',
      'Motion and Collisions'
  )
ORDER BY
    term.term_order,
    root.root_tag_name,
    competency.competency_name;
