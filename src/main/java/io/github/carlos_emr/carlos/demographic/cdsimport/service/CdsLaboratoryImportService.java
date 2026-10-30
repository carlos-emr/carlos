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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.dao.CdsLaboratoryResultDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.CdsLaboratoryResult;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsLaboratoryMeaning;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One source occurrence per bounded unit; no accession accumulator, upload handlers, or outbound side effects. */
@Service
public class CdsLaboratoryImportService implements CdsCategoryImportService {
    private static final Map<String, Integer> SCALARS=Map.ofEntries(
            Map.entry("LaboratoryName", 120), Map.entry("TestNameReportedByLab", 120), Map.entry("LabTestCode", 50),
            Map.entry("TestName", 120), Map.entry("AccessionNumber", 120), Map.entry("Result/Value", 120),
            Map.entry("Result/UnitOfMeasure", 120), Map.entry("ReferenceRange/LowLimit", 1024),
            Map.entry("ReferenceRange/HighLimit", 1024), Map.entry("ReferenceRange/ReferenceRangeText", 1024),
            Map.entry("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum", 2),
            Map.entry("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText", 50), Map.entry("TestResultStatus", 20),
            Map.entry("BlockedTestResult", 1), Map.entry("TestResultsInformationReportedByTheLab", 32768),
            Map.entry("NotesFromLab", 32768), Map.entry("PhysiciansNotes", 32768));
    private final CdsFieldParser fields;
    private final CdsClinicalNormalizationService normalization;
    private final CdsNativePreparationService preparation;
    private final CdsImportLedgerService ledger;
    private final DemographicDao demographicDao;
    private final Hl7TextMessageDao messages;
    private final Hl7TextInfoDao information;
    private final PatientLabRoutingDao routing;
    private final CdsLaboratoryResultDao results;

    public CdsLaboratoryImportService(CdsFieldParser fields, CdsClinicalNormalizationService normalization,
            CdsNativePreparationService preparation, CdsImportLedgerService ledger,
            DemographicDao demographicDao, Hl7TextMessageDao messages, Hl7TextInfoDao information,
            PatientLabRoutingDao routing, CdsLaboratoryResultDao results) {
        this.fields=fields; this.normalization=normalization; this.preparation=preparation; this.ledger=ledger;
        this.demographicDao=demographicDao; this.messages=messages; this.information=information;
        this.routing=routing; this.results=results;
    }
    @Override public Set<String> sections() { return Set.of("LaboratoryResults"); }
    @Override public void validate(CdsRecordDto record, CdsImportCommand command, CdsSnapshotDto snapshot, String xmlRelative)
            throws IOException {
        fields.selectedTexts(record, SCALARS);
        normalization.date(record, "CollectionDateTime", command); normalization.date(record, "LabRequisitionDateTime", command);
    }
    @Override public void prepare(String run, long epoch, String unit, CdsRecordDto record, CdsImportCommand command,
            CdsSnapshotDto snapshot, String xmlRelative) throws IOException {
        preparation.store(run, epoch, unit, record.effectiveSha256(), command, new CdsLaboratoryNativeDto(
                fields.selectedTexts(record, SCALARS), normalization.date(record, "CollectionDateTime", command),
                normalization.date(record, "LabRequisitionDateTime", command)));
    }

    @Override @Transactional(propagation=Propagation.MANDATORY, rollbackFor=Exception.class)
    public Map<String, String> write(String run, long epoch, String unit, int patient, CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        CdsImportUnitService.requireActive(run, epoch, unit);
        CdsLaboratoryNativeDto prepared=preparation.load(run, epoch, unit, record.effectiveSha256(), command, CdsLaboratoryNativeDto.class);
        Map<String, String> v=prepared.values();
        CdsTemporalDto collection=prepared.collection();
        CdsTemporalDto requisition=prepared.requisition();
        dateNotice(run, epoch, unit, "CollectionDateTime", collection);
        dateNotice(run, epoch, unit, "LabRequisitionDateTime", requisition);
        if ("Y".equals(v.get("BlockedTestResult"))) notice(run, epoch, unit, "CDS_RESTRICTED_LAB_RETAINED", "BlockedTestResult");
        CdsLaboratoryResult result=new CdsLaboratoryResult();
        result.setId(unit); result.setDemographicNo(patient); result.setRunId(run); result.setSourcePath(record.sourceXPath());
        result.setLaboratoryName(v.get("LaboratoryName")); result.setReportedTestName(v.get("TestNameReportedByLab"));
        result.setTestName(v.get("TestName")); result.setTestCode(v.get("LabTestCode"));
        result.setAccessionNumber(v.get("AccessionNumber")); result.setResultValue(v.get("Result/Value"));
        result.setUnitOfMeasure(v.get("Result/UnitOfMeasure")); result.setLowLimit(v.get("ReferenceRange/LowLimit"));
        result.setHighLimit(v.get("ReferenceRange/HighLimit")); result.setReferenceRange(v.get("ReferenceRange/ReferenceRangeText"));
        result.setResultStatus(v.get("TestResultStatus"));
        String flag=v.get("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum");
        result.setAbnormalFlag(flag==null?v.get("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText"):flag);
        result.setSourceBlocked(v.get("BlockedTestResult")); result.setCollectionDate(effectiveDate(collection));
        result.setCollectionPrecision(collection.precision().name()); result.setRequisitionDate(effectiveDate(requisition));
        result.setRequisitionPrecision(requisition.precision().name());
        result.setInformationFromLab(v.get("TestResultsInformationReportedByTheLab"));
        result.setNotesFromLab(v.get("NotesFromLab")); result.setPhysiciansNotes(v.get("PhysiciansNotes"));
        result.setImportedBy(command.providerNo());
        Map<String, String> targets=new LinkedHashMap<>();
        if (!"Y".equals(result.getSourceBlocked())) {
            Demographic demographic=demographicDao.getDemographicById(patient);
            if (demographic==null) throw new CdsImportException("PATIENT_TARGET_MISSING");
            Hl7TextMessage message=new Hl7TextMessage(); message.setType("CARLOS-CDS");
            message.setServiceName("CDS import"); message.setFileUploadCheckId(0);
            message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(
                    message(result, demographic, collection).getBytes(StandardCharsets.UTF_8)));
            // Reuse participating model DAOs without invoking live upload or inbox workflows.
            messages.persist(message); messages.flush();
            Hl7TextInfo info=new Hl7TextInfo(); info.setLabNumber(message.getId());
            info.setFirstName(demographic.getFirstName()); info.setLastName(demographic.getLastName());
            info.setHealthNumber(demographic.getHin()); info.setSex(demographic.getSex());
            info.setResultStatus(CdsLaboratoryMeaning.inboxAbnormal(result.getAbnormalFlag()));
            info.setFinalResultCount(CdsLaboratoryMeaning.isFinal(result.getResultStatus())?1:0);
            info.setImportedReportStatus(result.getResultStatus());
            info.setObrDate(displayDate(collection)); info.setImportedAccessionNumber(result.getAccessionNumber());
            info.setLabel(result.getTestName()!=null?result.getTestName():result.getReportedTestName());
            // LaboratoryName is 120 characters; sending_facility is only50. Do not shorten source identity.
            if (fits(result.getLaboratoryName(), 50)) info.setSendingFacility(result.getLaboratoryName());
            information.persist(info);
            PatientLabRouting patientRouting=new PatientLabRouting(message.getId(), "HL7", patient);
            routing.persist(patientRouting); routing.flush(); result.setNativeLabNo(message.getId());
            targets.put("hl7TextMessage", message.getId().toString()); targets.put("hl7TextInfo", info.getId().toString());
            targets.put("patientLabRouting", patientRouting.getId().toString());
        }
        // Blocked results stay fully structured and visible through the explicitly authorized
        // laboratory category view, without unrestricted legacy lab/portal/export/measurement copies.
        results.persist(result); targets.put("cds_laboratory_result", unit);
        return targets;
    }

    static String message(CdsLaboratoryResult r, Demographic patient, CdsTemporalDto collection) {
        String test=r.getTestName()!=null?r.getTestName():r.getReportedTestName();
        StringBuilder text=new StringBuilder(4096);
        String[] msh=new String[18]; java.util.Arrays.fill(msh, "");
        msh[0]="MSH"; msh[1]="^~\\&"; msh[2]="CARLOS-CDS"; msh[3]=escape(r.getLaboratoryName());
        msh[8]="ORU^R01"; msh[9]=r.getId(); msh[10]="P"; msh[11]="2.3"; msh[17]="UNICODE UTF-8";
        text.append(String.join("|", msh)).append('\r');
        text.append("PID|||"+patient.getDemographicNo()+"||").append(escape(patient.getLastName()))
                .append('^').append(escape(patient.getFirstName())).append("||||\r");
        String[] obr=new String[26]; java.util.Arrays.fill(obr, "");
        obr[0]="OBR"; obr[1]="1"; obr[3]=escape(r.getAccessionNumber());
        obr[4]=escape(r.getTestCode())+"^"+escape(test); obr[7]=wireDate(collection);
        obr[25]=escape(r.getResultStatus()); text.append(String.join("|", obr)).append('\r');
        String[] obx=new String[15]; java.util.Arrays.fill(obx, "");
        obx[0]="OBX"; obx[1]="1"; obx[2]="ST"; obx[3]=escape(r.getTestCode())+"^"+escape(test);
        obx[5]=escape(r.getResultValue()); obx[6]=escape(r.getUnitOfMeasure());
        obx[7]=escape(r.getReferenceRange()!=null?r.getReferenceRange():range(r.getLowLimit(), r.getHighLimit()));
        obx[8]=escape(r.getAbnormalFlag()); obx[11]=escape(r.getResultStatus()); obx[14]=wireDate(collection);
        text.append(String.join("|", obx)).append('\r');
        // Physician notes are provider-only source fields; do not append them to external laboratory narrative.
        int note=1;
        for (String value:new String[]{r.getInformationFromLab(), r.getNotesFromLab()}) {
            if (value!=null) text.append("NTE|").append(note++).append("||").append(escape(value)).append('\r');
        }
        return text.toString();
    }

    static String escape(String value) {
        if (value==null) return "";
        return value.replace("\\", "\\E\\").replace("|", "\\F\\").replace("^", "\\S\\")
                .replace("~", "\\R\\").replace("&", "\\T\\").replace("\r", "\\X0D\\").replace("\n", "\\X0A\\");
    }
    private void dateNotice(String run, long epoch, String unit, String path, CdsTemporalDto date) {
        if (date.appliedShiftDays()!=0) notice(run, epoch, unit, "CDS_SYNTHETIC_DATE_SHIFT_APPLIED", path);
        if (date.precision()==CdsTemporalDto.Precision.YEAR || date.precision()==CdsTemporalDto.Precision.MONTH) {
            notice(run, epoch, unit, "CDS_PARTIAL_DATE_RETAINED", path);
        }
    }
    private void notice(String run, long epoch, String unit, String code, String path) {
        ledger.diagnostic(run, epoch, ledger.unit(run, unit).getPatientKey(), unit, "WARNING", code, "NORMALIZATION", path,
                "Source meaning retained in the laboratory category; restriction and precision remain explicit", null, false);
    }
    private static String range(String low, String high) {
        if (low==null && high==null) return "";
        if (low==null) return "High: "+high;
        if (high==null) return "Low: "+low;
        return low+" - "+high;
    }
    private static boolean fits(String value, int capacity) { return value!=null && value.codePointCount(0, value.length())<=capacity; }
    static String wireDate(CdsTemporalDto date) {
        String zone=calendarZone(date.original()).replace(":", "").replace("Z", "+0000");
        return switch (date.precision()) {
            case UNKNOWN -> "";
            case YEAR -> date.earliest().format(DateTimeFormatter.ofPattern("uuuu"))+zone;
            case MONTH -> date.earliest().format(DateTimeFormatter.ofPattern("uuuuMM"))+zone;
            case FULL_DATE -> date.earliest().format(DateTimeFormatter.BASIC_ISO_DATE)+zone;
            case LOCAL_DATE_TIME -> date.localDateTime().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss"))+fraction(date);
            case OFFSET_DATE_TIME -> date.offsetDateTime().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss"))
                    +fraction(date)+date.offsetDateTime().format(DateTimeFormatter.ofPattern("xx"));
        };
    }

    /** Effective typed clinical value; source lexical evidence stays in the prepared DTO and source fields. */
    static String effectiveDate(CdsTemporalDto date) {
        if (date.precision()==CdsTemporalDto.Precision.UNKNOWN) return null;
        if (date.appliedShiftDays()==0) return date.original();
        return switch (date.precision()) {
            case FULL_DATE -> date.earliest().format(DateTimeFormatter.ISO_LOCAL_DATE)+calendarZone(date.original());
            case LOCAL_DATE_TIME -> date.localDateTime().format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"))+fraction(date);
            case OFFSET_DATE_TIME -> date.offsetDateTime().format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"))
                    +fraction(date)+date.offsetDateTime().getOffset().getId();
            default -> throw new CdsImportException("CDS_SYNTHETIC_PARTIAL_DATE_SHIFT_REQUIRES_REVIEW");
        };
    }

    private static String fraction(CdsTemporalDto date) {
        if (date.original()==null) return "";
        var match=java.util.regex.Pattern.compile("\\.([0-9]+)(?:Z|[+-][0-9]{2}:[0-9]{2})?$").matcher(date.original());
        return match.find()?"."+match.group(1):"";
    }
    private static String calendarZone(String original) {
        if (original==null) return "";
        var match=java.util.regex.Pattern.compile("(Z|[+-][0-9]{2}:[0-9]{2})$").matcher(original);
        return match.find()?match.group(1):"";
    }
    private static String displayDate(CdsTemporalDto date) {
        return switch (date.precision()) {
            case UNKNOWN -> "";
            case YEAR, MONTH -> date.original();
            default -> date.earliest().toString();
        };
    }
}
