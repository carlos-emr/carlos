/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;

/**
 * JPA entity representing an HL7 OBX (Observation Result) segment in the BC PathNet lab integration.
 * Maps to the {@code hl7_obx} table, storing individual observation values including
 * identifier, result value, units, reference range, abnormal flags, and result status.
 *
 * @since 2026-03-17
 */
@Entity
@Table(name = "hl7_obx")
public class Hl7Obx extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "obx_id")
    private Integer id;

    @Column(name = "obr_id")
    private int obrId;

    @Column(name = "set_id")
    private String setId;

    @Column(name = "value_type")
    private String valueType;

    @Column(name = "observation_identifier")
    private String observationIdentifier;

    @Column(name = "observation_sub_id")
    private String observationSubId;

    @Column(name = "observation_results")
    private String observationResults;

    private String units;

    @Column(name = "reference_range")
    private String referenceRange;

    @Column(name = "abnormal_flags")
    private String abnormalFlags;

    private String probability;

    @Column(name = "nature_of_abnormal_test")
    private String natureOfAbnormalTest;

    @Column(name = "observation_result_status")
    private String observationResultStatus;

    @Column(name = "date_last_normal_value")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateLastNormalValue;

    @Column(name = "user_defined_access_checks")
    private String userDefinedAccessChecks;

    @Column(name = "observation_date_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date observationDateTime;

    @Column(name = "producer_id")
    private String producerId;

    @Column(name = "responsible_observer")
    private String responsibleObserver;

    @Column(name = "observation_method")
    private String observationMethod;

    private String note;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getObrId() {
        return obrId;
    }

    public void setObrId(int obrId) {
        this.obrId = obrId;
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getObservationIdentifier() {
        return observationIdentifier;
    }

    public void setObservationIdentifier(String observationIdentifier) {
        this.observationIdentifier = observationIdentifier;
    }

    public String getObservationSubId() {
        return observationSubId;
    }

    public void setObservationSubId(String observationSubId) {
        this.observationSubId = observationSubId;
    }

    public String getObservationResults() {
        return observationResults;
    }

    public void setObservationResults(String observationResults) {
        this.observationResults = observationResults;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public String getReferenceRange() {
        return referenceRange;
    }

    public void setReferenceRange(String referenceRange) {
        this.referenceRange = referenceRange;
    }

    public String getAbnormalFlags() {
        return abnormalFlags;
    }

    public void setAbnormalFlags(String abnormalFlags) {
        this.abnormalFlags = abnormalFlags;
    }

    public String getProbability() {
        return probability;
    }

    public void setProbability(String probability) {
        this.probability = probability;
    }

    public String getNatureOfAbnormalTest() {
        return natureOfAbnormalTest;
    }

    public void setNatureOfAbnormalTest(String natureOfAbnormalTest) {
        this.natureOfAbnormalTest = natureOfAbnormalTest;
    }

    public String getObservationResultStatus() {
        return observationResultStatus;
    }

    public void setObservationResultStatus(String observationResultStatus) {
        this.observationResultStatus = observationResultStatus;
    }

    public Date getDateLastNormalValue() {
        return dateLastNormalValue;
    }

    public void setDateLastNormalValue(Date dateLastNormalValue) {
        this.dateLastNormalValue = dateLastNormalValue;
    }

    public String getUserDefinedAccessChecks() {
        return userDefinedAccessChecks;
    }

    public void setUserDefinedAccessChecks(String userDefinedAccessChecks) {
        this.userDefinedAccessChecks = userDefinedAccessChecks;
    }

    public Date getObservationDateTime() {
        return observationDateTime;
    }

    public void setObservationDateTime(Date observationDateTime) {
        this.observationDateTime = observationDateTime;
    }

    public String getProducerId() {
        return producerId;
    }

    public void setProducerId(String producerId) {
        this.producerId = producerId;
    }

    public String getResponsibleObserver() {
        return responsibleObserver;
    }

    public void setResponsibleObserver(String responsibleObserver) {
        this.responsibleObserver = responsibleObserver;
    }

    public String getObservationMethod() {
        return observationMethod;
    }

    public void setObservationMethod(String observationMethod) {
        this.observationMethod = observationMethod;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }


}
