/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.model.rehab;

import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One answer column of a rehabilitation-study questionnaire table, bound to its entity field.
 *
 * <p>The kind decides how the legacy form contract converts the column: a {@link Checkbox}
 * holds the {@code tinyint(1)} tick, a {@link Text} holds every string or numeric answer as the
 * text the form posted, and a {@link Day} holds a calendar date. The generated entities list
 * their columns with these records, so the mapper reads and writes fields without reflection.</p>
 *
 * @param <E> the questionnaire entity that owns the column
 * @since 2026-11-26
 */
public sealed interface RehabStudyColumn<E extends RehabStudyAssessment>
        permits RehabStudyColumn.Checkbox, RehabStudyColumn.Text, RehabStudyColumn.Day {

    /** @return the physical column name, which is also the form field name */
    String name();

    /**
     * A {@code tinyint(1)} checkbox answer: 1 is ticked, any other stored value is not.
     *
     * @param name the column and form field name
     * @param reader reads the stored value
     * @param writer stores a value
     * @param <E> the owning entity
     */
    record Checkbox<E extends RehabStudyAssessment>(String name, Function<E, Byte> reader,
            BiConsumer<E, Byte> writer) implements RehabStudyColumn<E> { }

    /**
     * A {@code varchar}, {@code char}, {@code int} or {@code double} answer kept as posted text.
     *
     * @param name the column and form field name
     * @param reader reads the stored value
     * @param writer stores a value
     * @param <E> the owning entity
     */
    record Text<E extends RehabStudyAssessment>(String name, Function<E, String> reader,
            BiConsumer<E, String> writer) implements RehabStudyColumn<E> { }

    /**
     * A {@code date} answer.
     *
     * @param name the column and form field name
     * @param reader reads the stored value
     * @param writer stores a value
     * @param <E> the owning entity
     */
    record Day<E extends RehabStudyAssessment>(String name, Function<E, Date> reader,
            BiConsumer<E, Date> writer) implements RehabStudyColumn<E> { }
}
