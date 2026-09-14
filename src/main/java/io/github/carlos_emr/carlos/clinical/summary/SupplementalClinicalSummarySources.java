/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.form.FrmRecordFactory;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMReportParser;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import static io.github.carlos_emr.carlos.clinical.summary.ChartClinicalSummaryProvider.*;

/** Patient-scoped clinical source readers. Every module checks its own read permission before lookup. */
final class SupplementalClinicalSummarySources {
    private final SecurityInfoManager security;

    SupplementalClinicalSummarySources(SecurityInfoManager security) { this.security = security; }

    void append(LoggedInInfo user, int patient, ObjectNode artifact) {
        if (allowed(user, patient, artifact, "_measurement", "measurements")) measurements(patient, artifact);
        if (allowed(user, patient, artifact, "_lab", "labs")) labs(user, patient, artifact);
        if (allowed(user, patient, artifact, "_edoc", "documents")) documents(user, patient, artifact);
        if (allowed(user, patient, artifact, "_eform", "eforms")) eforms(user, patient, artifact);
        if (allowed(user, patient, artifact, "_form", "forms")) forms(user, patient, artifact);
        if (allowed(user, patient, artifact, "_hrm", "hospital_reports")) hospitalReports(user, patient, artifact);
        if (allowed(user, patient, artifact, "_prevention", "preventions")) preventions(user, patient, artifact);
        if (allowed(user, patient, artifact, "_con", "consultations")) consultations(patient, artifact);
        if (allowed(user, patient, artifact, "_dxresearch", "problems")) problems(patient, artifact);
    }

    private boolean allowed(LoggedInInfo user, int patient, ObjectNode artifact, String privilege, String module) {
        if (security.hasPrivilege(user, privilege, "r", patient)) return true;
        warning(artifact, module + "_unavailable", module.replace('_', ' ') + " are not included because read access is unavailable.");
        return false;
    }

    private void measurements(int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "measurements", "Recorded measurements and vital signs");
        for (var measurement : SpringUtils.getBean(MeasurementDao.class).findByDemographicNo(patient)) {
            requirePatient(patient, measurement.getDemographicId());
            String text = "Measurement: " + value(measurement.getType()) + "\nValue: " + value(measurement.getDataField())
                    + "\nMeasuring instruction: " + value(measurement.getMeasuringInstruction())
                    + "\nComments: " + value(measurement.getComments());
            record(artifact, patient, "measurement-" + measurement.getId(), "Measurement", date(measurement.getDateObserved()), text, claims);
        }
    }

    private void labs(LoggedInInfo user, int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "labs", "Recorded laboratory reports");
        Set<Integer> seen = new HashSet<>();
        var manager = SpringUtils.getBean(LabManager.class);
        for (var route : SpringUtils.getBean(PatientLabRoutingDao.class).findByDemographicAndLabType(patient, PatientLabRoutingDao.HL7)) {
            requirePatient(patient, route.getDemographicNo());
            requirePatient(PatientLabRoutingDao.HL7, route.getLabType());
            if (!seen.add(route.getLabNo())) continue;
            String id = "lab-" + route.getLabNo();
            var message = manager.getHl7Message(user, route.getLabNo());
            if (message == null) {
                extracted(artifact, patient, id, "Laboratory report", "Not recorded",
                        new ClinicalSummaryTextExtractor.Extract("", false, "Linked laboratory report is unavailable."), claims);
                continue;
            }
            requirePatient(route.getLabNo(), message.getId());
            ClinicalSummaryTextExtractor.Extract extract;
            try {
                var parser = Factory.getHandler(message);
                StringBuilder text = new StringBuilder();
                boolean omitted = false;
                for (int obr = 0; obr < parser.getOBRCount(); obr++) {
                    text.append("\nReport: ").append(value(parser.getOBRName(obr)));
                    for (int comment = 0; comment < parser.getOBRCommentCount(obr); comment++) {
                        text.append("\nReport comment: ").append(value(parser.getOBRComment(obr, comment)));
                    }
                    for (int obx = 0; obx < parser.getOBXCount(obr); obx++) {
                        if ("ED".equals(parser.getOBXValueType(obr, obx)) || "RP".equals(parser.getOBXValueType(obr, obx))) {
                            omitted = true;
                            text.append("\nEmbedded attachment requires review of the original laboratory report.");
                            continue;
                        }
                        text.append("\nObservation: ").append(value(parser.getOBXName(obr, obx)))
                                .append("\nResult: ").append(value(parser.getOBXResult(obr, obx)))
                                .append("\nUnits: ").append(value(parser.getOBXUnits(obr, obx)))
                                .append("\nReference range: ").append(value(parser.getOBXReferenceRange(obr, obx)))
                                .append("\nAbnormal flag: ").append(value(parser.getOBXAbnormalFlag(obr, obx)))
                                .append("\nResult status: ").append(value(parser.getOBXResultStatus(obr, obx)))
                                .append("\nObservation time: ").append(value(parser.getTimeStamp(obr, obx)));
                        for (int comment = 0; comment < parser.getOBXCommentCount(obr, obx); comment++) {
                            text.append("\nObservation comment: ").append(value(parser.getOBXComment(obr, obx, comment)));
                        }
                    }
                }
                extract = new ClinicalSummaryTextExtractor.Extract(text.toString(), !omitted && !text.isEmpty(),
                        omitted ? "Embedded laboratory attachments were not extracted; review the original report."
                                : "Laboratory parser returned no readable observations.");
            } catch (RuntimeException unreadable) {
                extract = new ClinicalSummaryTextExtractor.Extract("", false, "Laboratory report could not be read; open the original.");
            }
            extracted(artifact, patient, id, "Laboratory report", date(message.getCreated()), extract, claims);
        }
        for (String type : new String[]{"MDS", "CML", "BCP", "Epsilon"}) {
            for (var route : SpringUtils.getBean(PatientLabRoutingDao.class).findByDemographicAndLabType(patient, type)) {
                requirePatient(patient, route.getDemographicNo());
                requirePatient(type, route.getLabType());
                extracted(artifact, patient, "lab-" + type + "-" + route.getLabNo(), "Legacy laboratory report", date(route.getCreated()),
                        new ClinicalSummaryTextExtractor.Extract("", false, "Legacy laboratory format has no summary text reader; open the original report."), claims);
            }
        }
    }

    private void documents(LoggedInInfo user, int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "documents", "Chart documents");
        var manager = SpringUtils.getBean(DocumentManager.class);
        // This is the same program/facility-filtered list as the document UI. Public templates
        // are not patient evidence. Recheck the ownership relation before any file access.
        for (var document : EDocUtil.listDocs(user, "demographic", String.valueOf(patient), "all",
                EDocUtil.PRIVATE, EDocUtil.EDocSort.OBSERVATIONDATE, "active")) {
            int documentId = Integer.parseInt(document.getDocId());
            var ownership = manager.getCtlDocumentByDocumentId(user, documentId);
            if (ownership == null || !ownership.isDemographicDocument()) throw new SecurityException("Document scope unavailable");
            requirePatient(patient, ownership.getId().getModuleId());
            requirePatient(documentId, ownership.getId().getDocumentNo());
            ClinicalSummaryTextExtractor.Extract extract;
            try {
                extract = ClinicalSummaryTextExtractor.document(document.getFileName(), document.getContentType());
            } catch (IOException | IllegalArgumentException unreadable) {
                extract = new ClinicalSummaryTextExtractor.Extract("", false, "Document content is unavailable or unreadable; open the original.");
            }
            extracted(artifact, patient, "document-" + documentId, value(document.getDescription()),
                    value(document.getObservationDate()), extract, claims);
        }
    }

    private void eforms(LoggedInInfo user, int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "eforms", "Stored electronic forms");
        for (var form : SpringUtils.getBean(FormsManager.class).findByDemographicId(user, patient)) {
            requirePatient(patient, form.getDemographicId());
            if (!form.isCurrent()) continue;
            extracted(artifact, patient, "eform-" + form.getId(), value(form.getFormName()), date(form.getFormDate()),
                    ClinicalSummaryTextExtractor.html(form.getFormData()), claims);
        }
    }

    private void forms(LoggedInInfo user, int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "forms", "Stored encounter forms");
        for (var form : SpringUtils.getBean(FormsManager.class).getEncounterFormsbyDemographicNumber(user, patient, true, false)) {
            requirePatient(String.valueOf(patient), form.getDemoNo());
            String table = form.getTable();
            if (table == null || !table.matches("form[A-Za-z0-9_]+")) throw new SecurityException("Unknown form storage");
            String id = table + "-" + Integer.parseInt(form.getFormId());
            var reader = new FrmRecordFactory().factory(table.substring(4));
            ClinicalSummaryTextExtractor.Extract extract;
            if (reader == null) {
                extract = new ClinicalSummaryTextExtractor.Extract("", false, "No text reader is available for this form; open the original.");
            } else {
                try {
                    var fields = reader.getFormRecord(user, patient, Integer.parseInt(form.getFormId()));
                    requirePatient(String.valueOf(patient), fields.getProperty("demographic_no"));
                    StringBuilder text = new StringBuilder();
                    for (String key : new TreeSet<>(fields.stringPropertyNames())) {
                        text.append(key).append(": ").append(fields.getProperty(key)).append('\n');
                    }
                    extract = new ClinicalSummaryTextExtractor.Extract(text.toString(), false,
                            "Stored form fields only. Field names, calculated values and visual layout require review of the original form.");
                } catch (SQLException unreadable) {
                    extract = new ClinicalSummaryTextExtractor.Extract("", false, "Stored form could not be read; open the original.");
                }
            }
            extracted(artifact, patient, id, value(form.getFormName()), value(form.getCreated()), extract, claims);
        }
    }

    private void hospitalReports(LoggedInInfo user, int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "hospital_reports", "Hospital reports");
        Set<Integer> seen = new HashSet<>();
        for (var route : SpringUtils.getBean(HRMDocumentToDemographicDao.class).findByDemographicNo(String.valueOf(patient))) {
            requirePatient(patient, route.getDemographicNo());
            if (!seen.add(route.getHrmDocumentId())) continue;
            var document = SpringUtils.getBean(HRMDocumentDao.class).find(route.getHrmDocumentId());
            if (document == null) throw new IllegalArgumentException("Hospital report unavailable");
            requirePatient(route.getHrmDocumentId(), document.getId());
            ClinicalSummaryTextExtractor.Extract extract;
            try {
                var report = HRMReportParser.parseReport(user, document.getId());
                StringBuilder text = new StringBuilder();
                boolean omitted = false;
                for (var received : report.getDocumentRoot().getPatientRecord().getReportsReceived()) {
                    String content = received.getContent() == null ? null : received.getContent().getTextContent();
                    if (content == null || content.isBlank()) {
                        omitted = true;
                    } else {
                        text.append("\nReport status: ").append(value(document.getReportStatus())).append('\n').append(content);
                    }
                }
                extract = new ClinicalSummaryTextExtractor.Extract(text.toString(), !omitted && !text.isEmpty(),
                        "Hospital report contains unreadable or binary content; open the original.");
            } catch (RuntimeException unreadable) {
                extract = new ClinicalSummaryTextExtractor.Extract("", false, "Hospital report could not be read; open the original.");
            }
            extracted(artifact, patient, "hrm-" + document.getId(), value(document.getDescription()), date(document.getReportDate()), extract, claims);
        }
    }

    private void preventions(LoggedInInfo user, int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "preventions", "Preventions and immunizations");
        for (var prevention : SpringUtils.getBean(PreventionManager.class).getPreventionsByDemographicNo(user, patient)) {
            requirePatient(patient, prevention.getDemographicId());
            if (prevention.isDeleted()) continue;
            StringBuilder text = new StringBuilder("Prevention: ").append(value(prevention.getPreventionType()))
                    .append("\nRefused: ").append(prevention.isRefused()).append("\nIneligible: ").append(prevention.isIneligible())
                    .append("\nCompleted externally: ").append(prevention.isCompletedExternally())
                    .append("\nRecorded next date: ").append(date(prevention.getNextDate()));
            prevention.setPreventionExtendedProperties();
            var fields = prevention.getPreventionExtendedProperties();
            for (String key : new TreeSet<>(fields.keySet())) text.append('\n').append(key).append(": ").append(value(fields.get(key)));
            record(artifact, patient, "prevention-" + prevention.getId(), "Prevention", date(prevention.getPreventionDate()), text.toString(), claims);
        }
    }

    private void problems(int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "problems", "Recorded problem registry");
        var dao = SpringUtils.getBean(DxresearchDAO.class);
        for (var problem : dao.findNonDeletedByDemographicNo(patient)) {
            requirePatient(patient, problem.getDemographicNo());
            String text = "Recorded problem: " + value(dao.getDescription(problem.getCodingSystem(), problem.getDxresearchCode()))
                    + "\nCoding system: " + value(problem.getCodingSystem()) + "\nCode: " + value(problem.getDxresearchCode())
                    + "\nRecorded status: " + problem.getStatus() + "\nLast updated: " + date(problem.getUpdateDate());
            record(artifact, patient, "problem-" + problem.getId(), "Problem registry entry", date(problem.getStartDate()), text, claims);
        }
    }

    private void consultations(int patient, ObjectNode artifact) {
        ArrayNode claims = section(artifact, "consultations", "Consultation requests and responses");
        for (var consult : SpringUtils.getBean(ConsultationRequestDao.class).findByDemographicNo(patient)) {
            requirePatient(patient, consult.getDemographicId());
            String text = "Referral reason: " + value(consult.getReasonForReferral())
                    + "\nClinical information: " + value(consult.getClinicalInfo())
                    + "\nRecorded medications: " + value(consult.getCurrentMeds())
                    + "\nRecorded allergies: " + value(consult.getAllergies())
                    + "\nConcurrent problems: " + value(consult.getConcurrentProblems())
                    + "\nStatus: " + value(consult.getStatus()) + " " + value(consult.getStatusText())
                    + "\nUrgency: " + value(consult.getUrgency())
                    + "\nAppointment: " + date(consult.getAppointmentDate())
                    + "\nFollow-up: " + date(consult.getFollowUpDate());
            record(artifact, patient, "consult-request-" + consult.getId(), "Consultation request", date(consult.getReferralDate()), text, claims);
        }
        for (var consult : SpringUtils.getBean(ConsultResponseDao.class).findByDemographicNo(patient)) {
            requirePatient(patient, consult.getDemographicNo());
            String text = "Referral reason: " + value(consult.getReferralReason())
                    + "\nClinical information: " + value(consult.getClinicalInfo())
                    + "\nExamination: " + value(consult.getExamination()) + "\nImpression: " + value(consult.getImpression())
                    + "\nPlan: " + value(consult.getPlan()) + "\nRecorded medications: " + value(consult.getCurrentMeds())
                    + "\nRecorded allergies: " + value(consult.getAllergies())
                    + "\nConcurrent problems: " + value(consult.getConcurrentProblems())
                    + "\nStatus: " + value(consult.getStatus()) + "\nUrgency: " + value(consult.getUrgency())
                    + "\nAppointment: " + date(consult.getAppointmentDate()) + "\nAppointment note: " + value(consult.getAppointmentNote())
                    + "\nFollow-up: " + date(consult.getFollowUpDate());
            record(artifact, patient, "consult-response-" + consult.getId(), "Consultation response", date(consult.getResponseDate()), text, claims);
        }
    }

    private static void record(ObjectNode artifact, int patient, String id, String title, String date, String text, ArrayNode claims) {
        addRecord(artifact, "demographic-" + patient, id, title, date, text, text, title, claims);
    }

    private static void extracted(ObjectNode artifact, int patient, String id, String title, String date,
            ClinicalSummaryTextExtractor.Extract extract, ArrayNode claims) {
        if (extract.text() == null || extract.text().isBlank()) {
            addSource(artifact, "demographic-" + patient, id, title, date, "Content not extracted. " + extract.reason(), false);
            ObjectNode coverage = (ObjectNode) artifact.get("coverage").get(artifact.get("coverage").size() - 1);
            coverage.put("status", "excluded").put("reason", extract.reason());
        } else {
            record(artifact, patient, id, title, date, extract.text(), claims);
        }
        if (!extract.complete()) {
            ((ArrayNode) artifact.get("validation")).addObject().put("severity", "warning")
                    .put("code", "source_extraction_incomplete").put("message", extract.reason()).putArray("source_ids").add(id);
        }
    }
}
