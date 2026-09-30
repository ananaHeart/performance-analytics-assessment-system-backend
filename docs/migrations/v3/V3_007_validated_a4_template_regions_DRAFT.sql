-- V3_007 DRAFT: Region seed for the physically validated A4 10-item MC sheet.
--
-- Approved and applied on 2026-08-30 to the isolated local V3 database. Do not
-- apply to V2, TiDB, production, or Mobile SQLite. Run only after V3_006 passes.
--
-- This file does not create a new template. It describes the exact immutable
-- geometry already approved as OMR-A4-10-MC-CTX-V2. It intentionally does not
-- seed dynamic A4, US Letter, US Legal, written-response, or mixed-type layouts.

USE performance_assessment_v3_db;

SET @legacy_template_id = (
    SELECT omr_template_id
    FROM omr_templates
    WHERE template_code = 'OMR-A4-10-MC-CTX-V2'
    LIMIT 1
);

SET @multiple_choice_type_id = (
    SELECT question_type_id
    FROM question_types
    WHERE question_type_code = 'multiple_choice'
    LIMIT 1
);

-- Four nested-square registration markers and the actual QR image boundary.
-- Coordinates use PDF points with a bottom-left origin.
INSERT IGNORE INTO omr_template_regions (
    region_uuid,
    omr_template_id,
    region_code,
    region_order,
    region_type,
    question_type_id,
    layout_variant,
    response_region_size,
    x_points,
    y_points,
    width_points,
    height_points,
    geometry_definition,
    geometry_hash,
    is_required
)
SELECT
    seed.region_uuid,
    @legacy_template_id,
    seed.region_code,
    seed.region_order,
    seed.region_type,
    NULL,
    'fixed_context_v2',
    NULL,
    seed.x_points,
    seed.y_points,
    seed.width_points,
    seed.height_points,
    seed.geometry_definition,
    SHA2(seed.geometry_definition, 256),
    TRUE
FROM (
    SELECT
        '00000000-0000-4707-8000-000000000001' AS region_uuid,
        'MARKER_TOP_LEFT' AS region_code,
        1 AS region_order,
        'registration_marker' AS region_type,
        20.000 AS x_points,
        806.890 AS y_points,
        15.000 AS width_points,
        15.000 AS height_points,
        '{"shape":"nested_square","outer_size_pt":15.0,"inner_inset_ratio":0.28,"center_x_pt":27.5,"center_y_pt":814.39}' AS geometry_definition
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000002',
        'MARKER_TOP_RIGHT',
        2,
        'registration_marker',
        560.276,
        806.890,
        15.000,
        15.000,
        '{"shape":"nested_square","outer_size_pt":15.0,"inner_inset_ratio":0.28,"center_x_pt":567.776,"center_y_pt":814.39}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000003',
        'MARKER_BOTTOM_LEFT',
        3,
        'registration_marker',
        20.000,
        20.000,
        15.000,
        15.000,
        '{"shape":"nested_square","outer_size_pt":15.0,"inner_inset_ratio":0.28,"center_x_pt":27.5,"center_y_pt":27.5}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000004',
        'MARKER_BOTTOM_RIGHT',
        4,
        'registration_marker',
        560.276,
        20.000,
        15.000,
        15.000,
        '{"shape":"nested_square","outer_size_pt":15.0,"inner_inset_ratio":0.28,"center_x_pt":567.776,"center_y_pt":27.5}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000005',
        'PAGE_QR',
        5,
        'page_identity',
        477.276,
        698.890,
        83.000,
        83.000,
        '{"error_correction":"M","quiet_zone_pt":6.0,"x_pt":477.276,"y_pt":698.89,"size_pt":83.0}'
) seed
WHERE @legacy_template_id IS NOT NULL;

-- Ten objective slots. Every row preserves the exact A-D bubble centers and
-- 6.4-point radius used by the validated Mobile scanner map.
INSERT IGNORE INTO omr_template_regions (
    region_uuid,
    omr_template_id,
    region_code,
    region_order,
    region_type,
    question_type_id,
    layout_variant,
    response_region_size,
    x_points,
    y_points,
    width_points,
    height_points,
    geometry_definition,
    geometry_hash,
    is_required
)
SELECT
    seed.region_uuid,
    @legacy_template_id,
    seed.region_code,
    seed.region_order,
    'objective_bubbles',
    @multiple_choice_type_id,
    'fixed_context_v2',
    'none',
    72.600,
    seed.y_center_points - 6.400,
    84.800,
    12.800,
    seed.geometry_definition,
    SHA2(seed.geometry_definition, 256),
    TRUE
FROM (
    SELECT
        '00000000-0000-4707-8000-000000000101' AS region_uuid,
        'MC_ITEM_01' AS region_code,
        10 AS region_order,
        604.334 AS y_center_points,
        '{"item_number":1,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,604.334],[103.0,604.334],[127.0,604.334],[151.0,604.334]],"bubble_radius_pt":6.4}' AS geometry_definition
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000102',
        'MC_ITEM_02',
        11,
        571.223,
        '{"item_number":2,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,571.223],[103.0,571.223],[127.0,571.223],[151.0,571.223]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000103',
        'MC_ITEM_03',
        12,
        538.112,
        '{"item_number":3,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,538.112],[103.0,538.112],[127.0,538.112],[151.0,538.112]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000104',
        'MC_ITEM_04',
        13,
        505.001,
        '{"item_number":4,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,505.001],[103.0,505.001],[127.0,505.001],[151.0,505.001]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000105',
        'MC_ITEM_05',
        14,
        471.890,
        '{"item_number":5,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,471.89],[103.0,471.89],[127.0,471.89],[151.0,471.89]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000106',
        'MC_ITEM_06',
        15,
        438.778,
        '{"item_number":6,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,438.778],[103.0,438.778],[127.0,438.778],[151.0,438.778]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000107',
        'MC_ITEM_07',
        16,
        405.667,
        '{"item_number":7,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,405.667],[103.0,405.667],[127.0,405.667],[151.0,405.667]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000108',
        'MC_ITEM_08',
        17,
        372.556,
        '{"item_number":8,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,372.556],[103.0,372.556],[127.0,372.556],[151.0,372.556]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000109',
        'MC_ITEM_09',
        18,
        339.445,
        '{"item_number":9,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,339.445],[103.0,339.445],[127.0,339.445],[151.0,339.445]],"bubble_radius_pt":6.4}'
    UNION ALL
    SELECT
        '00000000-0000-4707-8000-000000000110',
        'MC_ITEM_10',
        19,
        306.334,
        '{"item_number":10,"option_keys":["A","B","C","D"],"bubble_centers_pt":[[79.0,306.334],[103.0,306.334],[127.0,306.334],[151.0,306.334]],"bubble_radius_pt":6.4}'
) seed
WHERE @legacy_template_id IS NOT NULL
  AND @multiple_choice_type_id IS NOT NULL;

-- Review query only. Expected result after a successful disposable run:
-- registration_marker=4, page_identity=1, objective_bubbles=10.
SELECT
    region_type,
    COUNT(*) AS region_count
FROM omr_template_regions
WHERE omr_template_id = @legacy_template_id
GROUP BY region_type
ORDER BY region_type;
