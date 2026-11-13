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
 * review partition cannot authorize clinical capture or complete export.
 *
 * <p>A program restriction is not a classification fact. The source readers read the chart's
 * restrictions first, authorize each program live and prove the same restrictions inside the
 * source snapshot before they load type metadata ({@link CdsExportPreventionAccessService}), and
 * the mappers check each exported row's program again, live. This planner therefore classifies a
 * row restricted to a program like any other; only a malformed restriction flag (anything but
 * {@code 0}, {@code 1} or NULL) is a {@link Review#PROGRAM_SCOPE} review, since the chart's rule
 * cannot be applied to it ({@link ProgramRestriction}). It is an additional independent check of
 * the subsequently authenticated frozen metadata, not a disclosure gate.
 *
 * <p>The one exception to classification is origin proof (review finding F10): a row the
 * classification cannot place, which an authenticated CARLOS import receipt proves was written
 * from a CDS Immunizations element ({@link CdsExportImmunizationImportReceipts}), is an
 * immunization; it then travels through the retained-import path, whose field reconciler still
 * holds any native change it cannot represent. A malformed restriction is never overridden, and
 * a row the classification places is never moved.
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

    /**
     * Origin proof for rows the classification cannot place. It receives the candidate row ids
     * (rows classified for review whose program restriction, if any, is well formed) once, and
     * returns the ids an authenticated import receipt proves are imported immunizations; a
     * returned id outside the candidates is refused.
     */
    @FunctionalInterface
    interface ImportedImmunizations {
        ImportedImmunizations NONE = candidates -> Set.of();

        Set<Integer> proven(Set<Integer> candidates) throws IOException;
    }

    /**
     * One prevention row's program restriction under the rule the chart applies to a document
     * restricted to a program: a row is restricted when its flag is set (or a legacy NULL) and it
     * names a program other than none or {@code -1}. A flag set without a program, and an
     * explicit {@code 0} flag whatever the program, are unrestricted. A flag other than
     * {@code 0}, {@code 1} or NULL, or a program that is not an integer, is malformed and stays a
     * named review, never silently unrestricted.
     *
     * @param malformed whether the chart's rule cannot be applied to the row
     * @param program the program the row is restricted to, or null when it is not restricted
     */
    record ProgramRestriction(boolean malformed, String program) {
        static final ProgramRestriction NONE = new ProgramRestriction(false, null),
                MALFORMED = new ProgramRestriction(true, null);

        static ProgramRestriction of(String flag, String program) {
            if (flag != null && !flag.equals("0") && !flag.equals("1")) return MALFORMED;
            if ("0".equals(flag) || program == null || program.equals("-1")) return NONE;
            if (!program.matches("0|-?[1-9][0-9]{0,9}")
                    || Long.parseLong(program) < Integer.MIN_VALUE
                    || Long.parseLong(program) > Integer.MAX_VALUE) return MALFORMED;
            return new ProgramRestriction(false, program);
        }

        boolean restricted() {
            return program != null;
        }
    }

    public enum Review {
        /** A malformed program restriction flag: the chart's program rule cannot be applied. */
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
        return plan(patient, classifier, metadata, ImportedImmunizations.NONE);
    }

    Result plan(
            int patient,
            CdsExportPreventionClassifier classifier,
            Iterable<CdsExportCapturedTextRowDto> metadata,
            ImportedImmunizations imported)
            throws IOException {
        if (classifier == null) invalid();
        String binding = classifier.sourceManifestSha256();
        var selected = partition(patient, classifier, metadata, binding, imported);
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
        return select(patient, classifier, metadata, ImportedImmunizations.NONE);
    }

    Selection select(
            int patient,
            CdsExportPreventionClassifier classifier,
            Iterable<CdsExportCapturedTextRowDto> metadata,
            ImportedImmunizations imported)
            throws IOException {
        return partition(patient, classifier, metadata, null, imported);
    }

    private record Classified(
            int id,
            String recordKey,
            Map<String, String> fields,
            boolean malformedProgramScope,
            CdsExportPreventionClassifier.Decision decision) {
        @Override
        public String toString() {
            return "CdsExportPreventionScopePlanner.Classified[protected]";
        }
    }

    private Selection partition(
            int patient,
            CdsExportPreventionClassifier classifier,
            Iterable<CdsExportCapturedTextRowDto> metadata,
            String sourceBinding,
            ImportedImmunizations imported)
            throws IOException {
        if (patient < 1 || classifier == null || metadata == null || imported == null) invalid();
        // Every row is validated and classified before any receipt is consulted, so a corrupt or
        // foreign later row is refused even when an earlier row would have needed a receipt.
        var rows = new ArrayList<Classified>();
        var candidates = new TreeSet<Integer>();
        long total = 0;
        String after = null;
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
            // A well-formed restriction was authorized before this metadata could be read; a
            // malformed flag cannot be, and never supplies a grant.
            boolean malformed = ProgramRestriction.of(flag, program).malformed();
            var decision = classifier.classify(type, code);
            if (!malformed && decision.kind() == CdsExportPreventionClassifier.Kind.REQUIRES_REVIEW)
                candidates.add(id);
            rows.add(
                    new Classified(
                            id, row.recordKey(), new TreeMap<>(row.fields()), malformed, decision));
            after = row.recordKey();
        }
        var proven =
                candidates.isEmpty()
                        ? Set.<Integer>of()
                        : Set.copyOf(imported.proven(Set.copyOf(candidates)));
        if (!candidates.containsAll(proven)) invalid();
        var selected = new HashSet<Integer>();
        var other = new HashSet<Integer>();
        var reviews = new HashMap<Integer, Review>();
        var digest = digest();
        try (var output =
                new DataOutputStream(
                        new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            frame(output, "CDS_EXPORT_PREVENTION_SCOPE_V1");
            frame(output, Integer.toString(patient));
            frame(output, sourceBinding);
            frame(output, classifier.configurationSha256());
            frame(output, classifier.catalogueSha256());
            for (var row : rows) {
                int id = row.id();
                frame(output, row.recordKey());
                for (var entry : row.fields().entrySet()) {
                    frame(output, entry.getKey());
                    frame(output, entry.getValue());
                }
                var decision = row.decision();
                if (row.malformedProgramScope()) {
                    reviews.put(id, Review.PROGRAM_SCOPE);
                    frame(output, "REVIEW_PROGRAM_SCOPE");
                } else if (proven.contains(id)) {
                    // Receipt-proven imported vaccine the classification could not place.
                    selected.add(id);
                    frame(output, "IMMUNIZATION_IMPORT_RECEIPT_" + decision.rule().name());
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
            case CONFIGURED_IMMUNIZATION,
                    CATCH_ALL_INJECTION,
                    RH_IMMUNE_GLOBULIN,
                    CVC_IMMUNIZATION,
                    CONFIGURED_NON_IMMUNIZATION ->
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
