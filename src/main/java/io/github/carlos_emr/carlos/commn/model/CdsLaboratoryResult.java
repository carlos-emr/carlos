/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Native structured CDS laboratory result; restricted results never enter unrestricted legacy mirrors. */
@Entity @Table(name = "cds_laboratory_result")
public class CdsLaboratoryResult extends AbstractModel<String> {
    @Id @Column(length=64) private String id;
    private Integer demographicNo;
    @Column(length=36) private String runId;
    @Column(length=2048) private String sourcePath;
    @Column(length=120) private String laboratoryName;
    @Column(length=120) private String reportedTestName;
    @Column(length=120) private String testName;
    @Column(length=50) private String testCode;
    @Column(length=120) private String accessionNumber;
    @Column(length=120) private String resultValue;
    @Column(length=120) private String unitOfMeasure;
    @Column(length=1024) private String lowLimit;
    @Column(length=1024) private String highLimit;
    @Column(length=1024) private String referenceRange;
    @Column(length=20) private String resultStatus;
    @Column(length=50) private String abnormalFlag;
    @Column(length=1) private String sourceBlocked;
    @Column(length=128) private String collectionDate;
    @Column(length=32) private String collectionPrecision;
    @Column(length=128) private String requisitionDate;
    @Column(length=32) private String requisitionPrecision;
    @Lob @Column(columnDefinition="MEDIUMTEXT") private String informationFromLab;
    @Lob @Column(columnDefinition="MEDIUMTEXT") private String notesFromLab;
    @Lob @Column(columnDefinition="MEDIUMTEXT") private String physiciansNotes;
    private Integer nativeLabNo;
    @Column(length=16) private String importedBy;

    @Override public String getId() { return id; }
    public void setId(String value) { id=value; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer value) { demographicNo=value; }
    public String getRunId() { return runId; }
    public void setRunId(String value) { runId=value; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String value) { sourcePath=value; }
    public String getLaboratoryName() { return laboratoryName; }
    public void setLaboratoryName(String value) { laboratoryName=value; }
    public String getReportedTestName() { return reportedTestName; }
    public void setReportedTestName(String value) { reportedTestName=value; }
    public String getTestName() { return testName; }
    public void setTestName(String value) { testName=value; }
    public String getTestCode() { return testCode; }
    public void setTestCode(String value) { testCode=value; }
    public String getAccessionNumber() { return accessionNumber; }
    public void setAccessionNumber(String value) { accessionNumber=value; }
    public String getResultValue() { return resultValue; }
    public void setResultValue(String value) { resultValue=value; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String value) { unitOfMeasure=value; }
    public String getLowLimit() { return lowLimit; }
    public void setLowLimit(String value) { lowLimit=value; }
    public String getHighLimit() { return highLimit; }
    public void setHighLimit(String value) { highLimit=value; }
    public String getReferenceRange() { return referenceRange; }
    public void setReferenceRange(String value) { referenceRange=value; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String value) { resultStatus=value; }
    public String getAbnormalFlag() { return abnormalFlag; }
    public void setAbnormalFlag(String value) { abnormalFlag=value; }
    public String getSourceBlocked() { return sourceBlocked; }
    public void setSourceBlocked(String value) { sourceBlocked=value; }
    public String getCollectionDate() { return collectionDate; }
    public void setCollectionDate(String value) { collectionDate=value; }
    public String getCollectionPrecision() { return collectionPrecision; }
    public void setCollectionPrecision(String value) { collectionPrecision=value; }
    public String getRequisitionDate() { return requisitionDate; }
    public void setRequisitionDate(String value) { requisitionDate=value; }
    public String getRequisitionPrecision() { return requisitionPrecision; }
    public void setRequisitionPrecision(String value) { requisitionPrecision=value; }
    public String getInformationFromLab() { return informationFromLab; }
    public void setInformationFromLab(String value) { informationFromLab=value; }
    public String getNotesFromLab() { return notesFromLab; }
    public void setNotesFromLab(String value) { notesFromLab=value; }
    public String getPhysiciansNotes() { return physiciansNotes; }
    public void setPhysiciansNotes(String value) { physiciansNotes=value; }
    public Integer getNativeLabNo() { return nativeLabNo; }
    public void setNativeLabNo(Integer value) { nativeLabNo=value; }
    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String value) { importedBy=value; }
    @Override public String toString() { return "CdsLaboratoryResult[protected]"; }
}
