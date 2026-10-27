package com.capstone.assessment.v3.auth.service;

import com.capstone.assessment.v3.auth.repository.V3RegistrationRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * REG-3: a teacher's optional additional majors and subjects taught before. The main major is
 * kept only in users.major_id, so it is dropped from the additional majors (no duplicate data).
 */
public final class V3TeacherSubjects {

    public static final String ADDITIONAL_MAJOR = "additional_major";
    public static final String TAUGHT_BEFORE = "taught_before";
    public static final int MAX_PER_LIST = 10;

    private V3TeacherSubjects() {
    }

    /** Distinct positive IDs in the order given, without the main major when one is passed. */
    public static List<Integer> clean(List<Integer> ids, Integer mainMajorId) {
        if (ids == null) {
            return List.of();
        }
        Set<Integer> distinct = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id != null && id > 0 && !Objects.equals(id, mainMajorId)) {
                distinct.add(id);
            }
        }
        return new ArrayList<>(distinct);
    }

    /** Adds a field error when a list is too long or names a subject that does not exist. */
    public static void validate(List<Integer> cleaned, String field, V3RegistrationRepository repository,
                                Map<String, Object> errors) {
        if (cleaned.size() > MAX_PER_LIST) {
            errors.put(field, "Choose up to " + MAX_PER_LIST + ".");
        } else if (!cleaned.isEmpty() && repository.countExistingSubjects(cleaned) != cleaned.size()) {
            errors.put(field, "Choose subjects from the list.");
        }
    }
}
