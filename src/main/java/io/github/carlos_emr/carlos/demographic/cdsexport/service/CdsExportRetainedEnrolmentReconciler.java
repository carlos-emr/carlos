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
 * events. The source enrolment history exports as imported; native events recorded after it
 * follow the never-imported chart's rules ({@link CdsExportEnrolmentHistory}): only RO and TE
 * are enrolment events, other roster states are carried in the native demographic record, and
 * an episode the current state contradicts (re-rostering without a termination, a cleared or FS
 * current state, a conflicting termination, an event after the current state) is a superseded
 * snapshot in the approved audit arrangement rather than a hold. No termination is invented.
 * Only an unproven import projection, an invalid retained source history and an enrolled
 * provider whose public values no CDS name can hold still require review.
 */
public final class CdsExportRetainedEnrolmentReconciler {
    private record Fact(
            String status, LocalDate start, LocalDate end, String reason, String physician) {
        LocalDate effective() {
            return end == null ? start : end;
        }
    }

    private record Episode(LocalDate start, String physician) {}

    /**
     * One archive or current row: its fact, its native mapping (null when the row is the
     * import's own verified projection) and its enrolled provider's captured public row.
     */
    private record Entry(
            CdsExportCapturedTextRowDto row,
            Fact fact,
            CdsExportEnrolmentMapper.Result mapped,
            CdsExportCapturedTextRowDto provider) {}

    private static final String SUPERSEDED = "SUPERSEDED_ROSTER_SNAPSHOT_IN_APPROVED_AUDIT";
    private static final String SUPERSEDED_PROVIDER =
            "SUPERSEDED_ROSTER_PROVIDER_IN_APPROVED_AUDIT";

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
        var sourceFacts = Set.copyOf(facts);
        var entries = new ArrayList<Entry>();
        String previous = null;
        Fact currentFact = null;
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
            CdsExportEnrolmentMapper.Result mapped = null;
            CdsExportCapturedTextRowDto provider = null;
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
                provider = lookups.find(row.fields().get("roster_enrolled_to"));
                mapped = new CdsExportEnrolmentMapper().map(patient, row, provider);
                var rowFields = new LinkedHashMap<>(mapped.fields());
                rowFields.keySet().removeIf(key -> key.table().equals("provider"));
                merge(decisions, rowFields);
                // Only an enrolled provider whose public values no CDS name can hold remains;
                // every roster state is an event or carried, as on a never-imported chart.
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
                }
            }
            entries.add(new Entry(row, fact, mapped, provider));
            if (isCurrent) currentFact = fact;
            if (!isCurrent)
                for (String column : row.fields().keySet()) {
                    var key = new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column);
                    if (!decisions.containsKey(key)) deferred.add(key);
                }
        }
        if (!witnesses.isEmpty()) scope();
        // The source enrolment history always exports as imported. Native events recorded after
        // it follow the never-imported chart's rule (CdsExportEnrolmentHistory): the latest
        // version of an episode is its truth, and an episode left open while the current state
        // is another one (re-rostered without a termination, cleared, moved to FS...) or one
        // dated after the current state was superseded without a recorded termination. No end
        // date is invented; superseded snapshots stay in the approved audit arrangement.
        var kept = keptNativeFacts(entries, sourceFacts, currentFact, checkpoint);
        var appended = new LinkedHashMap<Fact, CdsExportEnrolmentEventDto>();
        for (var entry : entries)
            if (entry.mapped() != null
                    && entry.fact() != null
                    && kept.contains(entry.fact())
                    && !sourceFacts.contains(entry.fact()))
                appended.putIfAbsent(entry.fact(), entry.mapped().event());
        for (var entry : entries) {
            if (entry.mapped() == null
                    || entry.fact() == null
                    || kept.contains(entry.fact())
                    || entry.row() == current) continue;
            for (String column : CdsExportEnrolmentMapper.COLUMNS.keySet()) {
                var key =
                        new CdsExportNativeFieldKeyDto(
                                entry.row().table(), entry.row().recordKey(), column);
                if (decisions.get(key).outcome() != Outcome.ABSENT)
                    decisions.put(
                            key,
                            CdsExportEnrolmentMapper.superseded(
                                    entry.row().fields().get(column), SUPERSEDED));
            }
        }
        // A provider row's decision follows its uses: an exported event's physician, a carried
        // current state, or only superseded snapshots.
        for (int pass = 0; pass < 3; pass++)
            for (var entry : entries) {
                var provider = entry.provider();
                if (provider == null || entry.mapped() == null) continue;
                boolean event = entry.fact() != null && kept.contains(entry.fact());
                boolean carried = entry.fact() == null && entry.row() == current;
                if (pass == 0 && !event || pass == 1 && !carried) continue;
                for (var value : provider.fields().entrySet()) {
                    var key =
                            new CdsExportNativeFieldKeyDto(
                                    provider.table(), provider.recordKey(), value.getKey());
                    var decision =
                            pass == 2
                                    ? CdsExportEnrolmentMapper.superseded(
                                            value.getValue(), SUPERSEDED_PROVIDER)
                                    : entry.mapped().fields().get(key);
                    if (decision == null) scope();
                    decisions.putIfAbsent(key, decision);
                }
            }
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

    /**
     * The native facts that export: per episode (start date and physician), the latest native
     * version decides. An open episode keeps its start events only while it is the current
     * episode or the source history records its termination; a terminated episode keeps its
     * start events and its latest termination. A kept fact of another episode dated after the
     * current state (or on the same day, differently) was superseded.
     */
    private static Set<Fact> keptNativeFacts(
            List<Entry> entries, Set<Fact> sourceFacts, Fact current, Runnable checkpoint) {
        var episodes = new LinkedHashMap<Episode, List<Fact>>();
        for (var entry : entries)
            if (entry.mapped() != null && entry.fact() != null)
                episodes.computeIfAbsent(
                                new Episode(entry.fact().start(), entry.fact().physician()),
                                ignored -> new ArrayList<>())
                        .add(entry.fact());
        var currentEpisode =
                current == null ? null : new Episode(current.start(), current.physician());
        var kept = new HashSet<Fact>();
        for (var episode : episodes.entrySet()) {
            checkpoint.run();
            var versions = episode.getValue();
            var latest = versions.getLast();
            boolean isCurrent = episode.getKey().equals(currentEpisode);
            boolean sourceTerminated =
                    sourceFacts.stream()
                            .anyMatch(
                                    fact ->
                                            "0".equals(fact.status())
                                                    && new Episode(fact.start(), fact.physician())
                                                            .equals(episode.getKey()));
            boolean open = "1".equals(latest.status());
            for (var fact : versions) {
                boolean keep =
                        open
                                ? "1".equals(fact.status()) && (isCurrent || sourceTerminated)
                                : "1".equals(fact.status()) || fact.equals(latest);
                if (keep
                        && !isCurrent
                        && current != null
                        && (fact.effective().isAfter(current.effective())
                                || fact.effective().equals(current.effective())
                                        && !fact.equals(current))) keep = false;
                if (keep) kept.add(fact);
            }
        }
        return kept;
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
