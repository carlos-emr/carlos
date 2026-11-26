/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form.model.rehab;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;

/**
 * The header every 2004 rehabilitation-study questionnaire table shares: the version ID, the
 * patient, the saving provider, the form's creation date, the edit timestamp and the study ID.
 *
 * <p>Each saved questionnaire is an immutable version. A save inserts a new row with a
 * generated ID and leaves the version it was opened from untouched.</p>
 *
 * @since 2026-11-26
 */
@MappedSuperclass
public abstract class RehabStudyAssessment extends AbstractModel<Integer> {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;
    @Column(name = "demographic_no", nullable = false)
    private Integer demographicNo;
    @Column(name = "provider_no")
    private Integer providerNo;
    @Temporal(TemporalType.DATE)
    @Column(name = "formCreated")
    private Date formCreated;
    @Temporal(TemporalType.TIMESTAMP)
    // The published column is a TIMESTAMP, not the DATETIME Hibernate would generate.
    @Column(name = "formEdited", nullable = false, columnDefinition = "timestamp")
    private Date formEdited;
    @Column(name = "studyID", nullable = false, length = 20)
    private String studyId;

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public Integer getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(Integer providerNo) {
        this.providerNo = providerNo;
    }

    public Date getFormCreated() {
        return formCreated;
    }

    public void setFormCreated(Date formCreated) {
        this.formCreated = formCreated;
    }

    public Date getFormEdited() {
        return formEdited;
    }

    public void setFormEdited(Date formEdited) {
        this.formEdited = formEdited;
    }

    /** @return the study ID the form recorded, or {@code "N/A"} outside the study */
    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
}
