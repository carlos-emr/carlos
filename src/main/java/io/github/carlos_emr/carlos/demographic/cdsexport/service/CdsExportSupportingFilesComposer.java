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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMetadataDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientSummaryDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPhysicianSummaryDto;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Objects;

/**
 * Streams DP01.06/07 supporting files into caller-owned private outputs. Physician totals and
 * paths are reconciled with actual validated patient-file summaries. Partial files remain private
 * on failure; successful composition alone never authorizes package publication or audit access.
 */
public final class CdsExportSupportingFilesComposer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CdsExportPathComposer paths = new CdsExportPathComposer();

    public record Totals(long physicians, long patientFiles) {}

    public Totals compose(
            OutputStream readMeOutput,
            OutputStream eventLogOutput,
            CdsExportPackageMetadataDto metadata,
            Iterator<CdsExportPhysicianSummaryDto> physicians,
            Iterator<CdsExportPatientSummaryDto> patients,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint, "checkpoint");
        validateMetadata(metadata);
        BufferedWriter readMe = writer(readMeOutput);
        BufferedWriter eventLog = writer(eventLogOutput);
        line(readMe, "Physician group", metadata.physicianGroup());
        line(readMe, "Group contact information", metadata.groupContactInformation());
        line(readMe, "EMR vendor and offering", "CARLOS EMR");
        line(readMe, "Offering version", metadata.applicationVersion());
        line(
                readMe,
                "Export timestamp",
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        metadata.exportTime().atZone(metadata.clinicalZone())));
        line(readMe, "Logical profile", metadata.profile().identifier());
        line(readMe, "Export container root", metadata.exportRoot());
        line(
                readMe,
                "Requested category scope",
                metadata.categories().size() == CdsCategory.values().length
                        ? "ALL_CDS_CATEGORIES"
                        : "SELECTED_CDS_CATEGORIES");
        line(
                readMe,
                "Selected categories",
                java.util.Arrays.stream(CdsCategory.values())
                        .filter(metadata.categories()::contains)
                        .map(CdsCategory::wireName)
                        .collect(java.util.stream.Collectors.joining(", ")));
        line(readMe, "Audit information access after migration", metadata.auditAccessDescription());
        readMe.write("Patient file paths below are complete within the named export container.\n");
        readMe.write(
                "NOT_SELECTED in ExportEventLog is outside the requested scope; zero is a known selected-category count.\n");
        readMe.write(
                "ReportsText and ReportsBinary count representations; Reports counts source report occurrences.\n");

        eventLog.write("PatientID");
        for (CdsCategory category : CdsCategory.values()) eventLog.write("," + category.wireName());
        eventLog.write(",ReportsText,ReportsBinary\n");

        CdsExportPatientSummaryDto patient = next(patients, checkpoint);
        String previousPhysician = null, previousPatientPath = null;
        long physicianCount = 0, totalPatients = 0;
        while (physicians.hasNext()) {
            probe(checkpoint);
            CdsExportPhysicianSummaryDto physician =
                    Objects.requireNonNull(physicians.next(), "physician");
            String folder =
                    paths.physicianFolder(
                            physician.firstName(), physician.lastName(), physician.billingNumber());
            if (previousPhysician != null && previousPhysician.compareTo(folder) >= 0) {
                throw new IllegalArgumentException("CDS_EXPORT_PHYSICIAN_ORDER_INVALID");
            }
            previousPhysician = folder;
            line(readMe, "Physician", physician.firstName() + " " + physician.lastName());
            line(readMe, "Physician OHIP billing number", physician.billingNumber());
            line(readMe, "Physician patient files", Long.toString(physician.patientFiles()));
            long count = 0;
            while (patient != null) {
                var path = paths.compose(patient.patient());
                int groupOrder = path.physicianFolder().compareTo(folder);
                if (groupOrder > 0) break;
                if (groupOrder < 0)
                    throw new IllegalArgumentException("CDS_EXPORT_PATIENT_PHYSICIAN_MISMATCH");
                String relative = path.physicianFolder() + "/" + path.patientXmlFile();
                if (previousPatientPath != null && previousPatientPath.compareTo(relative) >= 0) {
                    throw new IllegalArgumentException("CDS_EXPORT_PATIENT_FILE_ORDER_INVALID");
                }
                previousPatientPath = relative;
                validatePatient(patient, metadata);
                line(readMe, "Patient file", metadata.exportRoot() + "/" + relative);
                line(readMe, "Patient XML SHA256", patient.xmlSha256());
                event(eventLog, patient);
                count = Math.incrementExact(count);
                totalPatients = Math.incrementExact(totalPatients);
                patient = next(patients, checkpoint);
            }
            if (count != physician.patientFiles()) {
                throw new IllegalArgumentException("CDS_EXPORT_PHYSICIAN_TOTAL_MISMATCH");
            }
            physicianCount = Math.incrementExact(physicianCount);
        }
        if (patient != null)
            throw new IllegalArgumentException("CDS_EXPORT_PATIENT_PHYSICIAN_MISMATCH");
        if (totalPatients == 0) throw new IllegalArgumentException("CDS_EXPORT_NO_PATIENT_FILES");
        probe(checkpoint);
        readMe.flush();
        eventLog.flush();
        return new Totals(physicianCount, totalPatients);
    }

    private static void validateMetadata(CdsExportPackageMetadataDto metadata) {
        Objects.requireNonNull(metadata, "metadata");
        required(metadata.exportRoot(), 255);
        PathValidationUtils.validatePathComponent(metadata.exportRoot(), "CDS export container");
        required(metadata.physicianGroup(), 512);
        required(metadata.groupContactInformation(), 4096);
        required(metadata.auditAccessDescription(), 4096);
        Objects.requireNonNull(metadata.exportTime(), "exportTime");
        Objects.requireNonNull(metadata.clinicalZone(), "clinicalZone");
        new CdsExportHeaderComposer().comment(metadata.applicationVersion(), metadata.profile());
        if (!metadata.categories().contains(CdsCategory.DEMOGRAPHICS)) {
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHICS_REQUIRED");
        }
    }

    private static void validatePatient(
            CdsExportPatientSummaryDto patient, CdsExportPackageMetadataDto metadata) {
        if (!patient.occurrences().keySet().equals(metadata.categories())
                || !Long.valueOf(1).equals(patient.occurrences().get(CdsCategory.DEMOGRAPHICS))) {
            throw new IllegalArgumentException("CDS_EXPORT_CATEGORY_COUNTS_INCOMPLETE");
        }
        Long reports = patient.occurrences().get(CdsCategory.REPORTS);
        if (reports == null) {
            if (patient.textReports() != null || patient.binaryReports() != null) {
                throw new IllegalArgumentException("CDS_EXPORT_REPORT_COUNTS_INVALID");
            }
        } else if (patient.textReports() == null
                || patient.binaryReports() == null
                || patient.textReports() < 0
                || patient.binaryReports() < 0
                || patient.textReports() > reports
                || patient.binaryReports() > reports) {
            throw new IllegalArgumentException("CDS_EXPORT_REPORT_COUNTS_INVALID");
        }
    }

    private static void event(BufferedWriter output, CdsExportPatientSummaryDto patient)
            throws IOException {
        csv(output, patient.patient().senderPatientId());
        for (CdsCategory category : CdsCategory.values()) {
            output.write(",");
            Long count = patient.occurrences().get(category);
            csv(output, count == null ? "NOT_SELECTED" : Long.toString(count));
        }
        output.write(",");
        csv(
                output,
                patient.textReports() == null ? "NOT_SELECTED" : patient.textReports().toString());
        output.write(",");
        csv(
                output,
                patient.binaryReports() == null
                        ? "NOT_SELECTED"
                        : patient.binaryReports().toString());
        output.write("\n");
    }

    private static void csv(BufferedWriter output, String value) throws IOException {
        output.write('"');
        output.write(value.replace("\"", "\"\""));
        output.write('"');
    }

    private static void line(BufferedWriter output, String label, String value) throws IOException {
        output.write(label);
        output.write(": ");
        output.write(JSON.writeValueAsString(value));
        output.write("\n");
    }

    private static BufferedWriter writer(OutputStream output) {
        return new BufferedWriter(
                new OutputStreamWriter(
                        Objects.requireNonNull(output),
                        StandardCharsets.UTF_8
                                .newEncoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)),
                65536);
    }

    private static CdsExportPatientSummaryDto next(
            Iterator<CdsExportPatientSummaryDto> input, Runnable checkpoint) {
        probe(checkpoint);
        return input.hasNext() ? Objects.requireNonNull(input.next(), "patient") : null;
    }

    private static void probe(Runnable checkpoint) {
        checkpoint.run();
        if (Thread.currentThread().isInterrupted())
            throw new IllegalStateException("CDS_EXPORT_INTERRUPTED");
    }

    private static void required(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException("CDS_EXPORT_PACKAGE_METADATA_REQUIRED");
        }
    }
}
