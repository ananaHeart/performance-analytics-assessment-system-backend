package com.capstone.assessment.v3.mobile.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record V3MobileDownloadResponse(
        String contractVersion,
        String snapshotMode,
        Instant generatedAt,
        Teacher teacher,
        List<ClassAssignment> classAssignments,
        List<ClassAssignmentSchedule> classAssignmentSchedules,
        List<ClassListMembership> classLists,
        List<Student> students,
        List<TermPeriod> termPeriods,
        List<TestAssignment> testAssignments,
        List<Test> tests,
        List<TestPart> testParts,
        List<Question> questions,
        List<QuestionOption> questionOptions,
        List<PartSkillMapping> partSkillMappings,
        List<Skill> skills,
        List<AnswerSheet> answerSheets
) {

    public V3MobileDownloadResponse {
        classAssignments = List.copyOf(classAssignments);
        classAssignmentSchedules = List.copyOf(classAssignmentSchedules);
        classLists = List.copyOf(classLists);
        students = List.copyOf(students);
        termPeriods = List.copyOf(termPeriods);
        testAssignments = List.copyOf(testAssignments);
        tests = List.copyOf(tests);
        testParts = List.copyOf(testParts);
        questions = List.copyOf(questions);
        questionOptions = List.copyOf(questionOptions);
        partSkillMappings = List.copyOf(partSkillMappings);
        skills = List.copyOf(skills);
        answerSheets = List.copyOf(answerSheets);
    }

    public record Teacher(long userId, String schoolId, String email) {
    }

    public record ClassAssignment(
            long classAssignmentId,
            long classId,
            int academicYearId,
            String academicYearName,
            int gradeLevelId,
            String gradeLevelName,
            int sectionId,
            String sectionName,
            int subjectId,
            String subjectCode,
            String subjectName,
            String assignmentRole,
            String status,
            String classStatus
    ) {
    }

    public record ClassAssignmentSchedule(
            long classAssignmentScheduleId,
            String scheduleUuid,
            long classAssignmentId,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String timezoneName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String scheduleStatus,
            Instant updatedAt
    ) {
    }

    public record ClassListMembership(
            long classListId,
            String membershipUuid,
            long classId,
            long studentId,
            String enrollmentStatus,
            String enrollmentSource,
            Instant enrolledAt
    ) {
    }

    public record Student(
            long studentId,
            String studentLrn,
            String firstName,
            String middleName,
            String lastName,
            String suffix,
            String gender,
            String status
    ) {
    }

    public record TermPeriod(
            int termPeriodId,
            int academicYearId,
            String termName,
            int termOrder,
            Instant startAt,
            Instant endAt,
            String status
    ) {
    }

    public record TestAssignment(
            long testAssignmentId,
            String assignmentUuid,
            long testId,
            long classAssignmentId,
            Instant openAt,
            Instant closeAt,
            String assignmentStatus,
            boolean allowLateCapture,
            boolean captureAllowedNow,
            String captureAvailability
    ) {
    }

    public record Test(
            long testId,
            String testUuid,
            int versionNumber,
            int termPeriodId,
            String testName,
            String testType,
            String instructions,
            int totalItems,
            String status
    ) {
    }

    public record TestPart(
            long testPartId,
            long testId,
            int partOrder,
            String partName,
            int questionTypeId,
            int numberOfItems,
            BigDecimal pointsPerItem,
            String instructions
    ) {
    }

    public record Question(
            long questionId,
            String questionUuid,
            long testPartId,
            int questionTypeId,
            int itemNumber,
            int globalItemNumber,
            String questionText,
            BigDecimal maximumPoints,
            Long rubricId,
            String responseInstructions,
            boolean answerOrderRequired,
            Integer maximumResponseLength,
            Integer expectedResponseCount,
            String responseRegionSize,
            boolean forcePageBreakBefore
    ) {
    }

    public record QuestionOption(
            long questionOptionId,
            long questionId,
            String optionKey,
            String optionText,
            int optionOrder
    ) {
    }

    public record PartSkillMapping(
            long partSkillMappingId,
            long testPartId,
            long skillId,
            int startItemNumber,
            int endItemNumber,
            int itemCount
    ) {
    }

    public record Skill(
            long skillId,
            long competencyId,
            String competencyName,
            int rootTagId,
            String rootTagName,
            int termPeriodId,
            int gradeLevelId,
            int subjectId
    ) {
    }

    public record AnswerSheet(
            String answerSheetUuid,
            long testAssignmentId,
            String assignmentUuid,
            String paperSize,
            int generationNumber,
            int testVersionNumber,
            int totalQuestions,
            int totalPages,
            int manifestVersion,
            String manifestHash,
            String requiredScannerVersion,
            Instant generatedAt
    ) {
    }
}
