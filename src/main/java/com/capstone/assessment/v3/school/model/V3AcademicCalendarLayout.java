package com.capstone.assessment.v3.school.model;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Recognizes supported calendars without changing existing term IDs or stored labels. */
public enum V3AcademicCalendarLayout {
    // New term instants are start-inclusive and end-exclusive, in the school's time zone.
    THREE_TERMS(List.of("Term 1", "Term 2", "Term 3")),
    FOUR_QUARTERS(List.of("First Quarter", "Second Quarter", "Third Quarter", "Fourth Quarter"));

    private final List<String> names;

    V3AcademicCalendarLayout(List<String> names) {
        this.names = names;
    }

    public int termCount() {
        return names.size();
    }

    public boolean acceptsName(String name, int order) {
        if (name == null || order < 1 || order > termCount()) {
            return false;
        }
        String normalized = name.trim();
        return names.get(order - 1).equals(normalized)
                || (this == FOUR_QUARTERS && ("Quarter " + order).equals(normalized));
    }

    public static <T> Optional<V3AcademicCalendarLayout> detect(
            List<T> terms, Function<T, String> name, ToIntFunction<T> order
    ) {
        if (terms == null || terms.stream().anyMatch(term -> term == null)) {
            return Optional.empty();
        }
        for (V3AcademicCalendarLayout layout : values()) {
            if (terms.size() != layout.termCount()) {
                continue;
            }
            Set<Integer> orders = new HashSet<>();
            boolean valid = true;
            for (T term : terms) {
                int position = order.applyAsInt(term);
                if (!layout.acceptsName(name.apply(term), position) || !orders.add(position)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return Optional.of(layout);
            }
        }
        return Optional.empty();
    }
}
