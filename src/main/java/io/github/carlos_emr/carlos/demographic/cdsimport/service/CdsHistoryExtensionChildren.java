/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCppExtensionProjection;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Derives one sealed native child per CPP extension; repeated source rows never share a receipt. */
final class CdsHistoryExtensionChildren {
    private final CdsFieldParser fields;

    CdsHistoryExtensionChildren(CdsFieldParser fields) { this.fields = fields; }

    static boolean supports(CdsRecordDto record) {
        return CdsCppExtensionProjection.SECTIONS.contains(record.section());
    }

    void forEach(CdsRecordDto record, CdsInputLimitsDto limits, Consumer<CdsRecordDto> children)
            throws IOException {
        try { derive(record, limits, children); }
        catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException known) { throw known; }
        catch (IOException unavailable) {
            throw new io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException(
                    "CDS_SPOOL_IO_FAILED", record.sourceXPath(), unavailable.getClass().getSimpleName());
        }
    }

    private void derive(CdsRecordDto record, CdsInputLimitsDto limits, Consumer<CdsRecordDto> children)
            throws IOException {
        if (!supports(record)) throw new IllegalArgumentException("CDS_HISTORY_EXTENSION_PARENT_INVALID");
        var details = new CdsHistoryResidualReader(fields).read(record).revisionDetails();
        CdsHistoryExtensionReader.Source source = details == null ? null : () -> fields.openToken(record, details);
        Map<String, Summary> summaries = new HashMap<>();
        if (source != null) CdsHistoryExtensionReader.current(source, row -> {
            if (known(row.key())) summaries.computeIfAbsent(row.key(), ignored -> new Summary())
                    .accept(record.section(), row);
        });
        var keys = new TreeSet<>(CdsCppExtensionProjection.DATE_KEYS);
        keys.addAll(CdsCppExtensionProjection.VALUE_KEYS);
        Map<String, Element> elements = new HashMap<>();
        for (String key : keys) elements.put(key, element(record, key));
        if (summaries.isEmpty() && elements.values().stream().allMatch(value -> value.shown() == null)) return;
        long[] ordinal = {0};
        try (var spool = new CdsNativeChildSpool(record, limits)) {
            if (source != null) CdsHistoryExtensionReader.current(source, row -> {
                if (!known(row.key())) return; // Operational and unknown keys stay in the original source.
                Summary summary = summaries.get(row.key());
                if (Objects.equals(summary.projection(), elements.get(row.key()).shown())) {
                    children.accept(spool.append(record, Math.incrementExact(ordinal[0]),
                            members(record, row, true, null)));
                    ordinal[0]++;
                }
            });
            for (String key : keys) {
                Summary summary = summaries.get(key);
                Element element = elements.get(key);
                boolean stale = summary != null && !Objects.equals(summary.projection(), element.shown());
                if (summary != null && !stale || element.shown() == null && !stale) continue;
                var row = new CdsHistoryExtensionReader.Value(key, element.value(), element.date());
                children.accept(spool.append(record, Math.incrementExact(ordinal[0]),
                        members(record, row, element.shown() != null,
                                stale ? "CDS_HISTORY_EXTENSION_CARRIER_NOT_APPLIED" : null)));
                ordinal[0]++;
            }
        }
    }

    private static boolean known(String key) {
        return CdsCppExtensionProjection.DATE_KEYS.contains(key)
                || CdsCppExtensionProjection.VALUE_KEYS.contains(key);
    }

    private static Map<String, String> members(CdsRecordDto parent,
            CdsHistoryExtensionReader.Value row, boolean apply, String warning) {
        var values = new TreeMap<String, String>();
        values.put("ParentSection", parent.section());
        values.put("ParentOrdinal", Long.toString(parent.ordinal()));
        values.put("ParentDigest", parent.effectiveSha256());
        values.put("Key", row.key());
        values.put("Value", row.value());
        values.put("Date", row.date());
        values.put("Apply", Boolean.toString(apply));
        values.put("Warning", warning);
        return values;
    }

    private record Element(String shown, String value, String date) {
        @Override public String toString() { return "CdsHistoryExtensionChildren.Element[protected]"; }
    }

    private Element element(CdsRecordDto record, String key) throws IOException {
        String path = CdsCppExtensionProjection.path(record.section(), key);
        if (path == null) return new Element(null, null, null);
        if (!CdsCppExtensionProjection.DATE_KEYS.contains(key)) {
            String value = fields.firstText(record, path, CdsHistoryExtensionReader.VALUE_CAPACITY);
            return new Element(value, value, null);
        }
        for (String part : new String[] {"FullDate", "YearMonth", "YearOnly"}) {
            String value = fields.firstToken(record, path + "/" + part, 32);
            if (value != null) return switch (part) {
                case "YearOnly" -> new Element(value, "YYYY", value + "-01-01");
                case "YearMonth" -> new Element(value, "YYYY-MM", value + "-01");
                default -> new Element(value, null, value);
            };
        }
        return new Element(null, null, null);
    }

    /** Only a digest and a schema-sized candidate are retained for any one of the eleven keys. */
    private static final class Summary {
        private byte[] firstDigest;
        private String projected;
        private boolean conflict;

        void accept(String section, CdsHistoryExtensionReader.Value row) {
            String path = CdsCppExtensionProjection.path(section, row.key());
            String value;
            if (CdsCppExtensionProjection.DATE_KEYS.contains(row.key()))
                value = CdsCppExtensionProjection.date(row.date(), row.value());
            else value = row.value();
            if (value == null || value.isEmpty()) return;
            byte[] digest = CdsPaths.sha256().digest(value.getBytes(StandardCharsets.UTF_8));
            if (firstDigest == null) {
                firstDigest = digest;
                if (path != null && (CdsCppExtensionProjection.DATE_KEYS.contains(row.key())
                        || CdsCppExtensionProjection.fits(path, value))) projected = value;
            } else if (!java.security.MessageDigest.isEqual(firstDigest, digest)) conflict = true;
        }

        String projection() { return conflict ? null : projected; }
    }
}
