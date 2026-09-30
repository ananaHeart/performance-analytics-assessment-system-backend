package com.capstone.assessment.v3.school.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V3AcademicCalendarLayoutTest {
    private record Term(String name, int order) { }

    @Test
    void detectsBothCompleteLayoutsIndependentOfInputOrder() {
        assertEquals(V3AcademicCalendarLayout.THREE_TERMS,
                V3AcademicCalendarLayout.detect(List.of(new Term("Term 3", 3), new Term("Term 1", 1),
                        new Term("Term 2", 2)), Term::name, Term::order).orElseThrow());
        assertEquals(V3AcademicCalendarLayout.FOUR_QUARTERS,
                V3AcademicCalendarLayout.detect(List.of(new Term("First Quarter", 1), new Term("Second Quarter", 2),
                        new Term("Third Quarter", 3), new Term("Fourth Quarter", 4)), Term::name, Term::order).orElseThrow());
        assertEquals(V3AcademicCalendarLayout.FOUR_QUARTERS,
                V3AcademicCalendarLayout.detect(List.of(new Term("Quarter 1", 1), new Term("Quarter 2", 2),
                        new Term("Quarter 3", 3), new Term("Quarter 4", 4)), Term::name, Term::order).orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("invalidCalendars")
    void countAloneCannotAuthorizeIncompleteOrMixedCalendars(List<Term> terms) {
        assertTrue(V3AcademicCalendarLayout.detect(terms, Term::name, Term::order).isEmpty());
    }

    static Stream<List<Term>> invalidCalendars() {
        return Stream.of(
                null,
                List.of(),
                List.of(new Term("Term 1", 1)),
                List.of(new Term("Term 1", 1), new Term("Term 2", 2)),
                Arrays.asList(new Term("Term 1", 1), null, new Term("Term 3", 3)),
                List.of(new Term("First Quarter", 1), new Term("Second Quarter", 2), new Term("Third Quarter", 3)),
                List.of(new Term("Term 1", 1), new Term("Term 2", 2), new Term("Term 3", 3), new Term("Term 4", 4)),
                List.of(new Term("Term 1", 1), new Term("Term 2", 2), new Term("Term 3", 3), new Term("Term 4", 4), new Term("Term 5", 5)),
                List.of(new Term("Term 1", 1), new Term("Term 1", 1), new Term("Term 3", 3)),
                List.of(new Term("Term 1", 0), new Term("Term 2", 2), new Term("Term 3", 3)),
                List.of(new Term("Term 1", 1), new Term("Term 2", 2), new Term("Term 3", 4)),
                List.of(new Term("Term 1", 1), new Term("Second Quarter", 2), new Term("Term 3", 3)),
                List.of(new Term(null, 1), new Term("Term 2", 2), new Term("Term 3", 3))
        );
    }
}
