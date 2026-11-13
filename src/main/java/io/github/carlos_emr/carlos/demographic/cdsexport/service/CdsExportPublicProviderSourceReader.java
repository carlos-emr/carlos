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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Selected-category public reference candidates, read directly in the same patient snapshot. */
@Service
public class CdsExportPublicProviderSourceReader implements CdsExportNativeSourceReader {
    private static final Set<CdsCategory> SUPPORTED = supported();

    private static Set<CdsCategory> supported() {
        var categories = java.util.EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
        categories.addAll(
                Set.of(
                        CdsCategory.DEMOGRAPHICS,
                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                        CdsCategory.APPOINTMENTS,
                        CdsCategory.MEDICATIONS_AND_TREATMENTS));
        categories.add(CdsCategory.REPORTS);
        // Providers whose acknowledgement of a lab is exported as its ResultReviewer.
        categories.add(CdsCategory.LABORATORY_RESULTS);
        return Set.copyOf(categories);
    }

    private final ProviderDao source;
    private final Set<CdsCategory> selected;
    private final Set<String> wireNames;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPublicProviderSourceReader(ProviderDao source) {
        this(source, Set.of(CdsCategory.DEMOGRAPHICS));
    }

    private CdsExportPublicProviderSourceReader(ProviderDao source, Set<CdsCategory> selected) {
        this.source = java.util.Objects.requireNonNull(source);
        this.selected = Set.copyOf(selected);
        if (selected.isEmpty()
                || !SUPPORTED.containsAll(selected)
                || !java.util.Collections.disjoint(selected, CdsExportNoteSourceReader.CATEGORIES)
                        && !selected.containsAll(CdsExportNoteSourceReader.CATEGORIES))
            throw new IllegalArgumentException("CDS_EXPORT_PUBLIC_PROVIDER_SCOPE_INVALID");
        wireNames =
                selected.stream()
                        .map(CdsCategory::wireName)
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public CdsExportNativeSourceReader forRequest(CdsExportCommand command) {
        return new CdsExportPublicProviderSourceReader(
                source,
                command.categories().stream()
                        .filter(SUPPORTED::contains)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    public Set<String> requiredTables() {
        var required = new java.util.HashSet<String>();
        if (!java.util.Collections.disjoint(selected, CdsExportNoteSourceReader.CATEGORIES))
            required.add("casemgmt_note");
        if (selected.contains(CdsCategory.MEDICATIONS_AND_TREATMENTS)) required.add("prescription");
        if (selected.contains(CdsCategory.REPORTS))
            required.addAll(Set.of("document", "ctl_document"));
        if (selected.contains(CdsCategory.LABORATORY_RESULTS))
            required.addAll(Set.of("patientLabRouting", "providerLabRouting"));
        return Set.copyOf(required);
    }

    @Override
    public String table() {
        return "provider";
    }

    @Override
    public Set<String> columns() {
        return CdsExportProviderRowDto.COLUMNS;
    }

    @Override
    public Set<CdsCategory> categories() {
        return selected;
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        return source.readCdsExportPublicProviderSchema();
    }

    @Override
    public long count(int patient) {
        return source.countCdsExportPublicProviders(patient, wireNames);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        String after = null;
        while (true) {
            var page = source.findCdsExportPublicProvidersAfter(patient, wireNames, after, 32);
            if (page.size() > 32) throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_PAGE_INVALID");
            for (var row : page) {
                String key = key(row.id());
                if (after != null && after.compareTo(key) >= 0)
                    throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_PAGE_INVALID");
                var identity = row.cells().get("provider_no");
                if (identity.sqlNull()
                        || !java.util.Arrays.equals(
                                identity.inlineBytes(),
                                row.id().getBytes(StandardCharsets.UTF_8))) {
                    throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_PAGE_INVALID");
                }
                output.row(
                        key,
                        writer -> {
                            for (var field : row.cells().entrySet()) {
                                if (field.getValue().sqlNull())
                                    writer.text(field.getKey(), (String) null);
                                else
                                    try (var input =
                                                    new CdsExportRawValueInputStream(
                                                            field.getValue(),
                                                            (offset, length) -> {
                                                                throw new IOException(
                                                                        "CDS_EXPORT_PUBLIC_PROVIDER_BOUND_INVALID");
                                                            });
                                            var text =
                                                    new InputStreamReader(
                                                            input,
                                                            StandardCharsets.UTF_8
                                                                    .newDecoder()
                                                                    .onMalformedInput(
                                                                            CodingErrorAction
                                                                                    .REPORT)
                                                                    .onUnmappableCharacter(
                                                                            CodingErrorAction
                                                                                    .REPORT))) {
                                        writer.text(field.getKey(), text);
                                    }
                            }
                        });
                after = key;
            }
            if (page.size() < 32) return;
        }
    }

    /** UTF-8 binary order, reversible and safe for the capture record-key protocol. */
    public static String key(String id) {
        return "p" + HexFormat.of().formatHex(id.getBytes(StandardCharsets.UTF_8));
    }
}
