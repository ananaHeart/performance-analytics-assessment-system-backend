-- PENDING APPROVAL AND LIVE PRECHECK: additive constraint-only repair candidate.
-- Target is exactly performance_assessment_v3_test on the reviewed TiDB cluster.
-- OFFLINE PREPARATION ONLY: this file has not been executed.
-- Never run against the working local database or another cloud database.

-- Run only after reviewing 00_precheck.sql results, confirming TiDB identity,
-- and approving these 124 ALTER statements. Each is fully qualified to the target.
-- DDL commits independently; stop on the first error. Never ignore an error or rerun blindly.
-- No transaction wrapper, row rewrite, column change, seed operation, or global setting change.
-- The repair restores all 124 named release CHECKs; preserve the 7 original JSON CHECKs.
-- Retain term_order 1..4 to support legacy quarters; application validates 3-term years.
-- Cloud QR-regex variants below require TiDB acceptance; never replace with NOT ENFORCED.

-- Source: docs/migrations/v3/V3_000_v2_schema_snapshot.sql:32
ALTER TABLE `performance_assessment_v3_test`.`academic_years`
    ADD CONSTRAINT `chk_academic_years_dates` CHECK (
        `end_date` >= `start_date`
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_000_v2_schema_snapshot.sql:366
ALTER TABLE `performance_assessment_v3_test`.`omr_detections`
    ADD CONSTRAINT `chk_omr_detections_confidence` CHECK (
        `confidence_score` >= 0 and `confidence_score` <= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_000_v2_schema_snapshot.sql:522
ALTER TABLE `performance_assessment_v3_test`.`student_answers`
    ADD CONSTRAINT `chk_student_answers_points` CHECK (
        `points_earned` >= 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_000_v2_schema_snapshot.sql:651
ALTER TABLE `performance_assessment_v3_test`.`test_parts`
    ADD CONSTRAINT `chk_test_parts_number_of_items` CHECK (
        `number_of_items` > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_000_v2_schema_snapshot.sql:652
ALTER TABLE `performance_assessment_v3_test`.`test_parts`
    ADD CONSTRAINT `chk_test_parts_points_per_item` CHECK (
        `points_per_item` > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_000_v2_schema_snapshot.sql:700
ALTER TABLE `performance_assessment_v3_test`.`test_results`
    ADD CONSTRAINT `chk_test_results_scores` CHECK (
        `total_score` >= 0 and `max_score` >= 0 and `total_score` <= `max_score`
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:15
ALTER TABLE `performance_assessment_v3_test`.`users`
    ADD CONSTRAINT `chk_users_teaching_start_month` CHECK (
        teaching_start_month IS NULL OR teaching_start_month BETWEEN 1 AND 12
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:17
ALTER TABLE `performance_assessment_v3_test`.`users`
    ADD CONSTRAINT `chk_users_teaching_start_year` CHECK (
        teaching_start_year IS NULL OR teaching_start_year BETWEEN 1900 AND 2200
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:32
ALTER TABLE `performance_assessment_v3_test`.`students`
    ADD CONSTRAINT `chk_students_lrn` CHECK (
        student_lrn REGEXP '^[0-9]{12}$'
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:56
ALTER TABLE `performance_assessment_v3_test`.`term_periods`
    ADD CONSTRAINT `chk_term_periods_dates` CHECK (
        end_at > start_at
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:57
ALTER TABLE `performance_assessment_v3_test`.`term_periods`
    ADD CONSTRAINT `chk_term_periods_override` CHECK (
        overridden_by_user_id IS NULL OR (override_reason IS NOT NULL AND overridden_at IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:102
ALTER TABLE `performance_assessment_v3_test`.`performance_rule_sets`
    ADD CONSTRAINT `chk_performance_rule_sets_dates` CHECK (
        effective_until_at IS NULL OR effective_from_at IS NULL OR effective_until_at > effective_from_at
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:125
ALTER TABLE `performance_assessment_v3_test`.`rubrics`
    ADD CONSTRAINT `chk_rubrics_total_points` CHECK (
        total_points > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:143
ALTER TABLE `performance_assessment_v3_test`.`rubric_criteria`
    ADD CONSTRAINT `chk_rubric_criteria_maximum_points` CHECK (
        maximum_points > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:190
ALTER TABLE `performance_assessment_v3_test`.`test_assignments`
    ADD CONSTRAINT `chk_test_assignments_dates` CHECK (
        close_at IS NULL OR open_at IS NULL OR close_at > open_at
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:225
ALTER TABLE `performance_assessment_v3_test`.`questions`
    ADD CONSTRAINT `chk_questions_maximum_points` CHECK (
        maximum_points > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:226
ALTER TABLE `performance_assessment_v3_test`.`questions`
    ADD CONSTRAINT `chk_questions_maximum_response_length` CHECK (
        maximum_response_length IS NULL OR maximum_response_length > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:243
ALTER TABLE `performance_assessment_v3_test`.`question_options`
    ADD CONSTRAINT `chk_question_options_order` CHECK (
        option_order > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:264
ALTER TABLE `performance_assessment_v3_test`.`accepted_answers`
    ADD CONSTRAINT `chk_accepted_answers_points` CHECK (
        points >= 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:283
ALTER TABLE `performance_assessment_v3_test`.`answer_keys`
    ADD CONSTRAINT `chk_answer_keys_strategy` CHECK (
        (answer_key_type = 'option' AND correct_question_option_id IS NOT NULL AND rubric_id IS NULL) OR (answer_key_type = 'accepted_text' AND correct_question_option_id IS NULL AND rubric_id IS NULL) OR (answer_key_type = 'rubric' AND correct_question_option_id IS NULL AND rubric_id IS NOT NULL) OR (answer_key_type = 'manual' AND correct_question_option_id IS NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:308
ALTER TABLE `performance_assessment_v3_test`.`part_skill_mappings`
    ADD CONSTRAINT `chk_part_skill_mappings_range` CHECK (
        start_item_number >= 1 AND end_item_number >= start_item_number AND item_count = end_item_number - start_item_number + 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_001_core_domain.sql:382
ALTER TABLE `performance_assessment_v3_test`.`class_lists`
    ADD CONSTRAINT `chk_class_lists_end_state` CHECK (
        (enrollment_status = 'enrolled' AND ended_at IS NULL) OR (enrollment_status <> 'enrolled' AND ended_at IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:78
ALTER TABLE `performance_assessment_v3_test`.`omr_detections`
    ADD CONSTRAINT `chk_omr_detections_option` CHECK (
        detected_option IS NULL OR detected_option IN ('A', 'B', 'C', 'D')
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:105
ALTER TABLE `performance_assessment_v3_test`.`test_results`
    ADD CONSTRAINT `chk_test_results_percentage` CHECK (
        percentage_snapshot IS NULL OR percentage_snapshot BETWEEN 0 AND 100
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:172
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_file_size` CHECK (
        file_size_bytes > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:193
ALTER TABLE `performance_assessment_v3_test`.`scan_verifications`
    ADD CONSTRAINT `chk_scan_verifications_reason` CHECK (
        verification_action = 'accepted' OR reason_code IS NOT NULL
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:231
ALTER TABLE `performance_assessment_v3_test`.`answer_verifications`
    ADD CONSTRAINT `chk_answer_verifications_points` CHECK (
        new_points >= 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:232
ALTER TABLE `performance_assessment_v3_test`.`answer_verifications`
    ADD CONSTRAINT `chk_answer_verifications_reason` CHECK (
        verification_action NOT IN ('ocr_corrected', 'reopened') OR reason_code IS NOT NULL
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_002_assessment_capture.sql:259
ALTER TABLE `performance_assessment_v3_test`.`answer_rubric_scores`
    ADD CONSTRAINT `chk_answer_rubric_scores_points` CHECK (
        points_awarded >= 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:20
ALTER TABLE `performance_assessment_v3_test`.`intervention_results`
    ADD CONSTRAINT `chk_intervention_results_mastery` CHECK (
        mastery_rate_snapshot BETWEEN 0 AND 100
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:68
ALTER TABLE `performance_assessment_v3_test`.`student_intervention_cases`
    ADD CONSTRAINT `chk_student_intervention_cases_dates` CHECK (
        resolved_at IS NULL OR resolved_at >= opened_at
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:94
ALTER TABLE `performance_assessment_v3_test`.`student_intervention_updates`
    ADD CONSTRAINT `chk_student_intervention_updates_status_change` CHECK (
        update_type <> 'status_change' OR (previous_status IS NOT NULL AND new_status IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:113
ALTER TABLE `performance_assessment_v3_test`.`syncs`
    ADD CONSTRAINT `chk_syncs_request_item_count` CHECK (
        request_item_count >= 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:152
ALTER TABLE `performance_assessment_v3_test`.`verification_challenges`
    ADD CONSTRAINT `chk_verification_challenges_attempts` CHECK (
        maximum_attempt_count > 0 AND attempt_count <= maximum_attempt_count
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:155
ALTER TABLE `performance_assessment_v3_test`.`verification_challenges`
    ADD CONSTRAINT `chk_verification_challenges_expiry` CHECK (
        expires_at > created_at
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:190
ALTER TABLE `performance_assessment_v3_test`.`user_mfa_factors`
    ADD CONSTRAINT `chk_user_mfa_factors_totp` CHECK (
        totp_digits IN (6, 8) AND totp_period_seconds BETWEEN 15 AND 120
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:232
ALTER TABLE `performance_assessment_v3_test`.`mfa_authentication_challenges`
    ADD CONSTRAINT `chk_mfa_authentication_challenges_attempts` CHECK (
        maximum_attempt_count > 0 AND attempt_count <= maximum_attempt_count
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:235
ALTER TABLE `performance_assessment_v3_test`.`mfa_authentication_challenges`
    ADD CONSTRAINT `chk_mfa_authentication_challenges_expiry` CHECK (
        expires_at > created_at
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_003_security_sync_intervention.sql:249
ALTER TABLE `performance_assessment_v3_test`.`auth_sessions`
    ADD CONSTRAINT `chk_auth_sessions_mfa_state` CHECK (
        authentication_level = 'password' OR (user_mfa_factor_id IS NOT NULL AND mfa_verified_at IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:24
ALTER TABLE `performance_assessment_v3_test`.`paper_sizes`
    ADD CONSTRAINT `chk_paper_sizes_dimensions` CHECK (
        width_points > 0 AND height_points > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:59
ALTER TABLE `performance_assessment_v3_test`.`omr_templates`
    ADD CONSTRAINT `chk_omr_templates_item_count` CHECK (
        (minimum_item_count IS NULL AND maximum_item_count IS NULL) OR ( minimum_item_count >= 1 AND maximum_item_count >= minimum_item_count )
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:66
ALTER TABLE `performance_assessment_v3_test`.`omr_templates`
    ADD CONSTRAINT `chk_omr_templates_option_count` CHECK (
        option_count IS NULL OR option_count BETWEEN 2 AND 4
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:69
ALTER TABLE `performance_assessment_v3_test`.`omr_templates`
    ADD CONSTRAINT `chk_omr_templates_print_scale` CHECK (
        required_print_scale_percent = 100.00
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:128
ALTER TABLE `performance_assessment_v3_test`.`omr_template_regions`
    ADD CONSTRAINT `chk_omr_template_regions_bounds` CHECK (
        x_points >= 0 AND y_points >= 0 AND width_points > 0 AND height_points > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:134
ALTER TABLE `performance_assessment_v3_test`.`omr_template_regions`
    ADD CONSTRAINT `chk_omr_template_regions_question_type` CHECK (
        (region_type IN ('objective_bubbles', 'written_response') AND question_type_id IS NOT NULL) OR (region_type IN ('page_identity', 'registration_marker') AND question_type_id IS NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:188
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_versions`
    ADD CONSTRAINT `chk_answer_sheet_versions_minimum_questions` CHECK (
        total_questions >= 5
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:191
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_versions`
    ADD CONSTRAINT `chk_answer_sheet_versions_pages` CHECK (
        total_pages >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:192
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_versions`
    ADD CONSTRAINT `chk_answer_sheet_versions_generation` CHECK (
        generation_number >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:193
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_versions`
    ADD CONSTRAINT `chk_answer_sheet_versions_content_version` CHECK (
        test_version_number >= 1 AND manifest_version >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:196
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_versions`
    ADD CONSTRAINT `chk_answer_sheet_versions_pdf_size` CHECK (
        pdf_file_size_bytes IS NULL OR pdf_file_size_bytes > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:229
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_pages`
    ADD CONSTRAINT `chk_answer_sheet_pages_number` CHECK (
        page_number >= 1 AND total_pages >= page_number
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:279
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_regions`
    ADD CONSTRAINT `chk_answer_sheet_regions_item_numbers` CHECK (
        global_item_number >= 1 AND part_item_number >= 1 AND region_sequence >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:300
ALTER TABLE `performance_assessment_v3_test`.`scan_sessions`
    ADD CONSTRAINT `chk_scan_sessions_capture_model` CHECK (
        answer_sheet_version_id IS NOT NULL OR omr_template_id IS NOT NULL
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:303
ALTER TABLE `performance_assessment_v3_test`.`scan_sessions`
    ADD CONSTRAINT `chk_scan_sessions_page_counts` CHECK (
        expected_page_count >= 1 AND captured_page_count <= expected_page_count
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:369
ALTER TABLE `performance_assessment_v3_test`.`scan_pages`
    ADD CONSTRAINT `chk_scan_pages_numbers` CHECK (
        page_number >= 1 AND capture_number >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:372
ALTER TABLE `performance_assessment_v3_test`.`scan_pages`
    ADD CONSTRAINT `chk_scan_pages_rotation` CHECK (
        captured_rotation_degrees IN (0, 90, 180, 270)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:397
ALTER TABLE `performance_assessment_v3_test`.`omr_detections`
    ADD CONSTRAINT `chk_omr_detections_page_region` CHECK (
        (scan_page_id IS NULL AND answer_sheet_region_id IS NULL) OR (scan_page_id IS NOT NULL AND answer_sheet_region_id IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:428
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_owner` CHECK (
        student_answer_id IS NOT NULL OR scan_session_id IS NOT NULL OR scan_page_id IS NOT NULL
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:433
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_region_page` CHECK (
        answer_sheet_region_id IS NULL OR scan_page_id IS NOT NULL
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_006_dynamic_answer_sheet_schema_DRAFT.sql:464
ALTER TABLE `performance_assessment_v3_test`.`answer_rubric_scores`
    ADD CONSTRAINT `chk_answer_rubric_scores_maximum` CHECK (
        maximum_points_snapshot IS NULL OR ( maximum_points_snapshot > 0 AND points_awarded <= maximum_points_snapshot )
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:23
ALTER TABLE `performance_assessment_v3_test`.`questions`
    ADD CONSTRAINT `chk_questions_expected_response_count` CHECK (
        expected_response_count IS NULL OR expected_response_count >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:34
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_regions`
    ADD CONSTRAINT `chk_answer_sheet_regions_expected_count` CHECK (
        expected_response_count_snapshot IS NULL OR expected_response_count_snapshot >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:38
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_regions`
    ADD CONSTRAINT `chk_answer_sheet_regions_line_count` CHECK (
        response_line_count IS NULL OR response_line_count >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:41
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_regions`
    ADD CONSTRAINT `chk_answer_sheet_regions_response_shape` CHECK (
        ( region_type = 'objective_bubbles' AND response_region_size = 'none' AND expected_response_count_snapshot IS NULL AND response_line_count IS NULL ) OR ( region_type = 'written_response' AND response_region_size <> 'none' AND ( expected_response_count_snapshot IS NULL OR response_line_count IS NULL OR response_line_count >= expected_response_count_snapshot ) )
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:98
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_normalized_source` CHECK (
        ( attachment_type = 'normalized_page' AND source_answer_attachment_id IS NOT NULL ) OR ( attachment_type <> 'normalized_page' AND source_answer_attachment_id IS NULL )
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:108
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_hold` CHECK (
        ( retention_hold = FALSE AND retention_hold_reason IS NULL AND retention_hold_set_by_user_id IS NULL AND retention_hold_set_at IS NULL ) OR ( retention_hold = TRUE AND retention_hold_reason IS NOT NULL AND retention_hold_set_by_user_id IS NOT NULL AND retention_hold_set_at IS NOT NULL )
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:122
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_purge_state` CHECK (
        ( purge_status = 'retained' AND purged_at IS NULL AND purged_by_user_id IS NULL AND purge_reason IS NULL AND last_purge_error IS NULL ) OR ( purge_status = 'purged' AND last_purge_attempt_at IS NOT NULL AND purged_at IS NOT NULL AND purge_reason IS NOT NULL AND last_purge_error IS NULL ) OR ( purge_status = 'purge_failed' AND last_purge_attempt_at IS NOT NULL AND purged_at IS NULL AND purged_by_user_id IS NULL AND purge_reason IS NOT NULL AND last_purge_error IS NOT NULL )
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:146
ALTER TABLE `performance_assessment_v3_test`.`answer_attachments`
    ADD CONSTRAINT `chk_answer_attachments_held_not_purged` CHECK (
        retention_hold = FALSE OR purge_status <> 'purged'
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:160
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_pages`
    ADD CONSTRAINT `chk_answer_sheet_pages_qr_payload` CHECK (
        OCTET_LENGTH(qr_payload) BETWEEN 2 AND 256
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:163
-- TiDB adaptation: replace binary REGEXP with case-sensitive REGEXP_LIKE.
ALTER TABLE `performance_assessment_v3_test`.`answer_sheet_pages`
    ADD CONSTRAINT `chk_answer_sheet_pages_qr_hash` CHECK (
        REGEXP_LIKE(`qr_payload_hash`, '^[0-9a-f]{64}$', 'c') AND `qr_payload_hash` = SHA2(`qr_payload`,256)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:169
ALTER TABLE `performance_assessment_v3_test`.`scan_pages`
    ADD CONSTRAINT `chk_scan_pages_qr_payload` CHECK (
        OCTET_LENGTH(qr_payload) BETWEEN 2 AND 256
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_009_dynamic_answer_sheet_contract_hardening_DRAFT.sql:172
-- TiDB adaptation: replace binary REGEXP with case-sensitive REGEXP_LIKE.
ALTER TABLE `performance_assessment_v3_test`.`scan_pages`
    ADD CONSTRAINT `chk_scan_pages_qr_hash` CHECK (
        REGEXP_LIKE(`qr_payload_hash`, '^[0-9a-f]{64}$', 'c') AND `qr_payload_hash` = SHA2(`qr_payload`,256)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:156
ALTER TABLE `performance_assessment_v3_test`.`term_periods`
    ADD CONSTRAINT `chk_term_periods_order` CHECK (
        `term_order` BETWEEN 1 AND 4
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:189
ALTER TABLE `performance_assessment_v3_test`.`class_assignment_schedules`
    ADD CONSTRAINT `chk_class_assignment_schedules_day` CHECK (
        `day_of_week` BETWEEN 1 AND 7
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:191
ALTER TABLE `performance_assessment_v3_test`.`class_assignment_schedules`
    ADD CONSTRAINT `chk_class_assignment_schedules_time` CHECK (
        `end_time` > `start_time`
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:193
ALTER TABLE `performance_assessment_v3_test`.`class_assignment_schedules`
    ADD CONSTRAINT `chk_class_assignment_schedules_dates` CHECK (
        `effective_to` IS NULL OR `effective_to` >= `effective_from`
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:195
ALTER TABLE `performance_assessment_v3_test`.`class_assignment_schedules`
    ADD CONSTRAINT `chk_class_assignment_schedules_timezone` CHECK (
        `timezone_name` = 'Asia/Manila'
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:197
ALTER TABLE `performance_assessment_v3_test`.`class_assignment_schedules`
    ADD CONSTRAINT `chk_class_assignment_schedules_archive` CHECK (
        (`schedule_status` = 'active' AND `status_changed_by_user_id` IS NULL AND `status_reason` IS NULL AND `archived_at` IS NULL) OR (`schedule_status` = 'archived' AND `status_changed_by_user_id` IS NOT NULL AND CHAR_LENGTH(TRIM(`status_reason`)) >= 5 AND `archived_at` IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_014_academic_calendar_and_class_schedules.sql:225
ALTER TABLE `performance_assessment_v3_test`.`test_assignments`
    ADD CONSTRAINT `chk_test_assignments_outside_schedule_confirmation` CHECK (
        (`outside_schedule_confirmed` = 0 AND `outside_schedule_reason` IS NULL AND `outside_schedule_confirmed_by_user_id` IS NULL AND `outside_schedule_confirmed_at` IS NULL) OR (`outside_schedule_confirmed` = 1 AND CHAR_LENGTH(TRIM(`outside_schedule_reason`)) >= 5 AND `outside_schedule_confirmed_by_user_id` IS NOT NULL AND `outside_schedule_confirmed_at` IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_015_mobile_scan_upload_receipts_DRAFT.sql:30
ALTER TABLE `performance_assessment_v3_test`.`mobile_scan_uploads`
    ADD CONSTRAINT `chk_mobile_scan_uploads_json` CHECK (
        JSON_VALID(request_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_015_mobile_scan_upload_receipts_DRAFT.sql:31
ALTER TABLE `performance_assessment_v3_test`.`mobile_scan_uploads`
    ADD CONSTRAINT `chk_mobile_scan_uploads_state` CHECK (
        (upload_state = 'pending' AND backend_scan_page_id IS NULL AND receipt_page_status IS NULL AND committed_at IS NULL) OR (upload_state = 'committed' AND backend_scan_page_id IS NOT NULL AND receipt_page_status = 'captured' AND committed_at IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_015_mobile_scan_upload_receipts_DRAFT.sql:35
ALTER TABLE `performance_assessment_v3_test`.`mobile_scan_uploads`
    ADD CONSTRAINT `chk_mobile_scan_uploads_size` CHECK (
        file_size_bytes BETWEEN 1 AND 15728640
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_015_mobile_scan_upload_receipts_DRAFT.sql:36
ALTER TABLE `performance_assessment_v3_test`.`mobile_scan_uploads`
    ADD CONSTRAINT `chk_mobile_scan_uploads_pixels` CHECK (
        width_pixels > 0 AND height_pixels > 0 AND CAST(width_pixels AS DECIMAL(20,0)) * height_pixels <= 40000000
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_016_mobile_detection_receipts_DRAFT.sql:5
ALTER TABLE `performance_assessment_v3_test`.`test_results`
    ADD CONSTRAINT `chk_test_results_mobile_revision` CHECK (
        mobile_revision BETWEEN 1 AND 9007199254740991
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_016_mobile_detection_receipts_DRAFT.sql:18
ALTER TABLE `performance_assessment_v3_test`.`mobile_detection_uploads`
    ADD CONSTRAINT `chk_mobile_detection_uploads_response` CHECK (
        JSON_VALID(response_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_017_mobile_teacher_verification_DRAFT.sql:10
ALTER TABLE `performance_assessment_v3_test`.`mobile_verification_batches`
    ADD CONSTRAINT `chk_mobile_verification_batches_json` CHECK (
        JSON_VALID(request_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_017_mobile_teacher_verification_DRAFT.sql:17
ALTER TABLE `performance_assessment_v3_test`.`mobile_verification_items`
    ADD CONSTRAINT `chk_mobile_verification_items_json` CHECK (
        response_json IS NULL OR JSON_VALID(response_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_018_mobile_result_finalization_DRAFT.sql:10
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_finalizations`
    ADD CONSTRAINT `chk_mobile_result_finalization_revision` CHECK (
        mobile_revision BETWEEN 2 AND 9007199254740991
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_018_mobile_result_finalization_DRAFT.sql:11
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_finalizations`
    ADD CONSTRAINT `chk_mobile_result_finalization_version` CHECK (
        score_version >= 1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_018_mobile_result_finalization_DRAFT.sql:12
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_finalizations`
    ADD CONSTRAINT `chk_mobile_result_finalization_json` CHECK (
        JSON_VALID(response_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_019_mobile_attachment_uploads_DRAFT.sql:29
ALTER TABLE `performance_assessment_v3_test`.`mobile_attachment_uploads`
    ADD CONSTRAINT `chk_mobile_attachment_request` CHECK (
        JSON_VALID(request_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_019_mobile_attachment_uploads_DRAFT.sql:30
ALTER TABLE `performance_assessment_v3_test`.`mobile_attachment_uploads`
    ADD CONSTRAINT `chk_mobile_attachment_response` CHECK (
        response_json IS NULL OR JSON_VALID(response_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_019_mobile_attachment_uploads_DRAFT.sql:31
ALTER TABLE `performance_assessment_v3_test`.`mobile_attachment_uploads`
    ADD CONSTRAINT `chk_mobile_attachment_image` CHECK (
        file_size_bytes BETWEEN 1 AND 15728640 AND width_pixels > 0 AND height_pixels > 0 AND width_pixels * height_pixels <= 40000000
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_019_mobile_attachment_uploads_DRAFT.sql:32
ALTER TABLE `performance_assessment_v3_test`.`mobile_attachment_uploads`
    ADD CONSTRAINT `chk_mobile_attachment_state` CHECK (
        (upload_state='pending' AND backend_attachment_id IS NULL AND response_json IS NULL AND committed_at IS NULL) OR (upload_state='committed' AND backend_attachment_id IS NOT NULL AND response_json IS NOT NULL AND committed_at IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_020_mobile_written_verification_DRAFT.sql:7
ALTER TABLE `performance_assessment_v3_test`.`student_answers`
    ADD CONSTRAINT `chk_student_answers_response` CHECK (
        selected_question_option_id IS NOT NULL OR response_text IS NOT NULL OR answer_status IN ('blank','multiple','uncertain','invalid','pending_manual') OR (capture_source='manual' AND response_evidence_attachment_id IS NOT NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_020_mobile_written_verification_DRAFT.sql:11
ALTER TABLE `performance_assessment_v3_test`.`student_answers`
    ADD CONSTRAINT `chk_student_answers_evidence_source` CHECK (
        response_evidence_attachment_id IS NULL OR (capture_source='manual' AND selected_question_option_id IS NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_020_mobile_written_verification_DRAFT.sql:22
ALTER TABLE `performance_assessment_v3_test`.`mobile_written_references`
    ADD CONSTRAINT `chk_mobile_written_reference_version` CHECK (
        test_version_number > 0
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_020_mobile_written_verification_DRAFT.sql:23
ALTER TABLE `performance_assessment_v3_test`.`mobile_written_references`
    ADD CONSTRAINT `chk_mobile_written_reference_json` CHECK (
        JSON_VALID(reference_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_020_mobile_written_verification_DRAFT.sql:33
ALTER TABLE `performance_assessment_v3_test`.`answer_verifications`
    ADD CONSTRAINT `chk_answer_verifications_evaluation_json` CHECK (
        evaluation_snapshot_json IS NULL OR JSON_VALID(evaluation_snapshot_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:26
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_revision` CHECK (
        previous_revision BETWEEN 1 AND 9007199254740990 AND mobile_revision=previous_revision+1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:27
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_version` CHECK (
        score_version>=1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:28
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_reason` CHECK (
        CHAR_LENGTH(TRIM(reason_code)) BETWEEN 1 AND 50
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:29
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_comment` CHECK (
        comment IS NULL OR CHAR_LENGTH(comment)<=4000
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:30
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_hash` CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:31
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_request` CHECK (
        JSON_VALID(request_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:32
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_score` CHECK (
        JSON_VALID(official_score_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_021_mobile_result_reopen_DRAFT.sql:33
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_reopens`
    ADD CONSTRAINT `chk_mobile_reopen_response` CHECK (
        JSON_VALID(response_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:26
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_corrections`
    ADD CONSTRAINT `chk_mobile_correction_version` CHECK (
        previous_score_version BETWEEN 1 AND 2147483646 AND score_version=previous_score_version+1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:27
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_corrections`
    ADD CONSTRAINT `chk_mobile_correction_revision` CHECK (
        mobile_revision BETWEEN 2 AND 9007199254740991
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:28
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_corrections`
    ADD CONSTRAINT `chk_mobile_correction_hash` CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:29
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_corrections`
    ADD CONSTRAINT `chk_mobile_correction_request` CHECK (
        JSON_VALID(request_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:30
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_corrections`
    ADD CONSTRAINT `chk_mobile_correction_reference` CHECK (
        JSON_VALID(reference_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:31
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_corrections`
    ADD CONSTRAINT `chk_mobile_correction_response` CHECK (
        JSON_VALID(response_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:47
ALTER TABLE `performance_assessment_v3_test`.`mobile_answer_corrections`
    ADD CONSTRAINT `chk_mobile_answer_correction_kind` CHECK (
        (omr_detection_id IS NULL)<>(answer_verification_id IS NULL)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:48
ALTER TABLE `performance_assessment_v3_test`.`mobile_answer_corrections`
    ADD CONSTRAINT `chk_mobile_answer_correction_before` CHECK (
        JSON_VALID(before_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_022_mobile_result_corrections_DRAFT.sql:49
ALTER TABLE `performance_assessment_v3_test`.`mobile_answer_corrections`
    ADD CONSTRAINT `chk_mobile_answer_correction_after` CHECK (
        JSON_VALID(after_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:27
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_distinct` CHECK (
        test_result_id<>replacement_result_id
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:28
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_revision` CHECK (
        previous_revision BETWEEN 1 AND 9007199254740990 AND mobile_revision=previous_revision+1
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:29
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_version` CHECK (
        score_version BETWEEN 1 AND 2147483647
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:30
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_hash` CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:31
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_request` CHECK (
        JSON_VALID(request_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:32
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_previous` CHECK (
        JSON_VALID(previous_score_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:33
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_replacement` CHECK (
        JSON_VALID(replacement_score_json)
    ) ENFORCED;

-- Source: docs/migrations/v3/V3_023_mobile_result_supersession_DRAFT.sql:34
ALTER TABLE `performance_assessment_v3_test`.`mobile_result_supersessions`
    ADD CONSTRAINT `chk_mobile_supersession_response` CHECK (
        JSON_VALID(response_json)
    ) ENFORCED;
