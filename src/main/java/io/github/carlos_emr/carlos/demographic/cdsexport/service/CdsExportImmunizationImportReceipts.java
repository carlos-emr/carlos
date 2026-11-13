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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeBaselineCodec;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Proof, from the CARLOS importer's own receipt, that a native prevention row was written from a
 * CDS Immunizations element (review finding F10).
 *
 * <p>The importer stores an imported vaccine whose ImmunizationType is absent or not configured
 * as the catch-all type {@code OtherA}, which the frozen prevention classification cannot prove
 * to be a vaccine. Such a row is still provably an immunization when an applied Immunizations
 * import unit names it in its target receipt and that receipt carries a native-baseline witness
 * sealed under the installation key: the seal binds the receipt digest, so a database writer can
 * neither invent the unit's references nor move a witness to another receipt, and only the
 * immunization persister ever writes a {@code preventions} row. The preventions witness must also
 * record this row's identifier and this patient. Provenance extensions ({@code cdsName},
 * {@code cdsDatePrecision}, ...) are ordinary editable chart rows and are never consulted.
 *
 * <p>Capture-time selection (live rows in the capture's repeatable-read snapshot) and the
 * finished-capture reclassification (the captured {@code cds_import_unit} rows) apply this same
 * function to the same row values, so both reach the same partition. A unit that cannot prove a
 * row (legacy unit without a witness, a refused, expected or foreign unit, a malformed, tampered
 * or foreign witness) simply proves nothing: the row keeps its classification review. A missing
 * installation key is a refusal ({@code CDS_EXPORT_NATIVE_BASELINE_KEY_UNAVAILABLE}), as in the
 * retained index, never a silently dropped witness. Proof is origin only: whether the row changed
 * since import remains the immunization field reconciler's decision.
 */
final class CdsExportImmunizationImportReceipts {
    static final String TABLE = "cds_import_unit";
    static final Set<String> COLUMNS =
            Set.copyOf(CdsExportPatientTableReadSupport.Plan.IMPORT_UNITS.columns());

    /** Receipt JSON bound; the importer refuses a larger receipt and the codec re-checks it. */
    static final int MAX_REFERENCE_BYTES = 65_536;

    /** Upper bound of one captured unit row, matching the retained index's per-row bound. */
    static final int MAX_UNIT_ROW_BYTES =
            (int) (2_097_152L + CdsNativeBaselineCodec.MAX_SEALED_BYTES);

    private static final Set<String> APPLIED = Set.of("COMMITTED", "ALREADY_COMPLETE");
    private static final ObjectMapper JSON = new ObjectMapper();

    private CdsExportImmunizationImportReceipts() {}

    /** One patient's accumulated proofs over a complete pass of its import units. */
    static final class Proof {
        private final int patient;
        private final Set<Integer> candidates;
        private final CdsNativeBaselineKeys keys;
        private final Set<Integer> proven = new TreeSet<>();

        Proof(int patient, Set<Integer> candidates, CdsNativeBaselineKeys keys) {
            if (patient < 1) throw new IllegalArgumentException("CDS_EXPORT_IMPORT_RECEIPT_SCOPE");
            this.patient = patient;
            this.candidates = Set.copyOf(candidates);
            this.keys = Objects.requireNonNull(keys);
        }

        /**
         * The candidate row a unit's receipt names, or {@code null} when the unit cannot prove one.
         * Reads no sealed witness, so a capture reads a large witness only for a real candidate.
         */
        Integer named(
                String category,
                String state,
                String references,
                String targetDigest,
                boolean witnessed) {
            return CdsExportImmunizationImportReceipts.named(
                    candidates, category, state, references, targetDigest, witnessed);
        }

        /** Authenticates the sealed witness of a named candidate and records the proof. */
        void witness(int id, String references, String targetDigest, String sealed)
                throws IOException {
            if (!candidates.contains(id))
                throw new IllegalArgumentException("CDS_EXPORT_IMPORT_RECEIPT_SCOPE");
            if (witnessed(patient, id, references, targetDigest, sealed, keys)) proven.add(id);
        }

        /** One complete {@code cds_import_unit} row, as captured. */
        void accept(Map<String, String> unit) throws IOException {
            Integer id =
                    named(
                            unit.get("category"),
                            unit.get("state"),
                            unit.get("target_references"),
                            unit.get("target_digest"),
                            unit.get("native_baseline") != null);
            if (id != null)
                witness(
                        id,
                        unit.get("target_references"),
                        unit.get("target_digest"),
                        unit.get("native_baseline"));
        }

        Set<Integer> proven() {
            return Set.copyOf(proven);
        }

        @Override
        public String toString() {
            return "CdsExportImmunizationImportReceipts.Proof[protected]";
        }
    }

    static Integer named(
            Set<Integer> candidates,
            String category,
            String state,
            String references,
            String targetDigest,
            boolean witnessed) {
        if (!witnessed
                || !CdsCategory.IMMUNIZATIONS.wireName().equals(category)
                || !APPLIED.contains(state)
                || references == null
                || targetDigest == null
                || references.getBytes(StandardCharsets.UTF_8).length > MAX_REFERENCE_BYTES
                || !targetDigest.equals(sha256(references))) return null;
        JsonNode root;
        try {
            root = JSON.readTree(references);
        } catch (IOException malformed) {
            return null;
        }
        if (root == null || !root.path("refs").isArray()) return null;
        // The occurrence service requires exactly one preventions reference per unit as well.
        String named = null;
        int parents = 0;
        for (var reference : root.path("refs"))
            if ("preventions".equals(reference.path("table").textValue())) {
                parents++;
                named = reference.path("id").textValue();
            }
        if (parents != 1 || named == null || !named.matches("-?(0|[1-9][0-9]{0,9})")) return null;
        int id;
        try {
            id = Integer.parseInt(named);
        } catch (NumberFormatException overflow) {
            return null;
        }
        return candidates.contains(id) ? id : null;
    }

    static boolean witnessed(
            int patient,
            int id,
            String references,
            String targetDigest,
            String sealed,
            CdsNativeBaselineKeys keys)
            throws IOException {
        if (sealed == null
                || sealed.getBytes(StandardCharsets.UTF_8).length
                        > CdsNativeBaselineCodec.MAX_SEALED_BYTES) return false;
        CdsNativeBaselineCodec.Document document;
        try {
            document = CdsNativeBaselineCodec.unseal(sealed, references, targetDigest, keys);
        } catch (IllegalArgumentException tamperedOrMalformed) {
            return false;
        } catch (IOException failed) {
            if ("CDS_NATIVE_BASELINE_KEY_UNAVAILABLE".equals(failed.getMessage()))
                throw new IOException("CDS_EXPORT_NATIVE_BASELINE_KEY_UNAVAILABLE");
            return false;
        }
        CdsNativeRowState parent = null;
        for (var row : document.rows())
            if (row.table().equals("preventions")) {
                if (parent != null) return false;
                parent = row;
            }
        String key = Integer.toString(id);
        if (parent == null || !parent.id().equals(key)) return false;
        try {
            return CdsExportImmunizationProjectionEvidence.matches(parent, "id", key)
                    && CdsExportImmunizationProjectionEvidence.matches(
                            parent, "demographic_no", Integer.toString(patient));
        } catch (IOException malformedWitness) {
            return false;
        }
    }

    private static String sha256(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
