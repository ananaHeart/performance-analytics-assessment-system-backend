INSERT IGNORE INTO school_profile (school_id, school_name, region_division)
VALUES ('SRNHS', 'San Roque National High School', 'Region XII');

INSERT IGNORE INTO academic_year (year_name, start_date, end_date, status)
VALUES ('2025-2026', '2025-06-01', '2026-03-31', 'Active');

INSERT IGNORE INTO grade_level (grade_level_name)
VALUES ('Grade 7'),
       ('Grade 8'),
       ('Grade 9'),
       ('Grade 10');

INSERT IGNORE INTO subject (subject_code, subject_name)
VALUES ('ENG', 'English'),
       ('MATH', 'Mathematics'),
       ('SCI', 'Science');

INSERT IGNORE INTO competency_tags (grade_level_id, subject_id, competency_name)
VALUES
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Operations on Integers'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Rational Numbers'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Algebraic Expressions'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Linear Equations'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Ratio and Proportion'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Percent Problems'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Lines and Angles'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Polygons'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Statistics and Data'
    ),
    (
        (SELECT grade_level_id FROM grade_level WHERE grade_level_name = 'Grade 7'),
        (SELECT subject_id FROM subject WHERE subject_name = 'Mathematics'),
        'Probability'
    );

-- Grade 7 competency hierarchy used by Assessment Setup item mapping.
-- Root tags remain NULL; branch skills point to their root through parent_competency_id.
UPDATE competency_tags ct
JOIN grade_level gl ON gl.grade_level_id = ct.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = ct.subject_id
SET ct.parent_competency_id = NULL
WHERE s.subject_name = 'English'
  AND ct.competency_name IN (
      'Grammar',
      'Vocabulary',
      'Reading Comprehension',
      'Viewing Comprehension',
      'Writing',
      'Literature',
      'Oral Communication'
  );

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Grammar'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Active and Passive Voice',
    'Past Tense',
    'Past Perfect Tense',
    'Direct and Reported Speech',
    'Phrases',
    'Clauses',
    'Sentence Structure',
    'Coherent Sentences'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Vocabulary'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Word Meaning', 'Analogy', 'Context Clues');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Reading Comprehension'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Reading Strategies',
    'Skimming',
    'Scanning',
    'Close Reading',
    'Summarizing Information',
    'Citing Evidence',
    'Reacting to Text'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Viewing Comprehension'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Viewed Text Features',
    'Genre and Purpose',
    'Audience Awareness',
    'Truthfulness and Accuracy'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Writing'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Academic Writing', 'Informative Essay');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Literature'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Literary Conflict',
    'Culture and History',
    'Philippine Literature',
    'Identity in Literature'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Oral Communication'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'English'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Oral Language',
    'Listening Strategies',
    'Public Questions',
    'Interpersonal Communication'
);

UPDATE competency_tags ct
JOIN grade_level gl ON gl.grade_level_id = ct.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = ct.subject_id
SET ct.parent_competency_id = NULL
WHERE s.subject_name = 'Mathematics'
  AND ct.competency_name IN ('Sets', 'Real Numbers', 'Measurement', 'Algebra', 'Geometry', 'Statistics');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Sets'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Mathematics'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Well-Defined Sets',
    'Subsets',
    'Universal Set',
    'Null Set',
    'Cardinality',
    'Union and Intersection',
    'Venn Diagram'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Real Numbers'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Mathematics'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Absolute Value',
    'Integers',
    'Integer Operations',
    'Properties of Operations',
    'Operations on Integers',
    'Rational Numbers',
    'Decimal and Fraction Form',
    'Square Roots',
    'Irrational Numbers',
    'Scientific Notation'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Measurement'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Mathematics'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Length', 'Mass and Weight', 'Volume', 'Time', 'Angle', 'Temperature', 'Unit Conversion');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Algebra'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Mathematics'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Mathematical Phrases',
    'Algebraic Expressions',
    'Constants and Variables',
    'Terms and Polynomials',
    'Evaluating Expressions',
    'Polynomial Addition',
    'Polynomial Subtraction',
    'Laws of Exponents',
    'Polynomial Multiplication',
    'Polynomial Division',
    'Linear Equations',
    'Linear Inequalities'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Geometry'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Mathematics'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Points Lines and Planes',
    'Subsets of a Line',
    'Kinds of Angles',
    'Angle Relationships',
    'Parallel Lines',
    'Transversal Angles',
    'Interior and Exterior Angles',
    'Lines and Angles',
    'Polygons',
    'Circle Terms'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Statistics'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Mathematics'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Statistical Questions',
    'Data Gathering',
    'Frequency Distribution',
    'Data Graphs',
    'Statistics and Data',
    'Mean',
    'Median',
    'Mode',
    'Range',
    'Variance',
    'Standard Deviation',
    'Probability'
);

UPDATE competency_tags ct
JOIN grade_level gl ON gl.grade_level_id = ct.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = ct.subject_id
SET ct.parent_competency_id = NULL
WHERE s.subject_name = 'Science'
  AND ct.competency_name IN (
      'Scientific Investigation',
      'Matter',
      'Microscope',
      'Cells',
      'Reproduction',
      'Ecosystem',
      'Motion',
      'Waves',
      'Light',
      'Heat',
      'Electric Charges',
      'Earth Science'
  );

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Scientific Investigation'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Investigation Components', 'Scientific Problem Solving');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Matter'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Elements and Compounds',
    'Substances',
    'Mixtures',
    'Properties of Mixtures',
    'Solutions',
    'Saturated Solutions',
    'Unsaturated Solutions',
    'Solution Concentration'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Microscope'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Microscope Parts', 'Microscope Functions', 'Specimen Focusing');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Cells'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Biological Organization',
    'Plant Cells',
    'Animal Cells',
    'Cell Organelles',
    'Cell Functions'
);

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Reproduction'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Sexual Reproduction', 'Asexual Reproduction');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Ecosystem'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Biotic Factors', 'Abiotic Factors', 'Ecological Relationships', 'Abiotic Changes');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Motion'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Distance', 'Displacement', 'Speed', 'Velocity', 'Acceleration', 'Motion Graphs');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Waves'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Wave Energy', 'Sound Characteristics', 'Wavelength', 'Amplitude');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Light'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Color of Light', 'Light Intensity');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Heat'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Heat Transfer');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Electric Charges'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN ('Charging Processes');

UPDATE competency_tags child
JOIN competency_tags root
  ON root.grade_level_id = child.grade_level_id
 AND root.subject_id = child.subject_id
 AND root.competency_name = 'Earth Science'
JOIN grade_level gl ON gl.grade_level_id = child.grade_level_id AND gl.grade_level_name = 'Grade 7'
JOIN subject s ON s.subject_id = child.subject_id AND s.subject_name = 'Science'
SET child.parent_competency_id = root.competency_id
WHERE child.competency_name IN (
    'Coordinate System',
    'Earth Resources',
    'Atmosphere',
    'Land Breeze and Sea Breeze',
    'Monsoons',
    'Seasons',
    'Solar Eclipse',
    'Lunar Eclipse'
);
