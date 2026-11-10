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

import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicDetailsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Retains all original history, folds proven native snapshots and appends distinct native
 * events. An NR flag supplies no invented termination. Conflicting corrections, unresolved
 * overlaps and stale current state require review; no proposed change can escape a blocker.
 */
public final class CdsExportRetainedEnrolmentReconciler {
    private record Fact(
            String status, LocalDate start, LocalDate end, String reason, String physician) {
        LocalDate effective() {
            return end == null ? start : end;
        }
    }

    private record Episode(LocalDate start, String physician) {}

    public record Result(
            String sourceManifestSha256,
            String unitAddress,
            Set<String> sourceFields,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<CdsExportNativeFieldKeyDto> deferredNativeFields,
            Set<String> blockers,
            List<CdsExportRetainedSectionWriter.Change> proposedChanges,
            int retainedHistories,
            int additionalHistories) {
        public Result {
            sourceFields = Set.copyOf(sourceFields);
            fields = Map.copyOf(fields);
            deferredNativeFields = Set.copyOf(deferredNativeFields);
            blockers = Set.copyOf(blockers);
            proposedChanges = List.copyOf(proposedChanges);
        }

        public boolean representable() {
            return blockers.isEmpty()
                    && fields.values().stream()
                            .noneMatch(value -> value.outcome() == Outcome.REQUIRES_REVIEW);
        }

        public List<CdsExportRetainedSectionWriter.Change> changes() throws IOException {
            if (!representable())
                throw new IOException("CDS_EXPORT_ENROLMENT_RECONCILIATION_REQUIRES_REVIEW");
            return proposedChanges;
        }

        @Override
        public String toString() {
            return "CdsExportRetainedEnrolmentReconciler.Result[protected]";
        }
    }

    public Result reconcile(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> allFields,
            CdsExportCapturedTextRowDto current,
            CdsExportNativeRowReceiptDto currentWitness,
            List<CdsExportCapturedTextRowDto> archives,
            List<CdsExportNativeRowReceiptDto> archiveWitnesses,
            CdsExportPublicProviderLookupsDto lookups,
            int maximumRows,
            long maximumBytes,
            CdsExportRetainedSectionWriter.Values values,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(values);
        checkpoint.run();
        values.requireActive();
        lookups.requireGeneration(index.sourceManifestSha256());
        var providers = lookups.providers();
        if (maximumRows < 1
                || maximumRows > 8192
                || maximumBytes < 1
                || maximumBytes > 64L << 20
                || archives.size() + 1 > maximumRows
                || archives.size() != archiveWitnesses.size()
                || providers.size() > 65536) scope();
        var evidence =
                new CdsExportDemographicProjectionEvidence()
                        .verify(
                                patient,
                                address,
                                index,
                                checked,
                                current,
                                currentWitness,
                                checkpoint);
        var parent =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.DEMOGRAPHICS,
                                address,
                                index,
                                checked,
                                allFields,
                                checkpoint);
        var options =
                new CdsExportRetainedImportOptionsReader()
                        .fromCaptured(index, address.substring(0, 36));
        var source =
                new CdsExportRetainedEnrolmentSource()
                        .read(
                                parent,
                                values,
                                options,
                                evidence.preparation().details(),
                                checkpoint);
        var projection = evidence.groups().get(CdsExportDemographicProjectionEvidence.Group.ROSTER);
        var roster = evidence.preparation().details().roster();
        var decisions =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var deferred = new HashSet<CdsExportNativeFieldKeyDto>();
        var blockers = new LinkedHashSet<String>();
        if (!projection.preparedProjectionProven())
            blockers.add("PREPARED_ENROLMENT_PROJECTION_NOT_PROVEN");
        long used = 0;
        var uniqueOhip = new HashMap<String, String>();
        for (var entry : providers.entrySet()) {
            checkpoint.run();
            var provider = entry.getValue();
            if (!provider.table().equals("provider")
                    || !provider.fields().keySet().equals(CdsExportProviderRowDto.COLUMNS)
                    || !entry.getKey().equals(provider.fields().get("provider_no"))
                    || !CdsExportPublicProviderSourceReader.key(entry.getKey())
                            .equals(provider.recordKey())) scope();
            used = Math.addExact(used, weight(provider));
            String ohip = provider.fields().get("ohip_no");
            if (ohip != null && !ohip.isEmpty() && ohip.codePointCount(0, ohip.length()) <= 6)
                uniqueOhip.merge(ohip, entry.getKey(), (a, b) -> a.equals(b) ? a : "AMBIGUOUS");
        }
        var sourceIdentities = new HashMap<Integer, String>();
        var facts = new LinkedHashSet<Fact>();
        var selected =
                source.histories().stream()
                        .filter(history -> history.ordinal() == source.selectedOrdinal())
                        .findFirst()
                        .orElse(null);
        if (selected != null
                && roster != null
                && roster.enrolledProvider() != null
                && physician(selected).isEmpty())
            blockers.add("SOURCE_ENROLLED_PHYSICIAN_NOT_PROVEN");
        for (var history : source.histories()) {
            checkpoint.run();
            String identity = sourceIdentity(history, selected, roster, uniqueOhip);
            sourceIdentities.put(history.ordinal(), identity);
            if (!valid(history)) blockers.add("RETAINED_ENROLMENT_STATE_OR_CODE_REQUIRES_REVIEW");
            facts.add(
                    new Fact(
                            history.status(),
                            history.start(),
                            history.end(),
                            history.reason(),
                            identity));
        }
        var witnesses = new HashMap<String, CdsExportNativeRowReceiptDto>();
        for (var witness : archiveWitnesses)
            if (!"demographicArchive".equals(witness.table())
                    || witnesses.putIfAbsent(witness.recordKey(), witness) != null) scope();
        var appended = new LinkedHashMap<Fact, CdsExportEnrolmentEventDto>();
        String previous = null;
        Fact currentFact = null;
        boolean currentAbsent = false;
        var nativeRows = new ArrayList<CdsExportCapturedTextRowDto>(archives);
        nativeRows.add(current);
        for (var row : nativeRows) {
            checkpoint.run();
            boolean isCurrent = row.table().equals("demographic");
            CdsExportEnrolmentMapper.requireRow(patient, row);
            if (!isCurrent) {
                if (previous != null && row.recordKey().compareTo(previous) <= 0) scope();
                previous = row.recordKey();
                var witness = witnesses.remove(row.recordKey());
                CdsExportCapturedTextWitness.require(row, witness, checkpoint);
                merge(
                        decisions,
                        CdsExportDemographicAncillaryReconciler.assessArchive(
                                patient, row, checkpoint));
            } else if (row != current) scope();
            used = Math.addExact(used, weight(row));
            if (used > maximumBytes)
                throw new IOException("CDS_EXPORT_ENROLMENT_RECONCILIATION_RESOURCE_LIMIT");
            Fact fact = null;
            if (projection.preparedProjectionProven() && sameProjection(row, roster)) {
                if (selected != null)
                    fact =
                            new Fact(
                                    selected.status(),
                                    selected.start(),
                                    selected.end(),
                                    selected.reason(),
                                    sourceIdentities.get(selected.ordinal()));
                for (String column : CdsExportEnrolmentMapper.COLUMNS.keySet())
                    set(
                            row,
                            decisions,
                            column,
                            present(row.fields().get(column)) ? Outcome.MAPPED : Outcome.ABSENT,
                            "VERIFIED_RETAINED_ENROLMENT_PROJECTION");
            } else {
                var mapped =
                        new CdsExportEnrolmentMapper()
                                .map(
                                        patient,
                                        row,
                                        lookups.find(row.fields().get("roster_enrolled_to")));
                merge(decisions, mapped.fields());
                if (!mapped.representable())
                    blockers.add("NATIVE_ENROLMENT_STATE_OR_PROVIDER_REQUIRES_REVIEW");
                else if (mapped.event() != null) {
                    var event = mapped.event();
                    fact =
                            new Fact(
                                    event.status(),
                                    event.start(),
                                    event.end(),
                                    event.terminationReason(),
                                    nativeIdentity(mapped.nativeProvider()));
                    if (!facts.contains(fact)) appended.putIfAbsent(fact, event);
                }
            }
            if (fact != null) facts.add(fact);
            if (isCurrent) {
                currentFact = fact;
                currentAbsent = fact == null;
            }
            if (!isCurrent)
                for (String column : row.fields().keySet()) {
                    var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
                    if (!decisions.containsKey(key)) deferred.add(key);
                }
        }
        if (!witnesses.isEmpty()) scope();
        if (blockers.isEmpty())
            validate(
                    facts,
                    currentFact,
                    currentAbsent,
                    current.fields().get("roster_status"),
                    blockers,
                    checkpoint);
        if (!projection.preparedProjectionProven())
            for (String column : CdsExportEnrolmentMapper.COLUMNS.keySet())
                set(
                        current,
                        decisions,
                        column,
                        Outcome.REQUIRES_REVIEW,
                        "PREPARED_ENROLMENT_PROJECTION_NOT_PROVEN");
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        if (blockers.isEmpty()) {
            changes.addAll(source.changes());
            var additional =
                    appended.entrySet().stream()
                            .sorted(
                                    Comparator
                                            .<Map.Entry<Fact, CdsExportEnrolmentEventDto>,
                                                    LocalDate>
                                                    comparing(entry -> entry.getKey().effective())
                                            .thenComparing(entry -> entry.getKey().start())
                                            .thenComparing(entry -> entry.getKey().physician())
                                            .thenComparing(entry -> entry.getKey().status()))
                            .toList();
            if (source.histories().isEmpty() && !additional.isEmpty())
                changes.add(
                        new CdsExportRetainedSectionWriter.Change(
                                "Enrolment[1]",
                                Set.of(),
                                xml -> {
                                    for (var entry : additional) {
                                        checkpoint.run();
                                        xml.start("cds", "EnrolmentHistory");
                                        new CdsExportEnrolmentXmlWriter()
                                                .writeHistoryContent(entry.getValue(), xml);
                                        xml.end();
                                    }
                                }));
            else {
                int ordinal = source.histories().size();
                for (var entry : additional) {
                    var event = entry.getValue();
                    changes.add(
                            new CdsExportRetainedSectionWriter.Change(
                                    "Enrolment[1]/EnrolmentHistory[" + (++ordinal) + "]",
                                    Set.of(),
                                    xml ->
                                            new CdsExportEnrolmentXmlWriter()
                                                    .writeHistoryContent(event, xml)));
                }
            }
        }
        values.requireActive();
        checkpoint.run();
        return new Result(
                index.sourceManifestSha256(),
                address,
                source.sourceFields(),
                decisions,
                deferred,
                blockers,
                changes,
                source.histories().size(),
                appended.size());
    }

    private static boolean valid(CdsExportRetainedEnrolmentSource.History value) {
        if ("1".equals(value.status()) && value.reason() != null
                || value.reason() != null
                        && !CdsExportEnrolmentEventDto.TERMINATION_REASONS.contains(value.reason()))
            return false;
        for (var entry : physician(value).entrySet()) {
            int max = entry.getKey().endsWith("OHIPPhysicianId") ? 6 : 50;
            if (entry.getValue().codePointCount(0, entry.getValue().length()) > max) return false;
        }
        return true;
    }

    private static void validate(
            Set<Fact> facts,
            Fact current,
            boolean currentAbsent,
            String currentState,
            Set<String> blockers,
            Runnable checkpoint) {
        var ended = new HashMap<Episode, Fact>();
        var episodes = new HashSet<Episode>();
        for (var fact : facts) {
            checkpoint.run();
            var episode = new Episode(fact.start(), fact.physician());
            episodes.add(episode);
            if ("0".equals(fact.status())) {
                var previous = ended.putIfAbsent(episode, fact);
                if (previous != null && !previous.equals(fact))
                    blockers.add("CONFLICTING_ENROLMENT_TERMINATION_REQUIRES_REVIEW");
            }
        }
        var ordered =
                episodes.stream()
                        .sorted(
                                Comparator.comparing(Episode::start)
                                        .thenComparing(Episode::physician))
                        .toList();
        for (int n = 1; n < ordered.size(); n++) {
            checkpoint.run();
            var previous = ordered.get(n - 1);
            var next = ordered.get(n);
            var termination = ended.get(previous);
            if (termination == null || !next.start().isAfter(termination.end()))
                blockers.add("OVERLAPPING_OR_UNCLOSED_ENROLMENT_REQUIRES_REVIEW");
        }
        if (currentAbsent && !facts.isEmpty()) {
            if (!"NR".equals(currentState) || !ended.keySet().containsAll(episodes))
                blockers.add("CLEARED_CURRENT_ENROLMENT_REQUIRES_REVIEW");
        } else if (current != null)
            for (var fact : facts)
                if (fact.effective().isAfter(current.effective())
                        || fact.effective().equals(current.effective()) && !fact.equals(current))
                    blockers.add("STALE_OR_AMBIGUOUS_CURRENT_ENROLMENT_REQUIRES_REVIEW");
    }

    private static String sourceIdentity(
            CdsExportRetainedEnrolmentSource.History value,
            CdsExportRetainedEnrolmentSource.History selected,
            CdsDemographicDetailsDto.Enrollment roster,
            Map<String, String> ohip) {
        var identity = physician(value);
        if (identity.isEmpty()) return "ABSENT";
        if (selected != null
                && roster != null
                && roster.enrolledProvider() != null
                && identity.equals(physician(selected)))
            return nativeIdentity(roster.enrolledProvider());
        String code = identity.get("EnrolledToPhysician/OHIPPhysicianId"),
                provider = code == null ? null : ohip.get(code);
        if (provider != null && !"AMBIGUOUS".equals(provider)) return nativeIdentity(provider);
        return "SOURCE:"
                + CdsImportKeys.hash(
                        optionalHash(identity.get("EnrolledToPhysician/Name/FirstName")),
                        optionalHash(identity.get("EnrolledToPhysician/Name/LastName")),
                        optionalHash(code));
    }

    private static Map<String, String> physician(CdsExportRetainedEnrolmentSource.History history) {
        var values = new TreeMap<String, String>();
        history.values()
                .forEach(
                        (name, value) -> {
                            if (name.startsWith("EnrolledToPhysician/")) values.put(name, value);
                        });
        return values;
    }

    private static String optionalHash(String value) {
        return value == null ? "NULL" : CdsImportKeys.hash("TEXT", value);
    }

    private static String nativeIdentity(String provider) {
        return provider == null || provider.isEmpty() ? "ABSENT" : "NATIVE:" + provider;
    }

    private static boolean sameProjection(
            CdsExportCapturedTextRowDto row, CdsDemographicDetailsDto.Enrollment roster) {
        var fields = row.fields();
        return Objects.equals(fields.get("roster_status"), roster == null ? "" : roster.status())
                && Objects.equals(fields.get("roster_date"), roster == null ? null : roster.date())
                && Objects.equals(
                        fields.get("roster_termination_date"),
                        roster == null ? null : roster.terminationDate())
                && Objects.equals(
                        fields.get("roster_termination_reason"),
                        roster == null ? null : roster.terminationReason())
                && Objects.equals(
                        fields.get("roster_enrolled_to"),
                        roster == null ? null : roster.enrolledProvider());
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty();
    }

    private static long weight(CdsExportCapturedTextRowDto row) {
        long bytes = 512;
        for (var entry : row.fields().entrySet())
            bytes =
                    Math.addExact(
                            bytes,
                            64L
                                    + entry.getKey().length() * 2L
                                    + (entry.getValue() == null
                                            ? 0
                                            : entry.getValue()
                                                    .getBytes(StandardCharsets.UTF_8)
                                                    .length));
        return bytes;
    }

    private static void merge(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> target,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> source)
            throws IOException {
        for (var entry : source.entrySet()) {
            var old = target.putIfAbsent(entry.getKey(), entry.getValue());
            if (old != null && !old.equals(entry.getValue())) scope();
        }
    }

    private static void set(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String column,
            Outcome outcome,
            String rule) {
        String part = CdsExportEnrolmentMapper.COLUMNS.get(column);
        fields.put(
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                new CdsExportFieldAssessmentDto(
                        CdsExportEnrolmentMapper.presence(row.fields().get(column)),
                        outcome,
                        part == null ? "" : "Enrolment/EnrolmentHistory/" + part,
                        rule));
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_ENROLMENT_RECONCILIATION_SCOPE_INVALID");
    }
}
