package com.capstone.assessment.v3.school.dto;

import java.util.List;

/**
 * CLS-9: teachers for one subject, best match first. Groups: main_major (priority 1),
 * additional_major (2), taught_before (3), others (4). Only approved, active teachers.
 */
public record V3TeacherOptionsResponse(
        int subjectId,
        String subjectName,
        List<Group> groups
) {
    public record Group(String key, String label, List<Teacher> teachers) {
    }

    public record Teacher(
            long teacherUserId,
            String displayName,
            String matchType,
            int priority,
            boolean alreadyAssignedToClass
    ) {
    }
}
