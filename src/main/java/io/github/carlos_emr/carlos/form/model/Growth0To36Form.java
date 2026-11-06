/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form.model;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;

/**
 * A version of the Growth 0–36m record, mapped to the published table without schema changes.
 * Visits remain embedded in their existing columns; no collection table is introduced.
 */
@Entity
@Table(name = "formGrowth0_36")
public class Growth0To36Form extends AbstractModel<Integer> {
    private static final long serialVersionUID = 1L;

    /** Number of visit slots in the physical table, including slots not rendered by the JSP. */
    public static final int VISIT_COUNT = 20;

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
    @Column(name = "formEdited", nullable = false)
    private Date formEdited;
    @Column(name = "patientName", length = 80)
    private String patientName;
    @Column(name = "recordNo", length = 10)
    private String recordNo;
    @Column(name = "motherStature", length = 80)
    private String motherStature;
    @Column(name = "gestationalAge", length = 5)
    private String gestationalAge;
    @Column(name = "fatherStature", length = 80)
    private String fatherStature;
    @Column(name = "edc", length = 10)
    private String edc;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_1")),
        @AttributeOverride(name = "age", column = @Column(name = "age_1", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_1", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_1", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_1", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_1", length = 25))
    })
    private Growth0To36Visit visit1;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_2")),
        @AttributeOverride(name = "age", column = @Column(name = "age_2", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_2", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_2", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_2", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_2", length = 25))
    })
    private Growth0To36Visit visit2;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_3")),
        @AttributeOverride(name = "age", column = @Column(name = "age_3", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_3", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_3", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_3", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_3", length = 25))
    })
    private Growth0To36Visit visit3;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_4")),
        @AttributeOverride(name = "age", column = @Column(name = "age_4", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_4", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_4", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_4", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_4", length = 25))
    })
    private Growth0To36Visit visit4;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_5")),
        @AttributeOverride(name = "age", column = @Column(name = "age_5", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_5", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_5", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_5", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_5", length = 25))
    })
    private Growth0To36Visit visit5;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_6")),
        @AttributeOverride(name = "age", column = @Column(name = "age_6", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_6", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_6", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_6", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_6", length = 25))
    })
    private Growth0To36Visit visit6;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_7")),
        @AttributeOverride(name = "age", column = @Column(name = "age_7", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_7", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_7", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_7", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_7", length = 25))
    })
    private Growth0To36Visit visit7;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_8")),
        @AttributeOverride(name = "age", column = @Column(name = "age_8", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_8", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_8", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_8", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_8", length = 25))
    })
    private Growth0To36Visit visit8;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_9")),
        @AttributeOverride(name = "age", column = @Column(name = "age_9", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_9", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_9", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_9", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_9", length = 25))
    })
    private Growth0To36Visit visit9;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_10")),
        @AttributeOverride(name = "age", column = @Column(name = "age_10", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_10", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_10", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_10", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_10", length = 25))
    })
    private Growth0To36Visit visit10;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_11")),
        @AttributeOverride(name = "age", column = @Column(name = "age_11", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_11", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_11", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_11", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_11", length = 25))
    })
    private Growth0To36Visit visit11;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_12")),
        @AttributeOverride(name = "age", column = @Column(name = "age_12", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_12", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_12", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_12", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_12", length = 25))
    })
    private Growth0To36Visit visit12;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_13")),
        @AttributeOverride(name = "age", column = @Column(name = "age_13", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_13", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_13", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_13", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_13", length = 25))
    })
    private Growth0To36Visit visit13;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_14")),
        @AttributeOverride(name = "age", column = @Column(name = "age_14", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_14", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_14", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_14", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_14", length = 25))
    })
    private Growth0To36Visit visit14;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_15")),
        @AttributeOverride(name = "age", column = @Column(name = "age_15", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_15", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_15", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_15", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_15", length = 25))
    })
    private Growth0To36Visit visit15;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_16")),
        @AttributeOverride(name = "age", column = @Column(name = "age_16", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_16", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_16", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_16", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_16", length = 25))
    })
    private Growth0To36Visit visit16;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_17")),
        @AttributeOverride(name = "age", column = @Column(name = "age_17", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_17", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_17", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_17", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_17", length = 25))
    })
    private Growth0To36Visit visit17;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_18")),
        @AttributeOverride(name = "age", column = @Column(name = "age_18", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_18", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_18", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_18", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_18", length = 25))
    })
    private Growth0To36Visit visit18;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_19")),
        @AttributeOverride(name = "age", column = @Column(name = "age_19", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_19", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_19", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_19", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_19", length = 25))
    })
    private Growth0To36Visit visit19;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_20")),
        @AttributeOverride(name = "age", column = @Column(name = "age_20", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_20", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_20", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_20", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_20", length = 25))
    })
    private Growth0To36Visit visit20;

    /** Returns the database-generated version identifier. */
    @Override
    public Integer getId() { return id; }

    /** Returns the stored demographicNo. */
    public Integer getDemographicNo() { return demographicNo; }
    /** Sets the stored demographicNo. */
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }

    /** Returns the stored providerNo. */
    public Integer getProviderNo() { return providerNo; }
    /** Sets the stored providerNo. */
    public void setProviderNo(Integer providerNo) { this.providerNo = providerNo; }

    /** Returns the stored formCreated. */
    public Date getFormCreated() { return formCreated; }
    /** Sets the stored formCreated. */
    public void setFormCreated(Date formCreated) { this.formCreated = formCreated; }

    /** Returns the stored formEdited. */
    public Date getFormEdited() { return formEdited; }
    /** Sets the stored formEdited. */
    public void setFormEdited(Date formEdited) { this.formEdited = formEdited; }

    /** Returns the stored patientName. */
    public String getPatientName() { return patientName; }
    /** Sets the stored patientName. */
    public void setPatientName(String patientName) { this.patientName = patientName; }

    /** Returns the stored recordNo. */
    public String getRecordNo() { return recordNo; }
    /** Sets the stored recordNo. */
    public void setRecordNo(String recordNo) { this.recordNo = recordNo; }

    /** Returns the stored motherStature. */
    public String getMotherStature() { return motherStature; }
    /** Sets the stored motherStature. */
    public void setMotherStature(String motherStature) { this.motherStature = motherStature; }

    /** Returns the stored gestationalAge. */
    public String getGestationalAge() { return gestationalAge; }
    /** Sets the stored gestationalAge. */
    public void setGestationalAge(String gestationalAge) { this.gestationalAge = gestationalAge; }

    /** Returns the stored fatherStature. */
    public String getFatherStature() { return fatherStature; }
    /** Sets the stored fatherStature. */
    public void setFatherStature(String fatherStature) { this.fatherStature = fatherStature; }

    /** Returns the stored edc. */
    public String getEdc() { return edc; }
    /** Sets the stored edc. */
    public void setEdc(String edc) { this.edc = edc; }

    /** Returns the numbered visit (1–20), or null when every stored value is null. */
    public Growth0To36Visit getVisit(int number) {
        return switch (number) {
            case 1 -> visit1;
            case 2 -> visit2;
            case 3 -> visit3;
            case 4 -> visit4;
            case 5 -> visit5;
            case 6 -> visit6;
            case 7 -> visit7;
            case 8 -> visit8;
            case 9 -> visit9;
            case 10 -> visit10;
            case 11 -> visit11;
            case 12 -> visit12;
            case 13 -> visit13;
            case 14 -> visit14;
            case 15 -> visit15;
            case 16 -> visit16;
            case 17 -> visit17;
            case 18 -> visit18;
            case 19 -> visit19;
            case 20 -> visit20;
            default -> throw new IllegalArgumentException("Growth visit number must be between 1 and 20");
        };
    }

    /** Replaces a numbered visit while preserving the published column layout. */
    public void setVisit(int number, Growth0To36Visit visit) {
        switch (number) {
            case 1 -> visit1 = visit;
            case 2 -> visit2 = visit;
            case 3 -> visit3 = visit;
            case 4 -> visit4 = visit;
            case 5 -> visit5 = visit;
            case 6 -> visit6 = visit;
            case 7 -> visit7 = visit;
            case 8 -> visit8 = visit;
            case 9 -> visit9 = visit;
            case 10 -> visit10 = visit;
            case 11 -> visit11 = visit;
            case 12 -> visit12 = visit;
            case 13 -> visit13 = visit;
            case 14 -> visit14 = visit;
            case 15 -> visit15 = visit;
            case 16 -> visit16 = visit;
            case 17 -> visit17 = visit;
            case 18 -> visit18 = visit;
            case 19 -> visit19 = visit;
            case 20 -> visit20 = visit;
            default -> throw new IllegalArgumentException("Growth visit number must be between 1 and 20");
        }
    }
}
