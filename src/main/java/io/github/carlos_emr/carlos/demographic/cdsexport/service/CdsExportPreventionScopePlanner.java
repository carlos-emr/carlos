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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Complete bounded partition of patient prevention metadata using exact frozen classification.
 * No clinical values, program grant, omitted row, duplicate or unknown type is inferred. A
 * review partition cannot authorize clinical capture or complete export. The source adapter
 * must check program restrictions before loading type metadata; this planner is an additional
 * independent check of the subsequently authenticated frozen metadata, not a disclosure gate.
 */
public final class CdsExportPreventionScopePlanner {
    static final Set<String> COLUMNS =
            Set.of(
                    "id",
                    "demographic_no",
                    "prevention_type",
                    "snomedId",
                    "restrictToProgram",
                    "programNo");

    public enum Review {
        PROGRAM_SCOPE,
        UNKNOWN_TYPE,
        CATEGORY_NOT_PROVEN,
        UNSUPPORTED_LAYOUT,
        CONFLICTING_CATEGORY,
        UNRESOLVED_CVC_MAPPING,
        UNRESOLVED_NON_IMMUNIZATION_CODE
    }

    public record Result(
            int patient,
            String sourceManifestSha256,
            String configurationSha256,
            String catalogueSha256,
            String scopeSha256,
            Set<Integer> immunizations,
            Set<Integer> otherPreventions,
            Map<Integer, Review> reviews,
            long rows) {
        public Result {
            immunizations = Set.copyOf(immunizations);
            otherPreventions = Set.copyOf(otherPreventions);
            reviews = Map.copyOf(reviews);
            for (String digest :
                    List.of(
                            sourceManifestSha256,
                            configurationSha256,
                            catalogueSha256,
                            scopeSha256))
                if (!digest.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException(
                            "CDS_EXPORT_PREVENTION_SCOPE_RESULT_INVALID");
            if (patient < 1
                    || rows < 0
                    || rows > 10_000
                    || rows != immunizations.size() + otherPreventions.size() + reviews.size()
                    || !Collections.disjoint(immunizations, otherPreventions)
                    || !Collections.disjoint(immunizations, reviews.keySet())
                    || !Collections.disjoint(otherPreventions, reviews.keySet()))
                throw new IllegalArgumentException("CDS_EXPORT_PREVENTION_SCOPE_RESULT_INVALID");
        }

        public boolean representable() {
            return reviews.isEmpty();
        }

        @Override
        public String toString() {
            return "CdsExportPreventionScopePlanner.Result[protected]";
        }
    }

    record Selection(
            Set<Integer> immunizations,
            Set<Integer> otherPreventions,
            Map<Integer, Review> reviews,
            String sha256,
            long rows) {
        Selection {
            immunizations = Set.copyOf(immunizations);
            otherPreventions = Set.copyOf(otherPreventions);
            reviews = Map.copyOf(reviews);
        }

        boolean representable() {
            return reviews.isEmpty();
        }

        @Override
        public String toString() {
            return "CdsExportPreventionScopePlanner.Selection[protected]";
        }
    }

    public Result plan(
            int patient,
            CdsExportPreventionClassifier classifier,
            Iterable<CdsExportCapturedTextRowDto> metadata)
            throws IOException {
        if (classifier == null) invalid();
        String binding = classifier.sourceManifestSha256();
        var selected = partition(patient, classifier, metadata, binding);
        return new Result(
                patient,
                binding,
                classifier.configurationSha256(),
                classifier.catalogueSha256(),
                selected.sha256(),
                selected.immunizations(),
                selected.otherPreventions(),
                selected.reviews(),
                selected.rows());
    }

    Selection select(
            int patient,
            CdsExportPreventionClassifier classifier,
            Iterable<CdsExportCapturedTextRowDto> metadata)
            throws IOException {
        return partition(patient, classifier, metadata, null);
    }

    private Selection partition(
            int patient,
            CdsExportPreventionClassifier classifier,
            Iterable<CdsExportCapturedTextRowDto> metadata,
            String sourceBinding)
            throws IOException {
        if (patient < 1 || classifier == null || metadata == null) invalid();
        var selected = new HashSet<Integer>();
        var other = new HashSet<Integer>();
        var reviews = new HashMap<Integer, Review>();
        var digest = digest();
        long total = 0;
        String after = null;
        try (var output =
                new DataOutputStream(
                        new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            frame(output, "CDS_EXPORT_PREVENTION_SCOPE_V1");
            frame(output, Integer.toString(patient));
            frame(output, sourceBinding);
            frame(output, classifier.configurationSha256());
            frame(output, classifier.catalogueSha256());
            for (var row : metadata) {
                if (row == null
                        || ++total > 10_000
                        || !"preventions_scope".equals(row.table())
                        || !row.fields().keySet().equals(COLUMNS)
                        || !row.exactNumbers().isEmpty()
                        || after != null && row.recordKey().compareTo(after) <= 0) invalid();
                int id = integer(row.fields().get("id"));
                if (!CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())
                        || !Integer.toString(patient).equals(row.fields().get("demographic_no")))
                    invalid();
                String flag = row.fields().get("restrictToProgram"),
                        program = row.fields().get("programNo"),
                        type = row.fields().get("prevention_type"),
                        code = row.fields().get("snomedId");
                if (program != null) integer(program);
                for (String value : row.fields().values())
                    if (value != null && !CdsExportNativeFieldAssessments.legal(value, 255))
                        invalid();
                frame(output, row.recordKey());
                for (var entry : new TreeMap<>(row.fields()).entrySet()) {
                    frame(output, entry.getKey());
                    frame(output, entry.getValue());
                }
                // Historical NULL/no-program means no recorded restriction. Explicit false remains
                // false even with an affiliation. An active/unknown flag never supplies a grant.
                boolean unrestricted = "0".equals(flag) || flag == null && program == null;
                var decision = classifier.classify(type, code);
                if (!unrestricted) {
                    reviews.put(id, Review.PROGRAM_SCOPE);
                    frame(output, "REVIEW_PROGRAM_SCOPE");
                } else
                    switch (decision.kind()) {
                        case IMMUNIZATION -> {
                            selected.add(id);
                            frame(output, "IMMUNIZATION");
                        }
                        case NON_IMMUNIZATION -> {
                            other.add(id);
                            frame(output, "NON_IMMUNIZATION");
                        }
                        case REQUIRES_REVIEW -> {
                            reviews.put(id, review(decision.rule()));
                            frame(output, "REVIEW_" + decision.rule().name());
                        }
                        default -> throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
                    }
                after = row.recordKey();
            }
        }
        return new Selection(
                selected, other, reviews, HexFormat.of().formatHex(digest.digest()), total);
    }

    private static Review review(CdsExportPreventionClassifier.Rule rule) throws IOException {
        return switch (rule) {
            case UNKNOWN_TYPE -> Review.UNKNOWN_TYPE;
            case CATEGORY_NOT_PROVEN -> Review.CATEGORY_NOT_PROVEN;
            case UNSUPPORTED_LAYOUT -> Review.UNSUPPORTED_LAYOUT;
            case CONFLICTING_CATEGORY -> Review.CONFLICTING_CATEGORY;
            case UNRESOLVED_CVC_MAPPING -> Review.UNRESOLVED_CVC_MAPPING;
            case UNRESOLVED_NON_IMMUNIZATION_CODE -> Review.UNRESOLVED_NON_IMMUNIZATION_CODE;
            case CONFIGURED_IMMUNIZATION, CVC_IMMUNIZATION, CONFIGURED_NON_IMMUNIZATION ->
                    throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        };
    }

    private static int integer(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (!Integer.toString(value).equals(text)) invalid();
            return value;
        } catch (NumberFormatException failed) {
            throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        }
    }

    private static void frame(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
    }
}
