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
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportMergeLinkRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportSelectionRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportMergeGroupDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Groups a job's selected charts by Merge Records ({@link CdsMergedChartContract}), inside the
 * selection's own read-only snapshot, and writes the frozen group plan ({@code COHORT_GROUPS}).
 *
 * <p>The links touching the selected charts are read 500 charts per statement, then the links of
 * every chart those reach, until no new chart is found, so every group is complete however
 * long its chain and wherever its head is. Only charts with an active link are held in memory.
 * A selected chart with no active link is not in the plan: it is a patient file of its own,
 * exported as before. Every other selected chart brings its head's whole group, even when the
 * head's physician is not in the roster; a group with two selected charts is one patient file.
 * A malformed group (cycle, fork, self-link, a link to a missing chart) is planned by the fixed
 * head rule and never held.
 */
@Service
public class CdsExportMergeGroupPlanner {
    /** Charts named by one statement; the DAO reads accept no more. */
    static final int BATCH = 500;

    private static final ObjectMapper JSON = new ObjectMapper();
    private final DemographicMergedDao merges;
    private final DemographicDao demographics;

    public CdsExportMergeGroupPlanner(DemographicMergedDao merges, DemographicDao demographics) {
        this.merges = Objects.requireNonNull(merges);
        this.demographics = Objects.requireNonNull(demographics);
    }

    /**
     * The plan of one selection.
     *
     * @param files the number of patient files: selected charts with no active link, plus groups
     * @param groups the staged {@code COHORT_GROUPS} member, one JSON line per group ascending by
     *     head (empty when no selected chart has an active link)
     * @param count the number of groups
     */
    public record Plan(long files, CdsExportStagedFileDto groups, long count) {
        public Plan {
            if (files < 0 || count < 0 || groups == null || !"COHORT_GROUPS".equals(groups.purpose()))
                throw new IllegalArgumentException("CDS_EXPORT_SELECTION_RECEIPT_INVALID");
        }
    }

    /**
     * Plans the groups of a staged selection. Runs inside the caller's read-only snapshot, the
     * same one that read the selection, so the plan and the selection agree.
     *
     * @param inventory the staged {@code PATIENT_SELECTION} member, ascending by chart
     * @param selected the number of charts it lists
     */
    public Plan plan(
            CdsExportStagingService staging,
            CdsExportStagedFileDto inventory,
            long selected,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(staging);
        Objects.requireNonNull(checkpoint);
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("CDS_EXPORT_SELECTION_SNAPSHOT_REQUIRED");
        var rows = new TreeMap<Integer, CdsExportMergeLinkRowDto>();
        // Selected charts with an active link, and every chart whose links were read.
        var involved = new TreeSet<Integer>();
        var queried = new HashSet<Integer>();
        long listed = 0;
        try (var input = staging.open(inventory)) {
            var lines = new CdsExportJsonLines(input, CdsExportSelectionRowDto.MAX_SELECTION_LINE_BYTES, checkpoint);
            var batch = new ArrayList<Integer>(BATCH);
            com.fasterxml.jackson.databind.JsonNode node;
            while ((node = lines.next()) != null) {
                checkpoint.run();
                var id = node.get("demographicNo");
                if (id == null || !id.canConvertToInt() || id.intValue() < 1) invalid();
                batch.add(id.intValue());
                listed++;
                if (batch.size() == BATCH) {
                    read(batch, rows, queried, involved, checkpoint);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) read(batch, rows, queried, involved, checkpoint);
        }
        if (listed != selected) invalid();
        // Every chart the links reach, until none is new: a group is complete however far its
        // chain goes and whichever of its charts the job named.
        while (true) {
            var frontier = new TreeSet<Integer>();
            for (var row : rows.values()) {
                if (!queried.contains(row.demographicNo())) frontier.add(row.demographicNo());
                if (!queried.contains(row.mergedTo())) frontier.add(row.mergedTo());
            }
            frontier.removeIf(chart -> chart < 1);
            if (frontier.isEmpty()) break;
            var batch = new ArrayList<Integer>(BATCH);
            for (int chart : frontier) {
                batch.add(chart);
                if (batch.size() == BATCH) {
                    read(batch, rows, queried, null, checkpoint);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) read(batch, rows, queried, null, checkpoint);
            // A chart a link names but no statement could read (a number below 1) is never a
            // chart: it cannot be queried, and it is left out of the frontier above.
            frontier.forEach(queried::add);
        }
        // Which of the named charts exist, and each one's primary physician.
        var named = new TreeSet<Integer>();
        for (var row : rows.values()) {
            if (row.demographicNo() > 0) named.add(row.demographicNo());
            if (row.mergedTo() > 0) named.add(row.mergedTo());
        }
        var physicians = new HashMap<Integer, String>();
        var existing = new HashSet<Integer>();
        var batch = new ArrayList<Integer>(BATCH);
        for (int chart : named) {
            batch.add(chart);
            if (batch.size() == BATCH) {
                exist(batch, existing, physicians, checkpoint);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) exist(batch, existing, physicians, checkpoint);
        var links = new ArrayList<CdsMergedChartContract.Link>(rows.size());
        for (var row : rows.values())
            links.add(
                    new CdsMergedChartContract.Link(
                            row.id(), row.demographicNo(), row.mergedTo(), 0));
        var groups = CdsMergedChartContract.groups(links, existing::contains);
        // Each involved selected chart is in the group of its head; a head's group is written
        // once with every selected chart it holds.
        var byHead = new TreeMap<Integer, TreeSet<Integer>>();
        for (int chart : involved) {
            // A selected chart was read in this snapshot: it exists.
            if (!existing.contains(chart)) invalid();
            byHead.computeIfAbsent(groups.head(chart), ignored -> new TreeSet<>()).add(chart);
        }
        var written = new long[1];
        var file =
                staging.stage(
                        "COHORT_GROUPS",
                        output -> {
                            for (var entry : byHead.entrySet()) {
                                checkpoint.run();
                                int head = entry.getKey();
                                var charts = groups.group(head);
                                var members = new ArrayList<Integer>(charts);
                                members.remove(Integer.valueOf(head));
                                var touching = new TreeSet<Integer>();
                                for (var row : rows.values())
                                    if (charts.contains(row.demographicNo())
                                            || charts.contains(row.mergedTo()))
                                        touching.add(row.id());
                                var shape = new ArrayList<String>();
                                for (var value : groups.shape(head)) shape.add(value.name());
                                var group =
                                        new CdsExportMergeGroupDto(
                                                head,
                                                physicians.get(head),
                                                members,
                                                List.copyOf(entry.getValue()),
                                                List.copyOf(touching),
                                                shape);
                                output.write(JSON.writeValueAsBytes(group));
                                output.write('\n');
                                written[0]++;
                            }
                        });
        long files = Math.addExact(selected - involved.size(), byHead.size());
        return new Plan(files, file, written[0]);
    }

    /**
     * The active links of one batch of charts.
     *
     * @param involved when the batch is selected charts, receives each one an active link names
     */
    private void read(
            List<Integer> batch,
            Map<Integer, CdsExportMergeLinkRowDto> rows,
            Set<Integer> queried,
            Set<Integer> involved,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        var charts = new HashSet<>(batch);
        for (var row : merges.findCdsExportActiveMergeLinks(List.copyOf(batch))) {
            checkpoint.run();
            if (row == null
                    || row.id() < 1
                    || !charts.contains(row.demographicNo()) && !charts.contains(row.mergedTo()))
                invalid();
            var prior = rows.putIfAbsent(row.id(), row);
            if (prior != null && !prior.equals(row)) invalid();
            if (involved != null) {
                if (charts.contains(row.demographicNo())) involved.add(row.demographicNo());
                if (charts.contains(row.mergedTo())) involved.add(row.mergedTo());
            }
        }
        queried.addAll(batch);
    }

    private void exist(
            List<Integer> batch,
            Set<Integer> existing,
            Map<Integer, String> physicians,
            Runnable checkpoint)
            throws IOException {
        checkpoint.run();
        var asked = new HashSet<>(batch);
        for (CdsExportSelectionRowDto row :
                demographics.findCdsExportSelectedPatients(List.copyOf(batch))) {
            if (row == null || !asked.contains(row.demographicNo()) || !existing.add(row.demographicNo()))
                invalid();
            physicians.put(row.demographicNo(), row.physicianNo());
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_SELECTION_SCOPE_REQUIRES_REVIEW");
    }
}
