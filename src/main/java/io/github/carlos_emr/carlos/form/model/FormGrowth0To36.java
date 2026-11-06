/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
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

/** Complete mapping of the unchanged formGrowth0_36 schema, including all twenty slots. */
@Entity
@Table(name = "formGrowth0_36")
public class FormGrowth0To36 extends AbstractModel<Integer> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Integer id;
    @Column(name = "demographic_no", nullable = false)
    private Integer demographicNo;
    @Column(name = "provider_no")
    private Integer providerNo;
    @Temporal(TemporalType.DATE)
    private Date formCreated;
    @Temporal(TemporalType.TIMESTAMP)
    // The legacy reader distinguishes TIMESTAMP from DATETIME by JDBC metadata.
    @Column(nullable = false, columnDefinition = "timestamp")
    private Date formEdited;
    @Column(length = 80)
    private String patientName;
    @Column(length = 10)
    private String recordNo;
    @Column(length = 80)
    private String motherStature;
    @Column(length = 5)
    private String gestationalAge;
    @Column(length = 80)
    private String fatherStature;
    @Column(length = 10)
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
    private Growth0To36Measurement measurement1;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_2")),
        @AttributeOverride(name = "age", column = @Column(name = "age_2", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_2", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_2", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_2", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_2", length = 25))
    })
    private Growth0To36Measurement measurement2;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_3")),
        @AttributeOverride(name = "age", column = @Column(name = "age_3", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_3", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_3", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_3", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_3", length = 25))
    })
    private Growth0To36Measurement measurement3;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_4")),
        @AttributeOverride(name = "age", column = @Column(name = "age_4", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_4", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_4", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_4", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_4", length = 25))
    })
    private Growth0To36Measurement measurement4;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_5")),
        @AttributeOverride(name = "age", column = @Column(name = "age_5", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_5", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_5", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_5", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_5", length = 25))
    })
    private Growth0To36Measurement measurement5;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_6")),
        @AttributeOverride(name = "age", column = @Column(name = "age_6", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_6", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_6", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_6", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_6", length = 25))
    })
    private Growth0To36Measurement measurement6;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_7")),
        @AttributeOverride(name = "age", column = @Column(name = "age_7", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_7", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_7", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_7", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_7", length = 25))
    })
    private Growth0To36Measurement measurement7;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_8")),
        @AttributeOverride(name = "age", column = @Column(name = "age_8", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_8", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_8", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_8", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_8", length = 25))
    })
    private Growth0To36Measurement measurement8;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_9")),
        @AttributeOverride(name = "age", column = @Column(name = "age_9", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_9", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_9", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_9", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_9", length = 25))
    })
    private Growth0To36Measurement measurement9;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_10")),
        @AttributeOverride(name = "age", column = @Column(name = "age_10", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_10", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_10", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_10", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_10", length = 25))
    })
    private Growth0To36Measurement measurement10;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_11")),
        @AttributeOverride(name = "age", column = @Column(name = "age_11", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_11", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_11", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_11", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_11", length = 25))
    })
    private Growth0To36Measurement measurement11;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_12")),
        @AttributeOverride(name = "age", column = @Column(name = "age_12", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_12", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_12", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_12", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_12", length = 25))
    })
    private Growth0To36Measurement measurement12;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_13")),
        @AttributeOverride(name = "age", column = @Column(name = "age_13", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_13", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_13", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_13", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_13", length = 25))
    })
    private Growth0To36Measurement measurement13;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_14")),
        @AttributeOverride(name = "age", column = @Column(name = "age_14", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_14", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_14", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_14", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_14", length = 25))
    })
    private Growth0To36Measurement measurement14;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_15")),
        @AttributeOverride(name = "age", column = @Column(name = "age_15", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_15", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_15", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_15", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_15", length = 25))
    })
    private Growth0To36Measurement measurement15;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_16")),
        @AttributeOverride(name = "age", column = @Column(name = "age_16", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_16", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_16", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_16", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_16", length = 25))
    })
    private Growth0To36Measurement measurement16;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_17")),
        @AttributeOverride(name = "age", column = @Column(name = "age_17", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_17", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_17", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_17", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_17", length = 25))
    })
    private Growth0To36Measurement measurement17;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_18")),
        @AttributeOverride(name = "age", column = @Column(name = "age_18", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_18", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_18", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_18", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_18", length = 25))
    })
    private Growth0To36Measurement measurement18;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_19")),
        @AttributeOverride(name = "age", column = @Column(name = "age_19", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_19", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_19", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_19", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_19", length = 25))
    })
    private Growth0To36Measurement measurement19;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "date", column = @Column(name = "date_20")),
        @AttributeOverride(name = "age", column = @Column(name = "age_20", length = 5)),
        @AttributeOverride(name = "weight", column = @Column(name = "weight_20", length = 6)),
        @AttributeOverride(name = "length", column = @Column(name = "length_20", length = 6)),
        @AttributeOverride(name = "headCirc", column = @Column(name = "headCirc_20", length = 6)),
        @AttributeOverride(name = "comment", column = @Column(name = "comment_20", length = 25))
    })
    private Growth0To36Measurement measurement20;

    @Override
    public Integer getId() { return id; }
    public void setId(Integer value) { id = value; }

    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer value) { demographicNo = value; }

    public Integer getProviderNo() { return providerNo; }
    public void setProviderNo(Integer value) { providerNo = value; }

    public Date getFormCreated() { return formCreated; }
    public void setFormCreated(Date value) { formCreated = value; }

    public Date getFormEdited() { return formEdited; }
    public void setFormEdited(Date value) { formEdited = value; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String value) { patientName = value; }

    public String getRecordNo() { return recordNo; }
    public void setRecordNo(String value) { recordNo = value; }

    public String getMotherStature() { return motherStature; }
    public void setMotherStature(String value) { motherStature = value; }

    public String getGestationalAge() { return gestationalAge; }
    public void setGestationalAge(String value) { gestationalAge = value; }

    public String getFatherStature() { return fatherStature; }
    public void setFatherStature(String value) { fatherStature = value; }

    public String getEdc() { return edc; }
    public void setEdc(String value) { edc = value; }

    /** Returns an empty value for a legacy slot whose columns are all SQL NULL. */
    public Growth0To36Measurement getMeasurement(int slot) {
        Growth0To36Measurement value = switch (slot) {
            case 1 -> measurement1;
            case 2 -> measurement2;
            case 3 -> measurement3;
            case 4 -> measurement4;
            case 5 -> measurement5;
            case 6 -> measurement6;
            case 7 -> measurement7;
            case 8 -> measurement8;
            case 9 -> measurement9;
            case 10 -> measurement10;
            case 11 -> measurement11;
            case 12 -> measurement12;
            case 13 -> measurement13;
            case 14 -> measurement14;
            case 15 -> measurement15;
            case 16 -> measurement16;
            case 17 -> measurement17;
            case 18 -> measurement18;
            case 19 -> measurement19;
            case 20 -> measurement20;
            default -> throw new IllegalArgumentException("Growth slot must be between 1 and 20");
        };
        return value == null ? new Growth0To36Measurement() : value;
    }

    public void setMeasurement(int slot, Growth0To36Measurement value) {
        switch (slot) {
            case 1 -> measurement1 = value;
            case 2 -> measurement2 = value;
            case 3 -> measurement3 = value;
            case 4 -> measurement4 = value;
            case 5 -> measurement5 = value;
            case 6 -> measurement6 = value;
            case 7 -> measurement7 = value;
            case 8 -> measurement8 = value;
            case 9 -> measurement9 = value;
            case 10 -> measurement10 = value;
            case 11 -> measurement11 = value;
            case 12 -> measurement12 = value;
            case 13 -> measurement13 = value;
            case 14 -> measurement14 = value;
            case 15 -> measurement15 = value;
            case 16 -> measurement16 = value;
            case 17 -> measurement17 = value;
            case 18 -> measurement18 = value;
            case 19 -> measurement19 = value;
            case 20 -> measurement20 = value;
            default -> throw new IllegalArgumentException("Growth slot must be between 1 and 20");
        }
    }
}
