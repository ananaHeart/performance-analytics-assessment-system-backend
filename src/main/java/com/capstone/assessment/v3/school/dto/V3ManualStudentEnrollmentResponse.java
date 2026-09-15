package com.capstone.assessment.v3.school.dto;

public record V3ManualStudentEnrollmentResponse(
        V3StudentRosterEntryResponse student,
        boolean studentCreated,
        boolean enrollmentCreated,
        boolean enrollmentReactivated
) {
}
