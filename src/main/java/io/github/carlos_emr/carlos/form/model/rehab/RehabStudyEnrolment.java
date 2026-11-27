/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.model.rehab;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A patient's enrolment in the 2004 rehabilitation study ({@code rehabStudy2004}).
 *
 * <p>The questionnaires only read this table, to fill in a new form's study ID. Nothing in
 * CARLOS writes it; enrolments are loaded outside the application.</p>
 *
 * @since 2026-11-26
 */
@Entity
@Table(name = "rehabStudy2004")
public class RehabStudyEnrolment extends AbstractModel<Integer> {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "studyID")
    private Integer studyId;
    @Column(name = "demographic_no", nullable = false)
    private Integer demographicNo;

    /** @return the study ID, the table's primary key */
    @Override
    public Integer getId() {
        return studyId;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }
}
