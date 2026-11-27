/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/**
 * JPA mappings of the eighteen 2004 rehabilitation-study questionnaire tables (issue #5693).
 *
 * <p>{@link io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment} maps the shared
 * header. The questionnaire entities are generated from the Flyway baseline by
 * {@code scripts/forms/generate_rehab_study_entities.py} and list their answer columns as
 * {@link io.github.carlos_emr.carlos.form.model.rehab.RehabStudyColumn} records. Do not edit
 * a generated entity by hand: regenerate it, and {@code RehabStudyEntitySchemaContractUnitTest}
 * checks the result against the baseline.</p>
 */
package io.github.carlos_emr.carlos.form.model.rehab;
