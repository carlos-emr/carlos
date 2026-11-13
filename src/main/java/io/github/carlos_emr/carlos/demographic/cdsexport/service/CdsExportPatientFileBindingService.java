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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Binds required physician/patient archive names to frozen roster ownership, captured
 * provider metadata and actual validated XML. No operator/provider fallback supplies a missing
 * roster physician, clinical author, birth date or OHIP number: a patient without a usable
 * physician goes in the Unassigned folder and a physician without a number keeps an empty one. Source registration authority
 * remains a separate profile-qualification gate; a usable name is not a disclosure grant.
 */
@Service
public class CdsExportPatientFileBindingService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportPatientFileBindingService(CdsExportNativeCaptureService captures) {
        this.captures = Objects.requireNonNull(captures);
        rows = new CdsExportCapturedTextRowsReader(captures);
    }

    public CdsExportPatientFileDto bind(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportPatientDocumentService.Document document,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        patient.require(lease);
        if (!lease.runId().equals(document.runId())
                || lease.epoch() != document.epoch()
                || patient.patient() != document.patient()
                || !source.manifest().plaintextSha256().equals(document.sourceManifestSha256()))
            invalid();
        var nativeRows = new ArrayList<CdsExportCapturedTextRowDto>();
        long[] bytes = {0};
        var read =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        Set.of("demographic", "provider"),
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        1 << 20,
                        control,
                        row -> {
                            control.checkpoint();
                            bytes[0] = Math.addExact(bytes[0], 1024L);
                            for (String value : row.fields().values())
                                if (value != null)
                                    bytes[0] =
                                            Math.addExact(
                                                    bytes[0],
                                                    64L
                                                            + value.getBytes(StandardCharsets.UTF_8)
                                                                            .length
                                                                    * 2L);
                            if (nativeRows.size() >= bounds.lookupRows()
                                    || bytes[0] > bounds.indexBytes()) limit();
                            nativeRows.add(row);
                        });
        if (!read.equals(source.capture())) invalid();
        var result =
                fromRows(
                        patient.patient(),
                        patient.selection().physicianNo(),
                        document.validation(),
                        nativeRows,
                        control::checkpoint);
        captures.authorizeCapturedSources(lease, patient, tables, source.capture(), control);
        patient.require(lease);
        control.checkpoint();
        return result;
    }

    /** Pure binding over already authenticated, same-snapshot rows; exposed to owning orchestration tests only. */
    static CdsExportPatientFileDto fromRows(
            int patient,
            String selectedPhysician,
            CdsExportXmlValidationDto xml,
            List<CdsExportCapturedTextRowDto> rows,
            Runnable checkpoint)
            throws IOException {
        if (patient < 1 || xml == null || !("C" + patient).equals(xml.senderPatientId())) invalid();
        var demographics = rows.stream().filter(row -> row.table().equals("demographic")).toList();
        if (demographics.size() != 1
                || !Integer.toString(patient)
                        .equals(demographics.getFirst().fields().get("demographic_no"))) invalid();
        String physician = demographics.getFirst().fields().get("provider_no");
        if (!Objects.equals(physician, selectedPhysician))
            throw new CdsExportReviewException(CdsExportReviewException.Reason.SOURCE_CHANGED);
        // The physician folder is a packaging convention (DP01.11) that no CARLOS importer reads.
        // A patient without a primary physician, or whose physician's provider record is gone,
        // goes in the Unassigned folder; a physician without a usable billing number keeps the
        // name with an empty number, as the legacy exporter wrote it. Neither holds the patient.
        String physicianFirst = null, physicianLast = null, billing = null;
        if (physician != null && !physician.isBlank()) {
            var providers =
                    rows.stream()
                            .filter(
                                    row ->
                                            row.table().equals("provider")
                                                    && physician.equals(
                                                            row.fields().get("provider_no")))
                            .toList();
            if (providers.size() > 1) throw review();
            checkpoint.run();
            if (providers.size() == 1) {
                // The folder names the physician as the provider record does; the
                // PrimaryPhysician element is decided separately by the demographic mapper.
                var fields = providers.getFirst().fields();
                String stored = fields.get("ohip_no");
                String number = stored == null ? "" : stored.strip();
                if (!number.matches("[A-Za-z0-9]{1,6}")) number = "";
                try {
                    new CdsExportPathComposer()
                            .physicianFolder(fields.get("first_name"), fields.get("last_name"), number);
                    physicianFirst = fields.get("first_name");
                    physicianLast = fields.get("last_name");
                    billing = number;
                } catch (IllegalArgumentException unrepresentable) {
                    // A name no file system can carry is never altered: Unassigned.
                }
            }
        }
        String lexical = xml.dateOfBirth() == null ? null : xml.dateOfBirth().strip();
        if (lexical == null
                || !lexical.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:Z|[+-][0-9]{2}:[0-9]{2})?"))
            throw review();
        LocalDate birth;
        try {
            birth = LocalDate.parse(lexical.substring(0, 10));
        } catch (java.time.DateTimeException invalid) {
            throw review();
        }
        var result =
                new CdsExportPatientFileDto(
                        physicianFirst,
                        physicianLast,
                        billing,
                        xml.legalFirstName(),
                        xml.legalLastName(),
                        xml.senderPatientId(),
                        birth);
        String relative;
        try {
            var path = new CdsExportPathComposer().compose(result);
            relative = path.physicianFolder() + "/" + path.patientXmlFile();
        } catch (IllegalArgumentException unrepresentable) {
            throw review();
        }
        new CdsExportXmlValidator().verifyFileBinding(xml, result, relative);
        checkpoint.run();
        return result;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PATIENT_FILE_BINDING_INVALID");
    }

    private static CdsExportReviewException review() {
        return new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
