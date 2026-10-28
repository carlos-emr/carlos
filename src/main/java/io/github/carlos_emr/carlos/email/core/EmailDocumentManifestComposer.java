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
package io.github.carlos_emr.carlos.email.core;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.utility.DocumentIdCalculator;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Builds the {@link DocumentManifest} embedded in each signed PDF an email sends
 * (carlos-emr/carlos#4691, carlos-emr/mycarlos#55).
 *
 * <p>The send's own facts come from its email log: the patient as on the chart, and the
 * provider who sends it. The clinic's name comes from the clinic record, and its portal
 * clinic ID from {@code patient_portal.clinic_id} when the portal is on. Each document's title
 * and service date come from its own record, as the chart note of the send describes them
 * ({@code EmailNoteUtil}): compose-flow attachments carry generated file names, which say
 * nothing about the document. A form has no service date that is cheap to read, so its
 * {@code service_date} is {@code null}.</p>
 *
 * <p>Each attached record must belong to the email's patient, since its manifest names that
 * patient: it is checked by the same rule as the send itself ({@link AttachmentOwnershipService}),
 * and a record that is not linked to that patient stops the send. A title longer than
 * {@value #MAX_TITLE_LENGTH} characters is shortened.</p>
 *
 * <p>{@code recipient_id} is {@code null} for now: CARLOS does not yet hold the patient's
 * portal recipient ID. {@code version} is 1 until corrections are tracked (#37).</p>
 *
 * @since 2026-10-21
 */
@Service
public class EmailDocumentManifestComposer {

    /** The manifests for one send: one per attachment, or {@code null} for an attachment that gets none. */
    @FunctionalInterface
    public interface AttachmentManifests {
        /** A send without manifests: the document ID secret is not set. */
        AttachmentManifests NONE = attachment -> null;

        /**
         * @param attachment one of the send's attachments
         * @return its manifest, or {@code null} when it has no CARLOS record behind it, such as
         *         the rendered message text
         */
        DocumentManifest forAttachment(EmailAttachment attachment);
    }

    private static final Logger logger = MiscUtils.getLogger();
    private static final Pattern ISO_DATE_PREFIX = Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*");
    /** Titles are shortened to this, well inside myCarlos's 16 KB limit for a whole manifest. */
    static final int MAX_TITLE_LENGTH = 500;

    private final ClinicDAO clinicDao;
    private final EFormDataDao eFormDataDao;
    private final DocumentDao documentDao;
    private final Hl7TextInfoDao hl7TextInfoDao;
    private final HRMDocumentDao hrmDocumentDao;
    private final FormsManager formsManager;
    private final AttachmentOwnershipService attachmentOwnership;
    private final Clock clock;

    @Autowired
    public EmailDocumentManifestComposer(ClinicDAO clinicDao, EFormDataDao eFormDataDao, DocumentDao documentDao,
            Hl7TextInfoDao hl7TextInfoDao, HRMDocumentDao hrmDocumentDao, FormsManager formsManager,
            AttachmentOwnershipService attachmentOwnership) {
        this(clinicDao, eFormDataDao, documentDao, hl7TextInfoDao, hrmDocumentDao, formsManager, attachmentOwnership,
                Clock.systemUTC());
    }

    EmailDocumentManifestComposer(ClinicDAO clinicDao, EFormDataDao eFormDataDao, DocumentDao documentDao,
            Hl7TextInfoDao hl7TextInfoDao, HRMDocumentDao hrmDocumentDao, FormsManager formsManager,
            AttachmentOwnershipService attachmentOwnership, Clock clock) {
        this.clinicDao = clinicDao;
        this.eFormDataDao = eFormDataDao;
        this.documentDao = documentDao;
        this.hl7TextInfoDao = hl7TextInfoDao;
        this.hrmDocumentDao = hrmDocumentDao;
        this.formsManager = formsManager;
        this.attachmentOwnership = attachmentOwnership;
        this.clock = clock;
    }

    /**
     * Prepares the manifests for one send. Every attachment of the send shares its issue time,
     * clinic, provider and patient.
     *
     * @param loggedInInfo the sending provider's session, for the form lookup's privilege check
     * @param emailLog the send's email log, holding the patient and the sending provider
     * @return the send's manifests, or {@link AttachmentManifests#NONE} when
     *         {@value DocumentIdCalculator#SECRET_PROPERTY} is not set
     * @throws IllegalStateException when that setting is set but invalid, so the send stops
     *         instead of going out without the manifest the clinic asked for; and, from the
     *         returned manifests, when an attached record belongs to another patient
     */
    public AttachmentManifests forSend(LoggedInInfo loggedInInfo, EmailLog emailLog) {
        Optional<DocumentIdCalculator> calculator = DocumentIdCalculator.fromCarlosProperties();
        if (calculator.isEmpty()) {
            return AttachmentManifests.NONE;
        }
        DocumentIdCalculator documentIds = calculator.get();
        long issuedAt = Instant.now(clock).getEpochSecond();
        String clinicName = clinicName();
        String clinicId = portalClinicId();
        Provider provider = emailLog.getProvider();
        String providerName = provider == null ? null : provider.getFormattedName();
        Demographic patient = emailLog.getDemographic();
        String patientName = patient == null ? null : patientName(patient);
        String birthDate = patient == null ? null : birthDate(patient);
        Integer demographicNo = patient == null ? null : patient.getDemographicNo();

        return attachment -> {
            DocumentType type = attachment.getDocumentType();
            int recordId = attachment.getDocumentId();
            if (type == null || recordId < 1) {
                return null;
            }
            requireTheEmailsPatient(type, recordId, demographicNo);
            RecordDetails details = recordDetails(loggedInInfo, type, recordId, demographicNo);
            return new DocumentManifest(
                    documentIds.documentId(documentIdSource(type), String.valueOf(recordId)),
                    1, issuedAt, clinicId, clinicName, providerName, null, patientName, birthDate,
                    details.title(), typeHint(type), details.serviceDate());
        };
    }

    /**
     * The source label hashed into the document ID. These values are part of every issued ID,
     * so they are written out here rather than taken from the enum, and must never change.
     */
    static String documentIdSource(DocumentType type) {
        return switch (type) {
            case EFORM -> "eform";
            case DOC -> "document";
            case LAB -> "lab";
            case HRM -> "hrm";
            case FORM -> "form";
        };
    }

    /** The {@code document.type} hint; myCarlos treats a value it does not know as {@code other}. */
    static String typeHint(DocumentType type) {
        return switch (type) {
            case EFORM -> "eform";
            case DOC -> "document";
            case LAB -> "lab_report";
            case HRM -> "hospital_report";
            case FORM -> "form";
        };
    }

    record RecordDetails(String title, String serviceDate) {
        static final RecordDetails UNKNOWN = new RecordDetails(null, null);

        RecordDetails {
            if (title != null && title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
        }
    }

    /**
     * Each attached record must belong to the email's patient: its manifest names that patient, and
     * myCarlos files the document under them. This is the send's own rule
     * ({@link AttachmentOwnershipService#allBelongToDemographic}), checked again here because the
     * manifest is built from the record: a record not linked to the email's patient (another
     * patient's, unlinked, deleted or gone) stops the send, like a manifest that cannot be built.
     * A form is not checked here: its renderer reads it for the email's patient only.
     */
    private void requireTheEmailsPatient(DocumentType type, int recordId, Integer demographicNo) {
        if (!AttachmentOwnershipService.isVerifiable(type)) {
            return;
        }
        if (!attachmentOwnership.allBelongToDemographic(type, demographicNo, List.of(recordId))) {
            throw new IllegalStateException("An attached record does not belong to the email's patient");
        }
    }

    private RecordDetails recordDetails(LoggedInInfo loggedInInfo, DocumentType type, int recordId,
            Integer demographicNo) {
        return switch (type) {
            case EFORM -> eFormDetails(eFormDataDao.find(recordId));
            case DOC -> documentDetails(documentDao.find(recordId));
            case LAB -> labDetails(hl7TextInfoDao.findLabId(recordId));
            case HRM -> reportDetails(hrmDocumentDao.find(recordId));
            case FORM -> formDetails(loggedInInfo, recordId, demographicNo);
        };
    }

    private static RecordDetails eFormDetails(EFormData eForm) {
        return eForm == null ? RecordDetails.UNKNOWN
                : new RecordDetails(firstPresent(eForm.getSubject(), eForm.getFormName()), isoDate(eForm.getFormDate()));
    }

    private static RecordDetails documentDetails(Document document) {
        return document == null ? RecordDetails.UNKNOWN
                : new RecordDetails(blankToNull(document.getDocdesc()), isoDate(document.getObservationdate()));
    }

    private static RecordDetails labDetails(Hl7TextInfo lab) {
        return lab == null ? RecordDetails.UNKNOWN
                : new RecordDetails(firstPresent(lab.getLabel(), lab.getDiscipline()), isoDate(lab.getObrDate()));
    }

    private static RecordDetails reportDetails(HRMDocument report) {
        return report == null ? RecordDetails.UNKNOWN
                : new RecordDetails(firstPresent(report.getDescription(), report.getReportType()),
                        isoDate(report.getReportDate()));
    }

    /** A form whose name cannot be read for this sender (no form privilege, say) gets no title. */
    private RecordDetails formDetails(LoggedInInfo loggedInInfo, int formId, Integer demographicNo) {
        PatientForm form;
        try {
            form = formsManager.getFormById(loggedInInfo, formId, demographicNo);
        } catch (SecurityException _) {
            return RecordDetails.UNKNOWN;
        }
        return form == null ? RecordDetails.UNKNOWN : new RecordDetails(blankToNull(form.getFormName()), null);
    }

    private String clinicName() {
        Clinic clinic = clinicDao.getClinic();
        return clinic == null ? null : blankToNull(clinic.getClinicName());
    }

    /**
     * The portal's clinic ID, or {@code null} when the portal is off. A portal that is on but
     * misconfigured is reported by the portal's own checks; email does not depend on it, so the
     * manifest then carries no clinic ID rather than stopping the send.
     */
    static String portalClinicId() {
        if (!PatientPortalSettings.isConfigured()) {
            return null;
        }
        try {
            return PatientPortalSettings.fromCarlosProperties().clinicId();
        } catch (PatientPortalConfigurationException _) {
            logger.warn("Patient portal settings are incomplete; signed PDFs carry no portal clinic ID");
            return null;
        }
    }

    private static String patientName(Demographic patient) {
        String last = blankToNull(patient.getLastName());
        String first = blankToNull(patient.getFirstName());
        if (last == null && first == null) {
            return null;
        }
        // "Last, First" as on the chart, without the alias, matching the delivery manifest.
        return (last == null ? "" : last) + ", " + (first == null ? "" : first);
    }

    /** The birth date as {@code yyyy-MM-dd}, or {@code null} when the chart's parts do not form a date. */
    static String birthDate(Demographic patient) {
        String year = blankToNull(patient.getYearOfBirth());
        String month = blankToNull(patient.getMonthOfBirth());
        String day = blankToNull(patient.getDateOfBirth());
        if (year == null || month == null || day == null) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)).toString();
        } catch (NumberFormatException | DateTimeException _) {
            return null;
        }
    }

    private static String isoDate(Date date) {
        if (date == null) {
            return null;
        }
        // getTime(), not toInstant(): Hibernate hands back java.sql.Date, whose toInstant() throws.
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    /** An HL7 date such as {@code 2026-10-01 08:30:00}, cut to its date, or {@code null}. */
    private static String isoDate(String date) {
        if (date == null || !ISO_DATE_PREFIX.matcher(date).matches()) {
            return null;
        }
        String day = date.substring(0, 10);
        try {
            return LocalDate.parse(day).toString();
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    private static String firstPresent(String preferred, String fallback) {
        String value = blankToNull(preferred);
        return value != null ? value : blankToNull(fallback);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
