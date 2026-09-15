package com.capstone.assessment.v3.school.model;

import java.time.Instant;
import java.time.LocalDate;

public final class V3SchoolSetupModels {

    private V3SchoolSetupModels() {
    }

    public record ClassContext(
            long classId,
            String schoolId,
            int academicYearId,
            String academicYearName,
            int sectionId,
            int gradeLevelId,
            String gradeLevelName,
            String sectionName,
            String status,
            int enrolledStudentCount
    ) {
    }

    public record AssignmentContext(
            long classAssignmentId,
            String schoolId,
            long classId,
            long teacherUserId,
            String teacherName,
            int subjectId,
            String subjectName,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            int sectionId,
            String sectionName,
            String assignmentRole,
            String status,
            java.time.Instant assignedAt,
            java.time.Instant endedAt,
            String statusReason
    ) {
    }

    public record StudentContext(
            long studentId,
            String schoolId,
            long addressId,
            int genderId,
            String studentLrn,
            String firstName,
            String middleName,
            String lastName,
            Integer suffixId,
            LocalDate birthDate,
            String status
    ) {
    }

    public record EnrollmentContext(
            long classListId,
            String membershipUuid,
            long classId,
            long studentId,
            int academicYearId,
            String enrollmentStatus,
            String enrollmentSource,
            Instant enrolledAt,
            Instant endedAt,
            String statusReason,
            Long statusChangedByUserId
    ) {
    }
}
