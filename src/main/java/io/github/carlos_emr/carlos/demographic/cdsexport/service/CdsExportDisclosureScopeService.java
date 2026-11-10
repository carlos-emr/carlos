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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Live permission verification against encrypted, exact retained cohort and note scopes.
 * Retained access never revives an execution lease or issues native capture tickets. The
 * artifact facade must first authorize the requesting operator and its durable artifact
 * lease. A returned receipt attests this boundary only; it is never a saved permission grant.
 */
@Service
public class CdsExportDisclosureScopeService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final CdsExportStagingFactory stores;
    private final CdsExportCohortService cohorts;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportLedgerService ledger;

    public CdsExportDisclosureScopeService(
            CdsExportStagingFactory stores,
            CdsExportCohortService cohorts,
            CdsExportNativeCaptureService captures,
            CdsExportLedgerService ledger) {
        this.stores = Objects.requireNonNull(stores);
        this.cohorts = Objects.requireNonNull(cohorts);
        this.captures = Objects.requireNonNull(captures);
        this.ledger = Objects.requireNonNull(ledger);
    }

    public Verified verify(
            CdsExportLeaseDto lease,
            CdsExportClinicalPackageService.Prepared prepared,
            CdsExportRunControl control)
            throws IOException {
        if (!lease.runId().equals(prepared.runId()) || lease.epoch() != prepared.epoch()) invalid();
        var cohort = ledger.cohort(lease);
        if (!cohort.plaintextSha256().equals(prepared.cohortSha256())) invalid();
        return verifyRetained(
                lease.runId(),
                lease.epoch(),
                lease.document(),
                cohort,
                prepared.authorization(),
                prepared.patients(),
                control);
    }

    /** Caller supplies only a currently authorized durable publication/download context, never request-supplied file descriptors. */
    public Verified verifyRetained(
            String runId,
            long epoch,
            CdsExportJobDocumentDto intent,
            CdsExportStagedFileDto cohort,
            CdsExportStagedFileDto authorization,
            long patients,
            CdsExportRunControl control)
            throws IOException {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(control);
        if (patients < 1
                || patients > 2_000_000
                || !"COHORT_MANIFEST".equals(cohort.purpose())
                || !"DISCLOSURE_MANIFEST".equals(authorization.purpose())) invalid();
        control.checkpoint();
        var tables = captures.requiredTables(intent.command());
        var store = stores.openExisting(runId, epoch, intent.storage(), control::checkpoint);
        var expectedHeader =
                Map.of(
                        "kind",
                        "HEADER",
                        "protocol",
                        1,
                        "runId",
                        runId,
                        "epoch",
                        epoch,
                        "cohortSha256",
                        cohort.plaintextSha256(),
                        "compatibilityDigest",
                        intent.compatibilityDigest(),
                        "patients",
                        patients,
                        "tables",
                        new TreeSet<>(tables));
        long[] notes = {0};
        try (var input = store.open(authorization)) {
            var lines = new CdsExportJsonLines(input, 1 << 20, control::checkpoint);
            if (!JSON.readTree(JSON.writeValueAsBytes(expectedHeader)).equals(lines.next()))
                invalid();
            long count =
                    cohorts.readRetained(
                            store,
                            runId,
                            epoch,
                            intent,
                            cohort,
                            patients,
                            control::checkpoint,
                            patient -> {
                                control.checkpoint();
                                var node = lines.required();
                                if (!node.isObject()
                                        || node.size() != 4
                                        || !node.path("kind").isTextual()
                                        || !"PATIENT".equals(node.path("kind").textValue())
                                        || !node.path("patient").isIntegralNumber()
                                        || !node.path("patient").canConvertToInt()
                                        || node.path("patient").intValue()
                                                != patient.demographicNo()
                                        || !node.path("sourceManifestSha256").isTextual()
                                        || !node.path("sourceManifestSha256")
                                                .textValue()
                                                .matches("[0-9a-f]{64}")
                                        || !node.path("notes").isArray()
                                        || node.path("notes").size() > 8192) invalid();
                                captures.authorizeScope(
                                        intent.command(), patient.demographicNo(), tables, control);
                                long previous = 0;
                                var patientNotes =
                                        new java.util.ArrayList<CdsExportNoteAccessDto>();
                                for (var value : node.path("notes")) {
                                    control.checkpoint();
                                    var scope = note(value, patient.demographicNo());
                                    if (scope.noteId() <= previous
                                            || !intent.command()
                                                    .categories()
                                                    .contains(CdsCategory.CLINICAL_NOTES))
                                        invalid();
                                    patientNotes.add(scope);
                                    previous = scope.noteId();
                                    notes[0]++;
                                }
                                if (!patientNotes.isEmpty())
                                    control.authorizeNotes(
                                            CdsCategory.CLINICAL_NOTES,
                                            java.util.List.copyOf(patientNotes));
                            });
            if (count != patients
                    || !JSON.readTree(
                                    JSON.writeValueAsBytes(
                                            Map.of("kind", "END", "patients", patients)))
                            .equals(lines.next())
                    || lines.next() != null) invalid();
        }
        control.checkpoint();
        return new Verified(
                runId,
                epoch,
                cohort.plaintextSha256(),
                authorization.plaintextSha256(),
                patients,
                notes[0],
                Instant.now());
    }

    private static CdsExportNoteAccessDto note(JsonNode node, int patient) throws IOException {
        if (!node.isObject()
                || node.size() != 4
                || !node.path("noteId").isIntegralNumber()
                || !node.path("noteId").canConvertToLong()
                || !node.path("patient").isIntegralNumber()
                || !node.path("patient").canConvertToInt()
                || node.path("patient").intValue() != patient
                || !node.path("program").isTextual()
                || !node.path("reporterRole").isTextual()) invalid();
        try {
            return new CdsExportNoteAccessDto(
                    node.path("noteId").longValue(),
                    patient,
                    node.path("program").textValue(),
                    node.path("reporterRole").textValue());
        } catch (IllegalArgumentException rejected) {
            throw new IOException("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
        }
    }

    /** Minted only after both complete authenticated EOFs and the last current-authority probe. */
    public static final class Verified {
        private final String runId, cohortSha256, authorizationSha256;
        private final long epoch, patients, notes;
        private final Instant verifiedAt;

        private Verified(
                String run,
                long epoch,
                String cohort,
                String authorization,
                long patients,
                long notes,
                Instant at) {
            runId = run;
            this.epoch = epoch;
            cohortSha256 = cohort;
            authorizationSha256 = authorization;
            this.patients = patients;
            this.notes = notes;
            verifiedAt = at;
        }

        public String runId() {
            return runId;
        }

        public long epoch() {
            return epoch;
        }

        public String cohortSha256() {
            return cohortSha256;
        }

        public String authorizationSha256() {
            return authorizationSha256;
        }

        public long patients() {
            return patients;
        }

        public long notes() {
            return notes;
        }

        public Instant verifiedAt() {
            return verifiedAt;
        }

        @Override
        public String toString() {
            return "CdsExportVerifiedDisclosureScope[protected]";
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_DISCLOSURE_SCOPE_INVALID");
    }
}
