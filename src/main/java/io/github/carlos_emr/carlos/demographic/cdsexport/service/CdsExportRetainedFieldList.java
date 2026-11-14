/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;

/**
 * The retained source fields of one imported chart, in the order of their ids, without holding
 * one of them: each element is decoded from its row of the retained index when it is read and
 * is garbage as soon as its reader lets it go.
 *
 * <p>A section used to receive every field of the chart decoded and kept (81,938 of them for a
 * chart of 2,048 notes and 2,048 results, in each of its sections) and found the fields of one
 * imported record by scanning them all, once for each record. This list keeps the id of each
 * field (a reference to a string the index holds) and, once a section has asked for the fields
 * of a record, the ids of each record's fields; a section then holds the decoded fields of the
 * record it is working on, whatever the chart's size.
 *
 * <p>Every row is decoded once when the list is built, so a row that cannot be decoded, or two
 * rows of one id, refuse the chart before any section reads a field, as they did when the
 * fields were kept.
 *
 * @since 2026-11-14
 */
final class CdsExportRetainedFieldList extends AbstractList<CdsExportRetainedFieldDto>
        implements RandomAccess {
    private final CdsExportRetainedIndexDto index;
    private final Map<String, CdsExportCapturedTextRowDto> rows;
    private final int patient;
    private final Set<CdsCategory> categories;
    private final String[] ids;
    private final CdsExportRetainedFieldMapper mapper = new CdsExportRetainedFieldMapper();
    private Map<String, List<String>> byParent;

    private CdsExportRetainedFieldList(
            CdsExportRetainedIndexDto index,
            Map<String, CdsExportCapturedTextRowDto> rows,
            int patient,
            Set<CdsCategory> categories,
            String[] ids) {
        this.index = index;
        this.rows = rows;
        this.patient = patient;
        this.categories = categories;
        this.ids = ids;
    }

    /**
     * Decodes every retained field row of {@code index} once, keeping none.
     *
     * @throws IOException when a row cannot be decoded or two rows have one id
     */
    static CdsExportRetainedFieldList of(
            CdsExportRetainedIndexDto index,
            int patient,
            Set<CdsCategory> categories,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(index);
        Objects.requireNonNull(checkpoint);
        var selected = Set.copyOf(categories);
        var stored = index.tables().get("cds_import_field");
        var mapper = new CdsExportRetainedFieldMapper();
        var ids = new String[stored.size()];
        boolean keyed = true;
        int next = 0;
        for (var entry : stored.entrySet()) {
            checkpoint.run();
            String id = mapper.map(patient, selected, entry.getValue()).id();
            keyed &= id.equals(entry.getKey());
            ids[next++] = id;
        }
        Arrays.sort(ids);
        for (int i = 1; i < ids.length; i++) if (ids[i].equals(ids[i - 1])) invalid();
        Map<String, CdsExportCapturedTextRowDto> rows = stored;
        if (!keyed) {
            // An index whose field rows are not keyed by their ids: find them by id all the same.
            rows = new HashMap<>();
            for (var row : stored.values()) rows.put(row.fields().get("id"), row);
        }
        return new CdsExportRetainedFieldList(index, rows, patient, selected, ids);
    }

    /** The index whose field rows this list decodes. */
    CdsExportRetainedIndexDto index() {
        return index;
    }

    @Override
    public int size() {
        return ids.length;
    }

    @Override
    public CdsExportRetainedFieldDto get(int position) {
        var field = find(ids[position]);
        if (field == null) throw new IllegalStateException("CDS_EXPORT_RETAINED_VALUES_INVALID");
        return field;
    }

    /** The field of this id, decoded now; null when the chart has none. */
    CdsExportRetainedFieldDto find(String id) {
        var row = id == null ? null : rows.get(id);
        if (row == null) return null;
        try {
            return mapper.map(patient, categories, row);
        } catch (IOException changed) {
            // Every row was decoded when the list was built; the rows are immutable.
            throw new IllegalStateException("CDS_EXPORT_RETAINED_VALUES_INVALID");
        }
    }

    /**
     * The ids of the fields whose native parent is the imported record at {@code address}, in
     * the order of the ids. The first call groups every field of the chart by its record once
     * (references to strings the index holds).
     */
    List<String> idsOf(String address) {
        if (byParent == null) {
            var grouped = new HashMap<String, List<String>>();
            for (var parent : index.nativeParents().entrySet())
                grouped.computeIfAbsent(parent.getValue(), ignored -> new ArrayList<>(4))
                        .add(parent.getKey());
            for (var list : grouped.values()) list.sort(null);
            byParent = grouped;
        }
        return byParent.getOrDefault(address, List.of());
    }

    @Override
    public String toString() {
        return "CdsExportRetainedFieldList[protected]";
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_RETAINED_VALUES_REQUIRES_REVIEW");
    }
}
