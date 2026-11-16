/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto;
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
        return document(demographic, properties, null);
    }

    /**
     * The document of a chart imported from another CARLOS that re-exports the earlier sender's
     * native demographic record before its own, as {@link CdsExportNewCategoryOccurrenceService}
     * does: the chart's own record follows it, the record of a chart that carries nothing
     * included ({@link CdsExportDemographicSupportingMapper#nothingCarried}).
     *
     * @param earlierSender the stored row of the earlier sender's chart, or null for a chart that
     *     re-exports no retained record
     */
    public static byte[] document(
            Map<String, String> demographic,
            List<CdsExportCapturedTextRowDto> properties,
            Map<String, String> earlierSender)
            throws Exception {
        return document(demographic, properties, earlierSender, null, List.of());
    }

    /**
     * The document of a chart with a care team: its {@code demographiccust} row and the public
     * provider rows its care-team columns name, as the export captures them.
     *
     * @param custom the care-team columns (cust1 nurse, cust2 resident, cust4 midwife) of the
     *     chart's {@code demographiccust} row, or null when it has none
     * @param providers the captured public provider rows
     */
    public static byte[] document(
            Map<String, String> demographic,
            List<CdsExportCapturedTextRowDto> properties,
            Map<String, String> custom,
            List<CdsExportCapturedTextRowDto> providers)
            throws Exception {
        return document(demographic, properties, null, custom, providers);
    }

    /** A captured pharmacy row, as Manage Pharmacy stores it, with the given columns changed. */
    public static CdsExportCapturedTextRowDto pharmacy(long id, Map<String, String> changed) {
        var row = CdsExportPharmacyMapperUnitTest.pharmacy(id);
        var fields = new java.util.HashMap<>(row.fields());
        fields.putAll(changed);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    /** A patient 27 link to a pharmacy, ranked first and active. */
    public static CdsExportCapturedTextRowDto link(long id, long pharmacy) {
        return CdsExportPharmacyMapperUnitTest.link(id, pharmacy);
    }

    /**
     * The document of a chart with a preferred pharmacy: PreferredPharmacy written from the
     * rank-one link's pharmacy, and the native demographic record carrying what it cannot hold.
     */
    public static byte[] documentWithPharmacy(
            Map<String, String> demographic,
            List<CdsExportCapturedTextRowDto> links,
            List<CdsExportCapturedTextRowDto> pharmacies)
            throws Exception {
        var row = new CdsExportDemographicRowDto(27, demographic);
        var base = decided(new CdsExportDemographicMapper().map(row), demographic, List.of());
        var mapping = new CdsExportDemographicPhonesMapper().map(base, row, List.of(), () -> {});
        String hash = "c".repeat(64);
        var current = new LinkedHashMap<>(demographic);
        current.put("demographic_no", "27");
        var enrolment =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                        .CdsExportDemographicEnrolmentProjectionDto(
                        hash,
                        mapping,
                        1,
                        0,
                        null,
                        List.of(),
                        new CdsExportEnrolmentMapper()
                                .map(
                                        27,
                                        new CdsExportCapturedTextRowDto(
                                                "demographic", "0000000027", current),
                                        null)
                                .fields());
        var pharmacy =
                new CdsExportPharmacyProjectionService.Result(
                        hash, new CdsExportPharmacyMapper().map(27, links, pharmacies, () -> {}));
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml ->
                                new CdsExportDemographicXmlWriter()
                                        .writeWithPharmacy(enrolment, pharmacy, xml)));
        var captured = new LinkedHashMap<>(demographic);
        captured.put("demographic_no", "27");
        var related = new LinkedHashMap<String, List<CdsExportCapturedTextRowDto>>();
        related.put("demographicPharmacy", links);
        related.put("pharmacyInfo", pharmacies);
        related.put("DemographicContact", List.of());
        related.put("Contact", List.of());
        var record =
                CdsExportDemographicSupportingMapper.map(
                        new CdsExportDemographicSupportingMapper.Rows(
                                27,
                                new CdsExportCapturedTextRowDto("demographic", "0000000027", captured),
                                null,
                                List.of(),
                                Map.of(),
                                related),
                        () -> {});
        if (record != null)
            sections.add(
                    new CdsExportXmlComposer.Section(
                            new CdsExportIdentityDto(
                                    "a".repeat(64), CdsCategory.NEW_CATEGORY, "d".repeat(64)),
                            xml -> CdsExportDemographicSupportingMapper.write(record.json(), xml)));
        return compose(sections);
    }

    /** A captured public provider row (the six columns the export reads). */
    public static CdsExportCapturedTextRowDto provider(
            String id, String first, String last, String ohip, String number, String type) {
        var fields = new java.util.HashMap<String, String>();
        fields.put("provider_no", id);
        fields.put("first_name", first);
        fields.put("last_name", last);
        fields.put("ohip_no", ohip);
        fields.put("practitionerNo", number);
        fields.put("practitionerNoType", type);
        return new CdsExportCapturedTextRowDto(
                "provider", CdsExportPublicProviderSourceReader.key(id), fields);
    }

    private static byte[] document(
            Map<String, String> demographic,
            List<CdsExportCapturedTextRowDto> properties,
            Map<String, String> earlierSender,
            Map<String, String> custom,
            List<CdsExportCapturedTextRowDto> providers)
            throws Exception {
        var row = new CdsExportDemographicRowDto(27, demographic);
        var base = decided(new CdsExportDemographicMapper().map(row), demographic, providers);
        var mapping =
                new CdsExportDemographicPhonesMapper().map(base, row, properties, () -> {});
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().write(mapping, xml)));
        var record = record(demographic, properties, custom, providers);
        if (earlierSender != null) {
            var earlier = record(earlierSender, List.of(), null, List.of());
            if (earlier == null)
                throw new IllegalArgumentException("the earlier row carries nothing");
            sections.add(
                    new CdsExportXmlComposer.Section(
                            new CdsExportIdentityDto(
                                    "a".repeat(64), CdsCategory.NEW_CATEGORY, "e".repeat(64)),
                            xml -> CdsExportDemographicSupportingMapper.write(earlier.json(), xml)));
            if (record == null) record = CdsExportDemographicSupportingMapper.nothingCarried();
        }
        if (record != null) {
            var own = record;
            sections.add(
                    new CdsExportXmlComposer.Section(
                            new CdsExportIdentityDto(
                                    "a".repeat(64), CdsCategory.NEW_CATEGORY, "d".repeat(64)),
                            xml -> CdsExportDemographicSupportingMapper.write(own.json(), xml)));
        }
        return compose(sections);
    }

    /** The composed, schema-validated document of these sections. */
    private static byte[] compose(List<CdsExportXmlComposer.Section> sections) throws Exception {
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

    /**
     * The base mapping with the columns no Demographics element states (alias, Date Joined, the
     * form defaults...) and the roster columns decided, as {@link
     * CdsExportNativeDemographicAssembler} decides them before it writes the element.
     */
    private static CdsExportDemographicMappingDto decided(
            CdsExportDemographicMappingDto base,
            Map<String, String> demographic,
            List<CdsExportCapturedTextRowDto> providers)
            throws Exception {
        var captured = new LinkedHashMap<>(demographic);
        captured.put("demographic_no", "27");
        var current = new CdsExportCapturedTextRowDto("demographic", "0000000027", captured);
        var fields = new LinkedHashMap<>(base.fields());
        var decisions =
                new LinkedHashMap<
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNativeFieldKeyDto,
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportFieldAssessmentDto>(
                        CdsExportDemographicAncillaryReconciler.assess(current, () -> {}));
        String enrolled = demographic.get("roster_enrolled_to");
        CdsExportCapturedTextRowDto provider = null;
        for (var row : providers)
            if (enrolled != null && enrolled.equals(row.fields().get("provider_no"))) provider = row;
        decisions.putAll(new CdsExportEnrolmentMapper().map(27, current, provider).fields());
        for (var entry : decisions.entrySet()) {
            if (!entry.getKey().table().equals("demographic")
                    || entry.getKey().column().equals("demographic_no")) continue;
            var value = entry.getValue();
            fields.put(
                    entry.getKey().column(),
                    new CdsExportDemographicMappingDto.Assessment(
                            CdsExportDemographicMappingDto.Presence.valueOf(value.presence().name()),
                            CdsExportDemographicMappingDto.Outcome.valueOf(value.outcome().name()),
                            value.destination(),
                            value.rule()));
        }
        return new CdsExportDemographicMappingDto(
                base.patient(),
                base.names(),
                base.birthDate(),
                base.healthCard(),
                base.chartNumber(),
                base.administrativeGender(),
                base.addresses(),
                base.phones(),
                base.officialLanguage(),
                base.spokenLanguage(),
                base.email(),
                base.personStatus(),
                base.enumeratedStatus(),
                base.personStatusDate(),
                base.sin(),
                fields,
                base.primaryPhysician(),
                base.relatedFields());
    }

    /** The native demographic record the exporter writes for a chart, or null when none. */
    private static CdsExportDemographicSupportingMapper.Record record(
            Map<String, String> demographic,
            List<CdsExportCapturedTextRowDto> properties,
            Map<String, String> custom,
            List<CdsExportCapturedTextRowDto> providers)
            throws Exception {
        var captured = new LinkedHashMap<>(demographic);
        captured.put("demographic_no", "27");
        CdsExportCapturedTextRowDto customRow = null;
        if (custom != null) {
            var values = new LinkedHashMap<String, String>();
            io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                    .DEMOGRAPHIC_CUST_DEMOGRAPHICS
                    .columns()
                    .forEach(column -> values.put(column, null));
            values.put("demographic_no", "27");
            values.putAll(custom);
            customRow =
                    new CdsExportCapturedTextRowDto(
                            "demographiccust", CdsExportPatientTableSourceReader.key(27), values);
        }
        var named = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        for (var provider : providers) named.put(provider.fields().get("provider_no"), provider);
        return CdsExportDemographicSupportingMapper.map(
                new CdsExportDemographicSupportingMapper.Rows(
                        27,
                        new CdsExportCapturedTextRowDto("demographic", "0000000027", captured),
                        customRow,
                        properties,
                        named),
                () -> {});
    }
}
