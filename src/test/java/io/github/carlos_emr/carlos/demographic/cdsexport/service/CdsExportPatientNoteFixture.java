/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicNoteProjectionDto;
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
 * A patient document as the exporter writes it for a chart with a patient note: the Demographics
 * record with NoteAboutPatient and, when the element cannot hold the stored note exactly, the
 * native demographic NewCategory record carrying the stored column. For tests on the import side
 * that prove a transfer between two CARLOS installations.
 */
public final class CdsExportPatientNoteFixture {
    private static final Set<CdsCategory> CATEGORIES =
            Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.NEW_CATEGORY);

    private CdsExportPatientNoteFixture() {}

    /**
     * The composed, schema-valid patient document.
     *
     * @param column the stored {@code demographiccust.content} value
     */
    public static byte[] document(String column) throws Exception {
        var custom = CdsExportPatientNoteMapperUnitTest.row(27, column);
        var note = new CdsExportPatientNoteMapper().map(27, CATEGORIES, custom);
        var demographic = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(name -> demographic.put(name, null));
        demographic.putAll(
                Map.of(
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "patient_status",
                        "AC",
                        "sex",
                        "U"));
        var base =
                new CdsExportDemographicMapper()
                        .map(new CdsExportDemographicRowDto(27, demographic));
        // The captured row the native demographic record reads names its patient too.
        var captured = new LinkedHashMap<>(demographic);
        captured.put("demographic_no", "27");
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        var value = new CdsExportDemographicNoteProjectionDto("a".repeat(64), base, 1, note);
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().writeWithPatientNote(value, xml)));
        var record =
                CdsExportDemographicSupportingMapper.map(
                        new CdsExportDemographicSupportingMapper.Rows(
                                27,
                                new CdsExportCapturedTextRowDto(
                                        "demographic", "0000000027", captured),
                                custom,
                                List.of(),
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
