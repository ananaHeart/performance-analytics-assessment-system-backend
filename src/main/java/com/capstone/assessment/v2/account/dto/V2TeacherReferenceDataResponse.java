package com.capstone.assessment.v2.account.dto;

import java.util.List;

public record V2TeacherReferenceDataResponse(
        List<V2GenderOption> genders,
        List<V2SuffixOption> suffixes,
        List<V2MajorOption> majors,
        List<V2EducationalAttainmentOption> educationalAttainments
) {
}
