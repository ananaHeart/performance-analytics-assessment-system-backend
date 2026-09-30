-- Read-only precheck: SELECT and SHOW only; require every expected result before repair.
-- Target is exactly performance_assessment_v3_test on the reviewed TiDB cluster.
-- OFFLINE PREPARATION ONLY: this file has not been executed.
-- Never run against the working local database or another cloud database.

SELECT VERSION() AS server_version, DATABASE() AS selected_database,
       DATABASE() = 'performance_assessment_v3_test' AS correct_selected_database,
       @@GLOBAL.tidb_enable_check_constraint AS check_feature_enabled,
       @@SESSION.foreign_key_checks AS foreign_key_checks_enabled;

-- Before first application: 79 / 200 / 7 / 118. Any different state requires review.
SELECT 'table_count' AS check_name, 79 AS expected, COUNT(*) AS actual
FROM information_schema.tables WHERE table_schema = 'performance_assessment_v3_test' AND table_type = 'BASE TABLE'
UNION ALL
SELECT 'foreign_key_count', 200, COUNT(*)
FROM information_schema.table_constraints WHERE constraint_schema = 'performance_assessment_v3_test' AND constraint_type = 'FOREIGN KEY'
UNION ALL
SELECT 'check_count', 7, COUNT(*)
FROM information_schema.check_constraints WHERE constraint_schema = 'performance_assessment_v3_test'
UNION ALL
SELECT 'unique_count', 118, COUNT(*)
FROM information_schema.table_constraints WHERE constraint_schema = 'performance_assessment_v3_test' AND constraint_type = 'UNIQUE';

-- All 124 rows must report present = 0 before first application.
SELECT expected.table_name, expected.constraint_name, COUNT(actual.constraint_name) AS present
FROM (
SELECT 'academic_years' AS table_name, 'chk_academic_years_dates' AS constraint_name
UNION ALL
SELECT 'omr_detections' AS table_name, 'chk_omr_detections_confidence' AS constraint_name
UNION ALL
SELECT 'student_answers' AS table_name, 'chk_student_answers_points' AS constraint_name
UNION ALL
SELECT 'test_parts' AS table_name, 'chk_test_parts_number_of_items' AS constraint_name
UNION ALL
SELECT 'test_parts' AS table_name, 'chk_test_parts_points_per_item' AS constraint_name
UNION ALL
SELECT 'test_results' AS table_name, 'chk_test_results_scores' AS constraint_name
UNION ALL
SELECT 'users' AS table_name, 'chk_users_teaching_start_month' AS constraint_name
UNION ALL
SELECT 'users' AS table_name, 'chk_users_teaching_start_year' AS constraint_name
UNION ALL
SELECT 'students' AS table_name, 'chk_students_lrn' AS constraint_name
UNION ALL
SELECT 'term_periods' AS table_name, 'chk_term_periods_dates' AS constraint_name
UNION ALL
SELECT 'term_periods' AS table_name, 'chk_term_periods_override' AS constraint_name
UNION ALL
SELECT 'performance_rule_sets' AS table_name, 'chk_performance_rule_sets_dates' AS constraint_name
UNION ALL
SELECT 'rubrics' AS table_name, 'chk_rubrics_total_points' AS constraint_name
UNION ALL
SELECT 'rubric_criteria' AS table_name, 'chk_rubric_criteria_maximum_points' AS constraint_name
UNION ALL
SELECT 'test_assignments' AS table_name, 'chk_test_assignments_dates' AS constraint_name
UNION ALL
SELECT 'questions' AS table_name, 'chk_questions_maximum_points' AS constraint_name
UNION ALL
SELECT 'questions' AS table_name, 'chk_questions_maximum_response_length' AS constraint_name
UNION ALL
SELECT 'question_options' AS table_name, 'chk_question_options_order' AS constraint_name
UNION ALL
SELECT 'accepted_answers' AS table_name, 'chk_accepted_answers_points' AS constraint_name
UNION ALL
SELECT 'answer_keys' AS table_name, 'chk_answer_keys_strategy' AS constraint_name
UNION ALL
SELECT 'part_skill_mappings' AS table_name, 'chk_part_skill_mappings_range' AS constraint_name
UNION ALL
SELECT 'class_lists' AS table_name, 'chk_class_lists_end_state' AS constraint_name
UNION ALL
SELECT 'omr_detections' AS table_name, 'chk_omr_detections_option' AS constraint_name
UNION ALL
SELECT 'test_results' AS table_name, 'chk_test_results_percentage' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_file_size' AS constraint_name
UNION ALL
SELECT 'scan_verifications' AS table_name, 'chk_scan_verifications_reason' AS constraint_name
UNION ALL
SELECT 'answer_verifications' AS table_name, 'chk_answer_verifications_points' AS constraint_name
UNION ALL
SELECT 'answer_verifications' AS table_name, 'chk_answer_verifications_reason' AS constraint_name
UNION ALL
SELECT 'answer_rubric_scores' AS table_name, 'chk_answer_rubric_scores_points' AS constraint_name
UNION ALL
SELECT 'intervention_results' AS table_name, 'chk_intervention_results_mastery' AS constraint_name
UNION ALL
SELECT 'student_intervention_cases' AS table_name, 'chk_student_intervention_cases_dates' AS constraint_name
UNION ALL
SELECT 'student_intervention_updates' AS table_name, 'chk_student_intervention_updates_status_change' AS constraint_name
UNION ALL
SELECT 'syncs' AS table_name, 'chk_syncs_request_item_count' AS constraint_name
UNION ALL
SELECT 'verification_challenges' AS table_name, 'chk_verification_challenges_attempts' AS constraint_name
UNION ALL
SELECT 'verification_challenges' AS table_name, 'chk_verification_challenges_expiry' AS constraint_name
UNION ALL
SELECT 'user_mfa_factors' AS table_name, 'chk_user_mfa_factors_totp' AS constraint_name
UNION ALL
SELECT 'mfa_authentication_challenges' AS table_name, 'chk_mfa_authentication_challenges_attempts' AS constraint_name
UNION ALL
SELECT 'mfa_authentication_challenges' AS table_name, 'chk_mfa_authentication_challenges_expiry' AS constraint_name
UNION ALL
SELECT 'auth_sessions' AS table_name, 'chk_auth_sessions_mfa_state' AS constraint_name
UNION ALL
SELECT 'paper_sizes' AS table_name, 'chk_paper_sizes_dimensions' AS constraint_name
UNION ALL
SELECT 'omr_templates' AS table_name, 'chk_omr_templates_item_count' AS constraint_name
UNION ALL
SELECT 'omr_templates' AS table_name, 'chk_omr_templates_option_count' AS constraint_name
UNION ALL
SELECT 'omr_templates' AS table_name, 'chk_omr_templates_print_scale' AS constraint_name
UNION ALL
SELECT 'omr_template_regions' AS table_name, 'chk_omr_template_regions_bounds' AS constraint_name
UNION ALL
SELECT 'omr_template_regions' AS table_name, 'chk_omr_template_regions_question_type' AS constraint_name
UNION ALL
SELECT 'answer_sheet_versions' AS table_name, 'chk_answer_sheet_versions_minimum_questions' AS constraint_name
UNION ALL
SELECT 'answer_sheet_versions' AS table_name, 'chk_answer_sheet_versions_pages' AS constraint_name
UNION ALL
SELECT 'answer_sheet_versions' AS table_name, 'chk_answer_sheet_versions_generation' AS constraint_name
UNION ALL
SELECT 'answer_sheet_versions' AS table_name, 'chk_answer_sheet_versions_content_version' AS constraint_name
UNION ALL
SELECT 'answer_sheet_versions' AS table_name, 'chk_answer_sheet_versions_pdf_size' AS constraint_name
UNION ALL
SELECT 'answer_sheet_pages' AS table_name, 'chk_answer_sheet_pages_number' AS constraint_name
UNION ALL
SELECT 'answer_sheet_regions' AS table_name, 'chk_answer_sheet_regions_item_numbers' AS constraint_name
UNION ALL
SELECT 'scan_sessions' AS table_name, 'chk_scan_sessions_capture_model' AS constraint_name
UNION ALL
SELECT 'scan_sessions' AS table_name, 'chk_scan_sessions_page_counts' AS constraint_name
UNION ALL
SELECT 'scan_pages' AS table_name, 'chk_scan_pages_numbers' AS constraint_name
UNION ALL
SELECT 'scan_pages' AS table_name, 'chk_scan_pages_rotation' AS constraint_name
UNION ALL
SELECT 'omr_detections' AS table_name, 'chk_omr_detections_page_region' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_owner' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_region_page' AS constraint_name
UNION ALL
SELECT 'answer_rubric_scores' AS table_name, 'chk_answer_rubric_scores_maximum' AS constraint_name
UNION ALL
SELECT 'questions' AS table_name, 'chk_questions_expected_response_count' AS constraint_name
UNION ALL
SELECT 'answer_sheet_regions' AS table_name, 'chk_answer_sheet_regions_expected_count' AS constraint_name
UNION ALL
SELECT 'answer_sheet_regions' AS table_name, 'chk_answer_sheet_regions_line_count' AS constraint_name
UNION ALL
SELECT 'answer_sheet_regions' AS table_name, 'chk_answer_sheet_regions_response_shape' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_normalized_source' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_hold' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_purge_state' AS constraint_name
UNION ALL
SELECT 'answer_attachments' AS table_name, 'chk_answer_attachments_held_not_purged' AS constraint_name
UNION ALL
SELECT 'answer_sheet_pages' AS table_name, 'chk_answer_sheet_pages_qr_payload' AS constraint_name
UNION ALL
SELECT 'answer_sheet_pages' AS table_name, 'chk_answer_sheet_pages_qr_hash' AS constraint_name
UNION ALL
SELECT 'scan_pages' AS table_name, 'chk_scan_pages_qr_payload' AS constraint_name
UNION ALL
SELECT 'scan_pages' AS table_name, 'chk_scan_pages_qr_hash' AS constraint_name
UNION ALL
SELECT 'term_periods' AS table_name, 'chk_term_periods_order' AS constraint_name
UNION ALL
SELECT 'class_assignment_schedules' AS table_name, 'chk_class_assignment_schedules_day' AS constraint_name
UNION ALL
SELECT 'class_assignment_schedules' AS table_name, 'chk_class_assignment_schedules_time' AS constraint_name
UNION ALL
SELECT 'class_assignment_schedules' AS table_name, 'chk_class_assignment_schedules_dates' AS constraint_name
UNION ALL
SELECT 'class_assignment_schedules' AS table_name, 'chk_class_assignment_schedules_timezone' AS constraint_name
UNION ALL
SELECT 'class_assignment_schedules' AS table_name, 'chk_class_assignment_schedules_archive' AS constraint_name
UNION ALL
SELECT 'test_assignments' AS table_name, 'chk_test_assignments_outside_schedule_confirmation' AS constraint_name
UNION ALL
SELECT 'mobile_scan_uploads' AS table_name, 'chk_mobile_scan_uploads_json' AS constraint_name
UNION ALL
SELECT 'mobile_scan_uploads' AS table_name, 'chk_mobile_scan_uploads_state' AS constraint_name
UNION ALL
SELECT 'mobile_scan_uploads' AS table_name, 'chk_mobile_scan_uploads_size' AS constraint_name
UNION ALL
SELECT 'mobile_scan_uploads' AS table_name, 'chk_mobile_scan_uploads_pixels' AS constraint_name
UNION ALL
SELECT 'test_results' AS table_name, 'chk_test_results_mobile_revision' AS constraint_name
UNION ALL
SELECT 'mobile_detection_uploads' AS table_name, 'chk_mobile_detection_uploads_response' AS constraint_name
UNION ALL
SELECT 'mobile_verification_batches' AS table_name, 'chk_mobile_verification_batches_json' AS constraint_name
UNION ALL
SELECT 'mobile_verification_items' AS table_name, 'chk_mobile_verification_items_json' AS constraint_name
UNION ALL
SELECT 'mobile_result_finalizations' AS table_name, 'chk_mobile_result_finalization_revision' AS constraint_name
UNION ALL
SELECT 'mobile_result_finalizations' AS table_name, 'chk_mobile_result_finalization_version' AS constraint_name
UNION ALL
SELECT 'mobile_result_finalizations' AS table_name, 'chk_mobile_result_finalization_json' AS constraint_name
UNION ALL
SELECT 'mobile_attachment_uploads' AS table_name, 'chk_mobile_attachment_request' AS constraint_name
UNION ALL
SELECT 'mobile_attachment_uploads' AS table_name, 'chk_mobile_attachment_response' AS constraint_name
UNION ALL
SELECT 'mobile_attachment_uploads' AS table_name, 'chk_mobile_attachment_image' AS constraint_name
UNION ALL
SELECT 'mobile_attachment_uploads' AS table_name, 'chk_mobile_attachment_state' AS constraint_name
UNION ALL
SELECT 'student_answers' AS table_name, 'chk_student_answers_response' AS constraint_name
UNION ALL
SELECT 'student_answers' AS table_name, 'chk_student_answers_evidence_source' AS constraint_name
UNION ALL
SELECT 'mobile_written_references' AS table_name, 'chk_mobile_written_reference_version' AS constraint_name
UNION ALL
SELECT 'mobile_written_references' AS table_name, 'chk_mobile_written_reference_json' AS constraint_name
UNION ALL
SELECT 'answer_verifications' AS table_name, 'chk_answer_verifications_evaluation_json' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_revision' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_version' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_reason' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_comment' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_hash' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_request' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_score' AS constraint_name
UNION ALL
SELECT 'mobile_result_reopens' AS table_name, 'chk_mobile_reopen_response' AS constraint_name
UNION ALL
SELECT 'mobile_result_corrections' AS table_name, 'chk_mobile_correction_version' AS constraint_name
UNION ALL
SELECT 'mobile_result_corrections' AS table_name, 'chk_mobile_correction_revision' AS constraint_name
UNION ALL
SELECT 'mobile_result_corrections' AS table_name, 'chk_mobile_correction_hash' AS constraint_name
UNION ALL
SELECT 'mobile_result_corrections' AS table_name, 'chk_mobile_correction_request' AS constraint_name
UNION ALL
SELECT 'mobile_result_corrections' AS table_name, 'chk_mobile_correction_reference' AS constraint_name
UNION ALL
SELECT 'mobile_result_corrections' AS table_name, 'chk_mobile_correction_response' AS constraint_name
UNION ALL
SELECT 'mobile_answer_corrections' AS table_name, 'chk_mobile_answer_correction_kind' AS constraint_name
UNION ALL
SELECT 'mobile_answer_corrections' AS table_name, 'chk_mobile_answer_correction_before' AS constraint_name
UNION ALL
SELECT 'mobile_answer_corrections' AS table_name, 'chk_mobile_answer_correction_after' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_distinct' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_revision' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_version' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_hash' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_request' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_previous' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_replacement' AS constraint_name
UNION ALL
SELECT 'mobile_result_supersessions' AS table_name, 'chk_mobile_supersession_response' AS constraint_name
) expected
LEFT JOIN information_schema.tidb_check_constraints actual
  ON actual.constraint_schema = 'performance_assessment_v3_test'
 AND actual.table_name = expected.table_name AND actual.constraint_name = expected.constraint_name
GROUP BY expected.table_name, expected.constraint_name ORDER BY expected.table_name, expected.constraint_name;

-- Read-only expression compatibility probe; expected values: 1, 0, 64, 2, 256.
-- This checks scalar function execution, not whether ALTER CHECK accepts the expression.
SELECT REGEXP_LIKE(SHA2('abc', 256), '^[0-9a-f]{64}$', 'c') AS valid_lowercase_hash,
       REGEXP_LIKE(UPPER(SHA2('abc', 256)), '^[0-9a-f]{64}$', 'c') AS uppercase_hash_rejected,
       CHAR_LENGTH(SHA2('abc', 256)) AS hash_length,
       OCTET_LENGTH('ab') AS payload_minimum, OCTET_LENGTH(REPEAT('a', 256)) AS payload_maximum;

-- All violating_rows must be zero. NULL/UNKNOWN follows the original SQL CHECK semantics.
SELECT 'chk_academic_years_dates' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`academic_years`
WHERE NOT (`end_date` >= `start_date`)
UNION ALL
SELECT 'chk_omr_detections_confidence' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_detections`
WHERE NOT (`confidence_score` >= 0 and `confidence_score` <= 1)
UNION ALL
SELECT 'chk_student_answers_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`student_answers`
WHERE NOT (`points_earned` >= 0)
UNION ALL
SELECT 'chk_test_parts_number_of_items' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_parts`
WHERE NOT (`number_of_items` > 0)
UNION ALL
SELECT 'chk_test_parts_points_per_item' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_parts`
WHERE NOT (`points_per_item` > 0)
UNION ALL
SELECT 'chk_test_results_scores' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_results`
WHERE NOT (`total_score` >= 0 and `max_score` >= 0 and `total_score` <= `max_score`)
UNION ALL
SELECT 'chk_users_teaching_start_month' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`users`
WHERE NOT (teaching_start_month IS NULL OR teaching_start_month BETWEEN 1 AND 12)
UNION ALL
SELECT 'chk_users_teaching_start_year' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`users`
WHERE NOT (teaching_start_year IS NULL OR teaching_start_year BETWEEN 1900 AND 2200)
UNION ALL
SELECT 'chk_students_lrn' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`students`
WHERE NOT (student_lrn REGEXP '^[0-9]{12}$')
UNION ALL
SELECT 'chk_term_periods_dates' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`term_periods`
WHERE NOT (end_at > start_at)
UNION ALL
SELECT 'chk_term_periods_override' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`term_periods`
WHERE NOT (overridden_by_user_id IS NULL OR (override_reason IS NOT NULL AND overridden_at IS NOT NULL))
UNION ALL
SELECT 'chk_performance_rule_sets_dates' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`performance_rule_sets`
WHERE NOT (effective_until_at IS NULL OR effective_from_at IS NULL OR effective_until_at > effective_from_at)
UNION ALL
SELECT 'chk_rubrics_total_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`rubrics`
WHERE NOT (total_points > 0)
UNION ALL
SELECT 'chk_rubric_criteria_maximum_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`rubric_criteria`
WHERE NOT (maximum_points > 0)
UNION ALL
SELECT 'chk_test_assignments_dates' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_assignments`
WHERE NOT (close_at IS NULL OR open_at IS NULL OR close_at > open_at)
UNION ALL
SELECT 'chk_questions_maximum_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`questions`
WHERE NOT (maximum_points > 0)
UNION ALL
SELECT 'chk_questions_maximum_response_length' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`questions`
WHERE NOT (maximum_response_length IS NULL OR maximum_response_length > 0)
UNION ALL
SELECT 'chk_question_options_order' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`question_options`
WHERE NOT (option_order > 0)
UNION ALL
SELECT 'chk_accepted_answers_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`accepted_answers`
WHERE NOT (points >= 0)
UNION ALL
SELECT 'chk_answer_keys_strategy' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_keys`
WHERE NOT ((answer_key_type = 'option' AND correct_question_option_id IS NOT NULL AND rubric_id IS NULL) OR (answer_key_type = 'accepted_text' AND correct_question_option_id IS NULL AND rubric_id IS NULL) OR (answer_key_type = 'rubric' AND correct_question_option_id IS NULL AND rubric_id IS NOT NULL) OR (answer_key_type = 'manual' AND correct_question_option_id IS NULL))
UNION ALL
SELECT 'chk_part_skill_mappings_range' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`part_skill_mappings`
WHERE NOT (start_item_number >= 1 AND end_item_number >= start_item_number AND item_count = end_item_number - start_item_number + 1)
UNION ALL
SELECT 'chk_class_lists_end_state' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`class_lists`
WHERE NOT ((enrollment_status = 'enrolled' AND ended_at IS NULL) OR (enrollment_status <> 'enrolled' AND ended_at IS NOT NULL))
UNION ALL
SELECT 'chk_omr_detections_option' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_detections`
WHERE NOT (detected_option IS NULL OR detected_option IN ('A', 'B', 'C', 'D'))
UNION ALL
SELECT 'chk_test_results_percentage' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_results`
WHERE NOT (percentage_snapshot IS NULL OR percentage_snapshot BETWEEN 0 AND 100)
UNION ALL
SELECT 'chk_answer_attachments_file_size' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (file_size_bytes > 0)
UNION ALL
SELECT 'chk_scan_verifications_reason' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_verifications`
WHERE NOT (verification_action = 'accepted' OR reason_code IS NOT NULL)
UNION ALL
SELECT 'chk_answer_verifications_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_verifications`
WHERE NOT (new_points >= 0)
UNION ALL
SELECT 'chk_answer_verifications_reason' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_verifications`
WHERE NOT (verification_action NOT IN ('ocr_corrected', 'reopened') OR reason_code IS NOT NULL)
UNION ALL
SELECT 'chk_answer_rubric_scores_points' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_rubric_scores`
WHERE NOT (points_awarded >= 0)
UNION ALL
SELECT 'chk_intervention_results_mastery' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`intervention_results`
WHERE NOT (mastery_rate_snapshot BETWEEN 0 AND 100)
UNION ALL
SELECT 'chk_student_intervention_cases_dates' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`student_intervention_cases`
WHERE NOT (resolved_at IS NULL OR resolved_at >= opened_at)
UNION ALL
SELECT 'chk_student_intervention_updates_status_change' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`student_intervention_updates`
WHERE NOT (update_type <> 'status_change' OR (previous_status IS NOT NULL AND new_status IS NOT NULL))
UNION ALL
SELECT 'chk_syncs_request_item_count' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`syncs`
WHERE NOT (request_item_count >= 0)
UNION ALL
SELECT 'chk_verification_challenges_attempts' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`verification_challenges`
WHERE NOT (maximum_attempt_count > 0 AND attempt_count <= maximum_attempt_count)
UNION ALL
SELECT 'chk_verification_challenges_expiry' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`verification_challenges`
WHERE NOT (expires_at > created_at)
UNION ALL
SELECT 'chk_user_mfa_factors_totp' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`user_mfa_factors`
WHERE NOT (totp_digits IN (6, 8) AND totp_period_seconds BETWEEN 15 AND 120)
UNION ALL
SELECT 'chk_mfa_authentication_challenges_attempts' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mfa_authentication_challenges`
WHERE NOT (maximum_attempt_count > 0 AND attempt_count <= maximum_attempt_count)
UNION ALL
SELECT 'chk_mfa_authentication_challenges_expiry' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mfa_authentication_challenges`
WHERE NOT (expires_at > created_at)
UNION ALL
SELECT 'chk_auth_sessions_mfa_state' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`auth_sessions`
WHERE NOT (authentication_level = 'password' OR (user_mfa_factor_id IS NOT NULL AND mfa_verified_at IS NOT NULL))
UNION ALL
SELECT 'chk_paper_sizes_dimensions' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`paper_sizes`
WHERE NOT (width_points > 0 AND height_points > 0)
UNION ALL
SELECT 'chk_omr_templates_item_count' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_templates`
WHERE NOT ((minimum_item_count IS NULL AND maximum_item_count IS NULL) OR ( minimum_item_count >= 1 AND maximum_item_count >= minimum_item_count ))
UNION ALL
SELECT 'chk_omr_templates_option_count' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_templates`
WHERE NOT (option_count IS NULL OR option_count BETWEEN 2 AND 4)
UNION ALL
SELECT 'chk_omr_templates_print_scale' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_templates`
WHERE NOT (required_print_scale_percent = 100.00)
UNION ALL
SELECT 'chk_omr_template_regions_bounds' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_template_regions`
WHERE NOT (x_points >= 0 AND y_points >= 0 AND width_points > 0 AND height_points > 0)
UNION ALL
SELECT 'chk_omr_template_regions_question_type' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_template_regions`
WHERE NOT ((region_type IN ('objective_bubbles', 'written_response') AND question_type_id IS NOT NULL) OR (region_type IN ('page_identity', 'registration_marker') AND question_type_id IS NULL))
UNION ALL
SELECT 'chk_answer_sheet_versions_minimum_questions' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_versions`
WHERE NOT (total_questions >= 5)
UNION ALL
SELECT 'chk_answer_sheet_versions_pages' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_versions`
WHERE NOT (total_pages >= 1)
UNION ALL
SELECT 'chk_answer_sheet_versions_generation' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_versions`
WHERE NOT (generation_number >= 1)
UNION ALL
SELECT 'chk_answer_sheet_versions_content_version' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_versions`
WHERE NOT (test_version_number >= 1 AND manifest_version >= 1)
UNION ALL
SELECT 'chk_answer_sheet_versions_pdf_size' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_versions`
WHERE NOT (pdf_file_size_bytes IS NULL OR pdf_file_size_bytes > 0)
UNION ALL
SELECT 'chk_answer_sheet_pages_number' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_pages`
WHERE NOT (page_number >= 1 AND total_pages >= page_number)
UNION ALL
SELECT 'chk_answer_sheet_regions_item_numbers' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_regions`
WHERE NOT (global_item_number >= 1 AND part_item_number >= 1 AND region_sequence >= 1)
UNION ALL
SELECT 'chk_scan_sessions_capture_model' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_sessions`
WHERE NOT (answer_sheet_version_id IS NOT NULL OR omr_template_id IS NOT NULL)
UNION ALL
SELECT 'chk_scan_sessions_page_counts' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_sessions`
WHERE NOT (expected_page_count >= 1 AND captured_page_count <= expected_page_count)
UNION ALL
SELECT 'chk_scan_pages_numbers' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_pages`
WHERE NOT (page_number >= 1 AND capture_number >= 1)
UNION ALL
SELECT 'chk_scan_pages_rotation' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_pages`
WHERE NOT (captured_rotation_degrees IN (0, 90, 180, 270))
UNION ALL
SELECT 'chk_omr_detections_page_region' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`omr_detections`
WHERE NOT ((scan_page_id IS NULL AND answer_sheet_region_id IS NULL) OR (scan_page_id IS NOT NULL AND answer_sheet_region_id IS NOT NULL))
UNION ALL
SELECT 'chk_answer_attachments_owner' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (student_answer_id IS NOT NULL OR scan_session_id IS NOT NULL OR scan_page_id IS NOT NULL)
UNION ALL
SELECT 'chk_answer_attachments_region_page' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (answer_sheet_region_id IS NULL OR scan_page_id IS NOT NULL)
UNION ALL
SELECT 'chk_answer_rubric_scores_maximum' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_rubric_scores`
WHERE NOT (maximum_points_snapshot IS NULL OR ( maximum_points_snapshot > 0 AND points_awarded <= maximum_points_snapshot ))
UNION ALL
SELECT 'chk_questions_expected_response_count' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`questions`
WHERE NOT (expected_response_count IS NULL OR expected_response_count >= 1)
UNION ALL
SELECT 'chk_answer_sheet_regions_expected_count' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_regions`
WHERE NOT (expected_response_count_snapshot IS NULL OR expected_response_count_snapshot >= 1)
UNION ALL
SELECT 'chk_answer_sheet_regions_line_count' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_regions`
WHERE NOT (response_line_count IS NULL OR response_line_count >= 1)
UNION ALL
SELECT 'chk_answer_sheet_regions_response_shape' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_regions`
WHERE NOT (( region_type = 'objective_bubbles' AND response_region_size = 'none' AND expected_response_count_snapshot IS NULL AND response_line_count IS NULL ) OR ( region_type = 'written_response' AND response_region_size <> 'none' AND ( expected_response_count_snapshot IS NULL OR response_line_count IS NULL OR response_line_count >= expected_response_count_snapshot ) ))
UNION ALL
SELECT 'chk_answer_attachments_normalized_source' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (( attachment_type = 'normalized_page' AND source_answer_attachment_id IS NOT NULL ) OR ( attachment_type <> 'normalized_page' AND source_answer_attachment_id IS NULL ))
UNION ALL
SELECT 'chk_answer_attachments_hold' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (( retention_hold = FALSE AND retention_hold_reason IS NULL AND retention_hold_set_by_user_id IS NULL AND retention_hold_set_at IS NULL ) OR ( retention_hold = TRUE AND retention_hold_reason IS NOT NULL AND retention_hold_set_by_user_id IS NOT NULL AND retention_hold_set_at IS NOT NULL ))
UNION ALL
SELECT 'chk_answer_attachments_purge_state' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (( purge_status = 'retained' AND purged_at IS NULL AND purged_by_user_id IS NULL AND purge_reason IS NULL AND last_purge_error IS NULL ) OR ( purge_status = 'purged' AND last_purge_attempt_at IS NOT NULL AND purged_at IS NOT NULL AND purge_reason IS NOT NULL AND last_purge_error IS NULL ) OR ( purge_status = 'purge_failed' AND last_purge_attempt_at IS NOT NULL AND purged_at IS NULL AND purged_by_user_id IS NULL AND purge_reason IS NOT NULL AND last_purge_error IS NOT NULL ))
UNION ALL
SELECT 'chk_answer_attachments_held_not_purged' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_attachments`
WHERE NOT (retention_hold = FALSE OR purge_status <> 'purged')
UNION ALL
SELECT 'chk_answer_sheet_pages_qr_payload' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_pages`
WHERE NOT (OCTET_LENGTH(qr_payload) BETWEEN 2 AND 256)
UNION ALL
SELECT 'chk_answer_sheet_pages_qr_hash' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_sheet_pages`
WHERE NOT (REGEXP_LIKE(`qr_payload_hash`, '^[0-9a-f]{64}$', 'c') AND `qr_payload_hash` = SHA2(`qr_payload`,256))
UNION ALL
SELECT 'chk_scan_pages_qr_payload' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_pages`
WHERE NOT (OCTET_LENGTH(qr_payload) BETWEEN 2 AND 256)
UNION ALL
SELECT 'chk_scan_pages_qr_hash' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`scan_pages`
WHERE NOT (REGEXP_LIKE(`qr_payload_hash`, '^[0-9a-f]{64}$', 'c') AND `qr_payload_hash` = SHA2(`qr_payload`,256))
UNION ALL
SELECT 'chk_term_periods_order' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`term_periods`
WHERE NOT (`term_order` BETWEEN 1 AND 4)
UNION ALL
SELECT 'chk_class_assignment_schedules_day' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`class_assignment_schedules`
WHERE NOT (`day_of_week` BETWEEN 1 AND 7)
UNION ALL
SELECT 'chk_class_assignment_schedules_time' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`class_assignment_schedules`
WHERE NOT (`end_time` > `start_time`)
UNION ALL
SELECT 'chk_class_assignment_schedules_dates' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`class_assignment_schedules`
WHERE NOT (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
UNION ALL
SELECT 'chk_class_assignment_schedules_timezone' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`class_assignment_schedules`
WHERE NOT (`timezone_name` = 'Asia/Manila')
UNION ALL
SELECT 'chk_class_assignment_schedules_archive' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`class_assignment_schedules`
WHERE NOT ((`schedule_status` = 'active' AND `status_changed_by_user_id` IS NULL AND `status_reason` IS NULL AND `archived_at` IS NULL) OR (`schedule_status` = 'archived' AND `status_changed_by_user_id` IS NOT NULL AND CHAR_LENGTH(TRIM(`status_reason`)) >= 5 AND `archived_at` IS NOT NULL))
UNION ALL
SELECT 'chk_test_assignments_outside_schedule_confirmation' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_assignments`
WHERE NOT ((`outside_schedule_confirmed` = 0 AND `outside_schedule_reason` IS NULL AND `outside_schedule_confirmed_by_user_id` IS NULL AND `outside_schedule_confirmed_at` IS NULL) OR (`outside_schedule_confirmed` = 1 AND CHAR_LENGTH(TRIM(`outside_schedule_reason`)) >= 5 AND `outside_schedule_confirmed_by_user_id` IS NOT NULL AND `outside_schedule_confirmed_at` IS NOT NULL))
UNION ALL
SELECT 'chk_mobile_scan_uploads_json' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_scan_uploads`
WHERE NOT (JSON_VALID(request_json))
UNION ALL
SELECT 'chk_mobile_scan_uploads_state' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_scan_uploads`
WHERE NOT ((upload_state = 'pending' AND backend_scan_page_id IS NULL AND receipt_page_status IS NULL AND committed_at IS NULL) OR (upload_state = 'committed' AND backend_scan_page_id IS NOT NULL AND receipt_page_status = 'captured' AND committed_at IS NOT NULL))
UNION ALL
SELECT 'chk_mobile_scan_uploads_size' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_scan_uploads`
WHERE NOT (file_size_bytes BETWEEN 1 AND 15728640)
UNION ALL
SELECT 'chk_mobile_scan_uploads_pixels' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_scan_uploads`
WHERE NOT (width_pixels > 0 AND height_pixels > 0 AND CAST(width_pixels AS DECIMAL(20,0)) * height_pixels <= 40000000)
UNION ALL
SELECT 'chk_test_results_mobile_revision' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`test_results`
WHERE NOT (mobile_revision BETWEEN 1 AND 9007199254740991)
UNION ALL
SELECT 'chk_mobile_detection_uploads_response' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_detection_uploads`
WHERE NOT (JSON_VALID(response_json))
UNION ALL
SELECT 'chk_mobile_verification_batches_json' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_verification_batches`
WHERE NOT (JSON_VALID(request_json))
UNION ALL
SELECT 'chk_mobile_verification_items_json' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_verification_items`
WHERE NOT (response_json IS NULL OR JSON_VALID(response_json))
UNION ALL
SELECT 'chk_mobile_result_finalization_revision' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_finalizations`
WHERE NOT (mobile_revision BETWEEN 2 AND 9007199254740991)
UNION ALL
SELECT 'chk_mobile_result_finalization_version' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_finalizations`
WHERE NOT (score_version >= 1)
UNION ALL
SELECT 'chk_mobile_result_finalization_json' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_finalizations`
WHERE NOT (JSON_VALID(response_json))
UNION ALL
SELECT 'chk_mobile_attachment_request' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_attachment_uploads`
WHERE NOT (JSON_VALID(request_json))
UNION ALL
SELECT 'chk_mobile_attachment_response' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_attachment_uploads`
WHERE NOT (response_json IS NULL OR JSON_VALID(response_json))
UNION ALL
SELECT 'chk_mobile_attachment_image' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_attachment_uploads`
WHERE NOT (file_size_bytes BETWEEN 1 AND 15728640 AND width_pixels > 0 AND height_pixels > 0 AND width_pixels * height_pixels <= 40000000)
UNION ALL
SELECT 'chk_mobile_attachment_state' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_attachment_uploads`
WHERE NOT ((upload_state='pending' AND backend_attachment_id IS NULL AND response_json IS NULL AND committed_at IS NULL) OR (upload_state='committed' AND backend_attachment_id IS NOT NULL AND response_json IS NOT NULL AND committed_at IS NOT NULL))
UNION ALL
SELECT 'chk_student_answers_response' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`student_answers`
WHERE NOT (selected_question_option_id IS NOT NULL OR response_text IS NOT NULL OR answer_status IN ('blank','multiple','uncertain','invalid','pending_manual') OR (capture_source='manual' AND response_evidence_attachment_id IS NOT NULL))
UNION ALL
SELECT 'chk_student_answers_evidence_source' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`student_answers`
WHERE NOT (response_evidence_attachment_id IS NULL OR (capture_source='manual' AND selected_question_option_id IS NULL))
UNION ALL
SELECT 'chk_mobile_written_reference_version' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_written_references`
WHERE NOT (test_version_number > 0)
UNION ALL
SELECT 'chk_mobile_written_reference_json' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_written_references`
WHERE NOT (JSON_VALID(reference_json))
UNION ALL
SELECT 'chk_answer_verifications_evaluation_json' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`answer_verifications`
WHERE NOT (evaluation_snapshot_json IS NULL OR JSON_VALID(evaluation_snapshot_json))
UNION ALL
SELECT 'chk_mobile_reopen_revision' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (previous_revision BETWEEN 1 AND 9007199254740990 AND mobile_revision=previous_revision+1)
UNION ALL
SELECT 'chk_mobile_reopen_version' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (score_version>=1)
UNION ALL
SELECT 'chk_mobile_reopen_reason' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (CHAR_LENGTH(TRIM(reason_code)) BETWEEN 1 AND 50)
UNION ALL
SELECT 'chk_mobile_reopen_comment' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (comment IS NULL OR CHAR_LENGTH(comment)<=4000)
UNION ALL
SELECT 'chk_mobile_reopen_hash' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (request_hash REGEXP '^[0-9a-f]{64}$')
UNION ALL
SELECT 'chk_mobile_reopen_request' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (JSON_VALID(request_json))
UNION ALL
SELECT 'chk_mobile_reopen_score' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (JSON_VALID(official_score_json))
UNION ALL
SELECT 'chk_mobile_reopen_response' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_reopens`
WHERE NOT (JSON_VALID(response_json))
UNION ALL
SELECT 'chk_mobile_correction_version' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_corrections`
WHERE NOT (previous_score_version BETWEEN 1 AND 2147483646 AND score_version=previous_score_version+1)
UNION ALL
SELECT 'chk_mobile_correction_revision' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_corrections`
WHERE NOT (mobile_revision BETWEEN 2 AND 9007199254740991)
UNION ALL
SELECT 'chk_mobile_correction_hash' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_corrections`
WHERE NOT (request_hash REGEXP '^[0-9a-f]{64}$')
UNION ALL
SELECT 'chk_mobile_correction_request' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_corrections`
WHERE NOT (JSON_VALID(request_json))
UNION ALL
SELECT 'chk_mobile_correction_reference' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_corrections`
WHERE NOT (JSON_VALID(reference_json))
UNION ALL
SELECT 'chk_mobile_correction_response' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_corrections`
WHERE NOT (JSON_VALID(response_json))
UNION ALL
SELECT 'chk_mobile_answer_correction_kind' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_answer_corrections`
WHERE NOT ((omr_detection_id IS NULL)<>(answer_verification_id IS NULL))
UNION ALL
SELECT 'chk_mobile_answer_correction_before' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_answer_corrections`
WHERE NOT (JSON_VALID(before_json))
UNION ALL
SELECT 'chk_mobile_answer_correction_after' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_answer_corrections`
WHERE NOT (JSON_VALID(after_json))
UNION ALL
SELECT 'chk_mobile_supersession_distinct' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (test_result_id<>replacement_result_id)
UNION ALL
SELECT 'chk_mobile_supersession_revision' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (previous_revision BETWEEN 1 AND 9007199254740990 AND mobile_revision=previous_revision+1)
UNION ALL
SELECT 'chk_mobile_supersession_version' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (score_version BETWEEN 1 AND 2147483647)
UNION ALL
SELECT 'chk_mobile_supersession_hash' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (request_hash REGEXP '^[0-9a-f]{64}$')
UNION ALL
SELECT 'chk_mobile_supersession_request' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (JSON_VALID(request_json))
UNION ALL
SELECT 'chk_mobile_supersession_previous' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (JSON_VALID(previous_score_json))
UNION ALL
SELECT 'chk_mobile_supersession_replacement' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (JSON_VALID(replacement_score_json))
UNION ALL
SELECT 'chk_mobile_supersession_response' AS check_name, COUNT(*) AS violating_rows
FROM `performance_assessment_v3_test`.`mobile_result_supersessions`
WHERE NOT (JSON_VALID(response_json));
