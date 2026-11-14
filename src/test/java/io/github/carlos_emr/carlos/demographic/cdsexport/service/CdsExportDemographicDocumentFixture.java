/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A patient document as the exporter writes it for a chart's demographic row and extension rows:
 * the Demographics record and, when an element cannot hold a stored value exactly, the native
 * demographic NewCategory record that carries it. For import-side tests that prove a transfer
 * between two CARLOS installations.
 */
public final class CdsExportDemographicDocumentFixture {
    private static final Set<CdsCategory> CATEGORIES =
            Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.NEW_CATEGORY);

    private CdsExportDemographicDocumentFixture() {}

    /** The stored row of a synthetic patient 27 whose values every element holds exactly. */
    public static Map<String, String> row() {
        var demographic = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(name -> demographic.put(name, null));
        demographic.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "F"));
        return demographic;
    }

    /** A {@code demographicExt} row of patient 27, visible, written at a fixed time. */
    public static CdsExportCapturedTextRowDto property(long id, String key, String value) {
        return CdsExportDemographicPhonesMapperUnitTest.property(id, key, value);
    }

    /** The same row with one column changed (the hidden flag, the time). */
    public static CdsExportCapturedTextRowDto with(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new java.util.TreeMap<>(row.fields());
        fields.put(column, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    /**
     * The composed, schema-valid patient document.
     *
     * @param demographic every column of the stored demographic row (see {@link #row})
     * @param properties the patient's {@code demographicExt} rows
     */
    public static byte[] document(
            Map<String, String> demographic, List<CdsExportCapturedTextRowDto> properties)
            throws Exception {
        var row = new CdsExportDemographicRowDto(27, demographic);
        var base = new CdsExportDemographicMapper().map(row);
        var mapping =
                new CdsExportDemographicPhonesMapper().map(base, row, properties, () -> {});
        var captured = new LinkedHashMap<>(demographic);
        captured.put("demographic_no", "27");
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().write(mapping, xml)));
        var record =
                CdsExportDemographicSupportingMapper.map(
                        new CdsExportDemographicSupportingMapper.Rows(
                                27,
                                new CdsExportCapturedTextRowDto(
                                        "demographic", "0000000027", captured),
                                null,
                                properties,
                                Map.of()),
                        () -> {});
        if (record != null)
            sections.add(
                    new CdsExportXmlComposer.Section(
                            new CdsExportIdentityDto(
                                    "a".repeat(64), CdsCategory.NEW_CATEGORY, "d".repeat(64)),
                            xml -> CdsExportDemographicSupportingMapper.write(record.json(), xml)));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "c".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CATEGORIES,
                        sections.iterator(),
                        ignored -> {},
                        1L << 22,
                        () -> {});
        byte[] bytes = output.toByteArray();
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes),
                        "27",
                        CATEGORIES,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        ignored -> {},
                        () -> {});
        return bytes;
    }
}
