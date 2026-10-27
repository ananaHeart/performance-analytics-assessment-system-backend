package com.capstone.assessment.v3.account.dto;

import java.util.List;

/** REG-3: the teacher's optional additional majors and subjects taught before (the main major is separate). */
public record V3TeacherSubjectsResponse(
        List<Subject> additionalMajors,
        List<Subject> subjectsTaughtBefore
) {
    public record Subject(int subjectId, String subjectName) {
    }
}
