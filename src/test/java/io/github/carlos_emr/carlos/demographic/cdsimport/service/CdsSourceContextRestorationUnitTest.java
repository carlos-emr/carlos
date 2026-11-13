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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparedImmunizationDefaults;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparedMedicationDefaults;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Restoring the native values a CARLOS sender carries only in its {@code *.Source.v1} residuals
 * (#6198): the medication quantity unit and as-needed flag, and the vaccine's free-text "Other"
 * provider and reminder date. Each is restored only from a well-formed carrier that agrees with
 * the standard elements the exporter wrote from the same row; anything else requires review.
 */
@Tag("unit")
class CdsSourceContextRestorationUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ESCAPED_SPACE = "\\u0020";
    @TempDir Path directory;
    private final CdsClinicalNormalizationService normalization =
            new CdsClinicalNormalizationService(new CdsFieldParser());

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldRestoreTheQuantityUnitAndAsNeededFlag_fromAMatchingCarlosCarrier() throws Exception {
        // The Rx page's "100 mL" of a custom liquid with the as-needed box ticked beside BID: the
        // exporter wrote Quantity "100 mL" and Frequency "BID PRN".
        var record =
                medication(
                        Map.of("Quantity", "100 mL", "Frequency", "BID PRN"),
                        medicationCarrier(drug("100", "", "mL", "1")));
        var value = normalization.medication(record);
        assertThat(value.quantity()).isEqualTo("100");
        assertThat(value.quantityUnit()).isEqualTo("mL");
        assertThat(value.asNeeded()).isTrue();
        assertThat(value.frequency()).isEqualTo("BID PRN");
        var drug =
                new CdsMedicationPersister(
                                mock(DrugDao.class), mock(PartialDateDao.class), normalization)
                        .candidate(27, value);
        assertThat(drug.getQuantity()).isEqualTo("100");
        assertThat(drug.getUnitName()).isEqualTo("mL");
        assertThat(drug.isPrn()).isTrue();
        assertThat(drug.getFreqCode()).isEqualTo("BID PRN");
    }

    @Test
    void shouldRestoreAnUnsetAsNeededFlag_besideAPrnFrequency() throws Exception {
        // The box left unticked beside a PRN frequency is an ordinary saved prescription: the
        // exporter wrote both as chosen, and the importer restores the flag as recorded.
        var value =
                normalization.medication(
                        medication(
                                Map.of("Quantity", "30", "Frequency", "PRN"),
                                medicationCarrier(drug("30", "tab", "tab", "0"))));
        assertThat(value.asNeeded()).isFalse();
        assertThat(value.quantityUnit()).isNull();
        var drug =
                new CdsMedicationPersister(
                                mock(DrugDao.class), mock(PartialDateDao.class), normalization)
                        .candidate(27, value);
        assertThat(drug.isPrn()).isFalse();
    }

    @Test
    void shouldKeepTheEarlierDerivation_withoutACarlosCarrier() throws Exception {
        var value =
                normalization.medication(
                        medication(
                                Map.of(
                                        "Quantity",
                                        "30",
                                        "Frequency",
                                        "PRN",
                                        "DosageUnitOfMeasure",
                                        "tablet")));
        assertThat(value.quantityUnit()).isNull();
        assertThat(value.asNeeded()).isNull();
        var drug =
                new CdsMedicationPersister(
                                mock(DrugDao.class), mock(PartialDateDao.class), normalization)
                        .candidate(27, value);
        assertThat(drug.getUnitName()).isEqualTo("tablet");
        assertThat(drug.isPrn()).isTrue();
        // An unrelated residual element stays opaque source and restores nothing.
        var unrelated =
                normalization.medication(
                        medicationResidual(
                                Map.of("Quantity", "30"), "Vendor.Unrelated", "{", "string"));
        assertThat(unrelated.quantityUnit()).isNull();
        assertThat(unrelated.asNeeded()).isNull();
    }

    @Test
    void shouldRefuseAMedicationCarrier_thatContradictsTheStandardQuantityOrFrequency()
            throws Exception {
        String liquid = medicationCarrier(drug("100", "", "mL", "0"));
        for (var standard :
                List.of(
                        Map.of("Quantity", "100"),
                        Map.of("Quantity", "50 mL"),
                        Map.of("Quantity", "100 L"),
                        Map.<String, String>of())) {
            var record = medication(standard, liquid);
            assertThatThrownBy(() -> normalization.medication(record))
                    .as(standard.toString())
                    .isInstanceOf(CdsClinicalException.class)
                    .hasMessageContaining("CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW");
        }
        // A set as-needed flag is always shown in the standard elements the exporter writes.
        var hidden =
                medication(
                        Map.of("Quantity", "30", "Frequency", "BID"),
                        medicationCarrier(drug("30", "tab", "tab", "1")));
        assertThatThrownBy(() -> normalization.medication(hidden))
                .hasMessageContaining("CDS_MEDICATION_NATIVE_FIELD_REQUIRES_REVIEW");
        var treatment =
                normalization.medication(
                        medication(
                                Map.of(
                                        "Quantity",
                                        "30",
                                        "Frequency",
                                        "BID",
                                        "TreatmentType",
                                        "PRN Long-term"),
                                medicationCarrier(drug("30", "tab", "tab", "1"))));
        assertThat(treatment.asNeeded()).isTrue();
    }

    @Test
    void shouldRefuseAMalformedOrDuplicatedMedicationCarrier_insteadOfGuessing() throws Exception {
        var unflagged = drug("30", "tab", "tab", "yes");
        var missing = drug("30", "tab", "tab", "0");
        missing.remove("prn");
        String valid = medicationCarrier(drug("30", "tab", "tab", "0"));
        for (String content :
                List.of(
                        "{",
                        "not json",
                        valid + "x",
                        valid.replace("\"version\":1", "\"version\":2"),
                        valid.replace("\"version\":1", "\"version\":\"1\""),
                        "{\"version\":1,\"version\":1,\"fields\":{}}",
                        "{\"version\":1,\"fields\":{\"quantity\":\"1\",\"quantity\":\"2\","
                                + "\"unit\":null,\"unitName\":null,\"prn\":\"0\"}}",
                        "{\"version\":1,\"fields\":{\"quantity\":1,\"unit\":null,"
                                + "\"unitName\":null,\"prn\":\"0\"}}",
                        "{\"version\":1,\"fields\":[]}",
                        medicationCarrier(missing),
                        medicationCarrier(unflagged))) {
            var record = medication(Map.of("Quantity", "30"), content);
            assertThatThrownBy(() -> normalization.medication(record))
                    .as(content)
                    .hasMessageContaining("CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW");
        }
        var duplicated = medication(Map.of("Quantity", "30"), valid, valid);
        assertThatThrownBy(() -> normalization.medication(duplicated))
                .hasMessageContaining("CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW");
        var boolType =
                medicationResidual(
                        Map.of("Quantity", "30"),
                        CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT,
                        valid,
                        "boolean");
        assertThatThrownBy(() -> normalization.medication(boolType))
                .hasMessageContaining("CDS_MEDICATION_NATIVE_STATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldRestoreTheOtherProviderAndReminderDate_fromAMatchingCarlosCarrier()
            throws Exception {
        String name = "FAKE-Other  Clinician café";
        var record =
                immunization(
                        "N",
                        preventionCarrier(
                                prevention("-1", "2025-02-28", "0"),
                                extension(30, "providerName", ""),
                                extension(31, "providerName", name),
                                extension(32, "lot", "AB123")));
        var value = normalization.immunization(record);
        assertThat(value.providerName()).isEqualTo(name);
        assertThat(value.reminderDate()).isEqualTo("2025-02-28");
        var preventions = mock(PreventionDao.class);
        var extensions = mock(PreventionExtDao.class);
        doAnswer(
                        call -> {
                            Prevention written = call.getArgument(0);
                            ReflectionTestUtils.setField(written, "id", 73);
                            return null;
                        })
                .when(preventions)
                .persist(any());
        var writer =
                new CdsImmunizationPersister(
                        preventions, extensions, mock(PartialDateDao.class), normalization, "");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        writer.write(42, "999998", value);
        var saved = ArgumentCaptor.forClass(Prevention.class);
        verify(preventions).persist(saved.capture());
        assertThat(saved.getValue().getProviderNo()).isEqualTo("-1");
        assertThat(saved.getValue().getCreatorProviderNo()).isEqualTo("999998");
        assertThat(
                        new java.sql.Date(saved.getValue().getNextDate().getTime())
                                .toLocalDate()
                                .toString())
                .isEqualTo("2025-02-28");
        var extra = ArgumentCaptor.forClass(PreventionExt.class);
        verify(extensions, atLeastOnce()).persist(extra.capture());
        assertThat(extra.getAllValues())
                .filteredOn(ext -> "providerName".equals(ext.getkeyval()))
                .singleElement()
                .satisfies(ext -> assertThat(ext.getVal()).isEqualTo(name));
    }

    @Test
    void shouldNotRestoreTheSendersProviderNumberOrHiddenName_forAChosenCarlosProvider()
            throws Exception {
        // A CARLOS provider number belongs to the sender; the name box is hidden and unused for a
        // chosen provider. Neither is restored and the import attributes no clinician.
        var value =
                normalization.immunization(
                        immunization(
                                "N",
                                preventionCarrier(
                                        prevention("999998", null, "0"),
                                        extension(30, "providerName", "Stale typed name"))));
        assertThat(value.providerName()).isNull();
        assertThat(value.reminderDate()).isNull();
        var candidate =
                new CdsImmunizationPersister(
                                mock(PreventionDao.class),
                                mock(PreventionExtDao.class),
                                mock(PartialDateDao.class),
                                normalization,
                                "")
                        .candidate(42, "999998", value);
        assertThat(candidate.getProviderNo()).isEmpty();
        assertThat(candidate.getNextDate()).isNull();
        // The other-provider choice with no typed name restores nothing either.
        assertThat(
                        normalization
                                .immunization(
                                        immunization(
                                                "N",
                                                preventionCarrier(
                                                        prevention("-1", "0000-00-00", "3"),
                                                        extension(30, "providerName", ""))))
                                .providerName())
                .isNull();
    }

    @Test
    void shouldShiftTheRestoredReminderDate_withTheAuthorizedSyntheticDateShift() throws Exception {
        var record =
                immunization(
                        "Y",
                        preventionCarrier(
                                prevention("-1", "2024-02-28", "1"),
                                extension(30, "providerName", "FAKE-Clinician")));
        assertThat(normalization.immunization(record, command(2)).reminderDate())
                .isEqualTo("2024-03-01");
        assertThat(normalization.immunization(record, command(0)).reminderDate())
                .isEqualTo("2024-02-28");
    }

    @Test
    void shouldRefuseAnImmunizationCarrier_thatContradictsRefusedFlagOrIsMalformed()
            throws Exception {
        // Refused (1) and ineligible (2) were exported as RefusedFlag Y; given (0) and completed
        // externally (3) as N.
        for (var pair : List.of(List.of("1", "N"), List.of("2", "N"), List.of("0", "Y"))) {
            var record =
                    immunization(
                            pair.get(1),
                            preventionCarrier(prevention("-1", null, pair.getFirst())));
            assertThatThrownBy(() -> normalization.immunization(record))
                    .as(pair.toString())
                    .hasMessageContaining("CDS_IMMUNIZATION_NATIVE_FIELD_REQUIRES_REVIEW");
        }
        String valid = preventionCarrier(prevention("-1", "2025-02-28", "0"));
        var badExtension = new LinkedHashMap<String, Object>();
        badExtension.put("version", 1);
        badExtension.put("prevention", prevention("-1", null, "0"));
        badExtension.put("extensions", List.of("providerName"));
        badExtension.put("precision", List.of());
        for (String content :
                List.of(
                        "[]",
                        valid + "{}",
                        valid.replace("\"version\":1", "\"version\":3"),
                        preventionCarrier(prevention("-1", "2025-02-30", "0")),
                        preventionCarrier(prevention("-1", "28/02/2025", "0")),
                        preventionCarrier(prevention("-1", null, "4")),
                        preventionCarrier(prevention("-1", null, null)),
                        preventionCarrier(
                                prevention("-1", null, "0"),
                                extension(30, "providerName", "First"),
                                extension(31, "providerName", "Second")),
                        JSON.writeValueAsString(badExtension))) {
            var record = immunization("N", content);
            assertThatThrownBy(() -> normalization.immunization(record))
                    .as(content)
                    .hasMessageContaining("CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW");
        }
        var duplicated = immunization("N", valid, valid);
        assertThatThrownBy(() -> normalization.immunization(duplicated))
                .hasMessageContaining("CDS_IMMUNIZATION_NATIVE_STATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldKeepPreparedBytesOfUnrestoredValues_andDecodeEarlierPayloads() throws Exception {
        var mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var plain =
                normalization.immunization(
                        CdsClinicalFixture.record(
                                directory,
                                "Immunizations",
                                Map.of(
                                        "ImmunizationName",
                                        "Synthetic vaccine",
                                        "RefusedFlag/ynIndicatorsimple",
                                        "N")));
        String legacy = mapper.writeValueAsString(plain);
        assertThat(legacy).doesNotContain("providerName").doesNotContain("reminderDate");
        var decoded =
                mapper.treeToValue(
                        CdsPreparedImmunizationDefaults.apply(mapper.readTree(legacy)),
                        CdsImmunizationDto.class);
        assertThat(decoded).isEqualTo(plain);
        assertThatThrownBy(
                        () ->
                                CdsPreparedImmunizationDefaults.apply(
                                        mapper.readTree("{\"providerName\":1}")))
                .hasMessage("CDS_PREPARED_IMMUNIZATION_DEFAULTS_INVALID");
        var medication = normalization.medication(medication(Map.of("Quantity", "30")));
        String medicationLegacy =
                mapper.writeValueAsString(new CdsMedicationNativeDto(medication, null));
        assertThat(medicationLegacy).doesNotContain("quantityUnit").doesNotContain("asNeeded");
        var tree = CdsPreparedMedicationDefaults.apply(mapper.readTree(medicationLegacy));
        assertThat(tree.path("source").get("quantityUnit").isNull()).isTrue();
        assertThat(tree.path("source").get("asNeeded").isNull()).isTrue();
        var restored =
                normalization.medication(
                        medication(
                                Map.of("Quantity", "100 mL", "Frequency", "BID PRN"),
                                medicationCarrier(drug("100", "", "mL", "1"))));
        String sealed = mapper.writeValueAsString(new CdsMedicationNativeDto(restored, null));
        assertThat(sealed).contains("\"quantityUnit\":\"mL\"", "\"asNeeded\":true");
        var again =
                mapper.treeToValue(
                        CdsPreparedMedicationDefaults.apply(mapper.readTree(sealed)),
                        CdsMedicationNativeDto.class);
        assertThat(again.source()).isEqualTo(restored);
    }

    private static Map<String, String> drug(
            String quantity, String unit, String unitName, String prn) {
        var row = new LinkedHashMap<String, String>();
        row.put("drugid", "51");
        row.put("BN", "Synthetic  brand");
        row.put("quantity", quantity);
        row.put("unit", unit);
        row.put("unitName", unitName);
        row.put("prn", prn);
        row.put("special", null);
        return row;
    }

    /** As CdsExportNativeMedicationMapper writes it: fixed key order, every space escaped. */
    private static String medicationCarrier(Map<String, String> fields) throws Exception {
        var document = new LinkedHashMap<String, Object>();
        document.put("version", 1);
        document.put("fields", fields);
        document.put("exactNumbers", Map.of());
        document.put("precision", List.of());
        document.put("regionalIdentifierAuthority", "UNQUALIFIED");
        document.put("currentness", "Active");
        return JSON.writeValueAsString(document).replace(" ", ESCAPED_SPACE);
    }

    private static Map<String, String> prevention(
            String providerNo, String nextDate, String refused) {
        var row = new LinkedHashMap<String, String>();
        row.put("id", "17");
        row.put("demographic_no", "27");
        row.put("prevention_type", "Td");
        row.put("provider_no", providerNo);
        row.put("provider_name", null);
        row.put("next_date", nextDate);
        row.put("never", "0");
        row.put("refused", refused);
        return row;
    }

    private static Map<String, String> extension(int id, String key, String value) {
        var row = new LinkedHashMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("keyval", key);
        row.put("prevention_id", "17");
        row.put("val", value);
        return row;
    }

    /** As CdsExportImmunizationMapper writes it: fixed key order, every space escaped. */
    @SafeVarargs
    private static String preventionCarrier(
            Map<String, String> prevention, Map<String, String>... extensions) throws Exception {
        var document = new LinkedHashMap<String, Object>();
        document.put("version", 1);
        document.put("prevention", prevention);
        document.put("extensions", Arrays.asList(extensions));
        document.put("precision", List.of());
        return JSON.writeValueAsString(document).replace(" ", ESCAPED_SPACE);
    }

    private CdsRecordDto medication(Map<String, String> standard, String... carriers)
            throws Exception {
        var values = new LinkedHashMap<String, String>();
        int element = 1;
        for (String carrier : carriers)
            residual(
                    values,
                    element++,
                    CdsNativeExtensionContract.MEDICATION_SOURCE_CONTEXT,
                    carrier,
                    "string");
        values.put("DrugName", "Synthetic source medicine");
        values.putAll(standard);
        return CdsClinicalFixture.record(directory, "MedicationsAndTreatments", values);
    }

    private CdsRecordDto medicationResidual(
            Map<String, String> standard, String name, String content, String type)
            throws Exception {
        var values = new LinkedHashMap<String, String>();
        residual(values, 1, name, content, type);
        values.put("DrugName", "Synthetic source medicine");
        values.putAll(standard);
        return CdsClinicalFixture.record(directory, "MedicationsAndTreatments", values);
    }

    private CdsRecordDto immunization(String refused, String... carriers) throws Exception {
        var values = new LinkedHashMap<String, String>();
        int element = 1;
        for (String carrier : carriers)
            residual(
                    values,
                    element++,
                    CdsNativeExtensionContract.PREVENTION_SOURCE_CONTEXT,
                    carrier,
                    "string");
        values.put("ImmunizationName", "Synthetic vaccine");
        values.put("RefusedFlag/ynIndicatorsimple", refused);
        return CdsClinicalFixture.record(directory, "Immunizations", values);
    }

    private static void residual(
            Map<String, String> values, int element, String name, String content, String type) {
        String prefix = "ResidualInfo/DataElement[" + element + "]/";
        values.put(prefix + "Name", name);
        values.put(prefix + "DataType", type);
        values.put(prefix + "Content", content);
    }

    private CdsImportCommand command(int days) {
        return new CdsImportCommand(
                directory,
                "synthetic-date-test",
                "999998",
                null,
                null,
                "synthetic-fixture",
                "cds-5.2",
                false,
                false,
                false,
                days,
                1,
                true);
    }
}
