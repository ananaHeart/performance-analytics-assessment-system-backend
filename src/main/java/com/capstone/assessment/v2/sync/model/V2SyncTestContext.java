package com.capstone.assessment.v2.sync.model;

public record V2SyncTestContext(
        Long testId,
        Long classId,
        Long teacherUserId,
        String schoolId,
        String status
) {
}
