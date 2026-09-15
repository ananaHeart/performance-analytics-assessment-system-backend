package com.capstone.assessment.v2.account.dto;

import java.util.List;

public record V2TeacherRegistrationReferenceDataResponse(
        List<V2GenderOption> genders,
        List<V2SuffixOption> suffixes,
        List<V2MajorOption> majors,
        List<V2EducationalAttainmentOption> educationalAttainments,
        List<V2SchoolOption> schools,
        List<V2RegistrationVerificationMethodOption> verificationMethods
) {
}
