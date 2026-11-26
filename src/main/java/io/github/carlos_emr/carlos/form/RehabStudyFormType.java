/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * One 2004 rehabilitation-study questionnaire as the form router knows it.
 *
 * @param formClass the router's {@code form_class} name, the suffix of the retired {@code Frm*Record}
 * @param table the physical table, which also names the optional XML archive copies
 * @param entity the questionnaire entity
 * @param factory creates an empty entity for a new version
 * @param columns the entity's answer columns in physical order
 * @param prefill the patient details a new form copies from the master record
 * @param <E> the questionnaire entity
 * @since 2026-11-26
 */
public record RehabStudyFormType<E extends RehabStudyAssessment>(String formClass, String table, Class<E> entity,
        Supplier<E> factory, List<RehabStudyColumn<E>> columns, Set<Prefill> prefill) {

    /** Patient details a new form showed besides its patient number and creation date. */
    public enum Prefill {
        /** {@code dob}, formatted {@code yyyy/MM/dd}. */
        BIRTH_DATE,
        /** {@code sex}. */
        SEX,
        /** {@code phone}, the patient's home telephone. */
        PHONE
    }

    /** Copies the column and prefill lists so a registered type cannot change after start-up. */
    public RehabStudyFormType {
        Objects.requireNonNull(formClass, "formClass");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(factory, "factory");
        columns = List.copyOf(columns);
        prefill = Set.copyOf(prefill);
    }
}
