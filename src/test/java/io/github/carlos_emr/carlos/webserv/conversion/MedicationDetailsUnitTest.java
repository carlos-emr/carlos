/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.webserv.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.FavoriteTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MedicationDetails;
import io.github.carlos_emr.carlos.webserv.rest.util.RestXmlAnnotationIntrospector;
import jakarta.xml.bind.JAXBContext;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class MedicationDetailsUnitTest {
    @Test
    void strictLegacyShapesOmitTheOptInDetails() throws Exception {
        ObjectMapper mapper = restMapper();
        JAXBContext xml = JAXBContext.newInstance(DrugTo1.class, FavoriteTo1.class);
        for (Object legacy : List.of(new DrugTo1(), new FavoriteTo1())) {
            assertThat(mapper.writeValueAsString(legacy)).doesNotContain("medicationDetails");
            StringWriter body = new StringWriter();
            xml.createMarshaller().marshal(legacy, body);
            assertThat(body.toString()).doesNotContain("medicationDetails");
        }
    }

    @Test
    void jsonAndXmlKeepTheRecordedAmountAndEveryComponent() throws Exception {
        var entered = new MedicationDetails();
        entered.setQuantityText(" 7.50 ");
        entered.setDispenseUnit("mL");
        entered.setDosageText("250.0 mg/5 mL + 31.25 mg/5 mL");
        entered.setComponents(List.of(new MedicationDetails.Component("Ingredient A", "250.0", "mg/5 mL"),
                new MedicationDetails.Component("Ingredient B", "31.25", "mg/5 mL")));
        var stored = MedicationDetails.read(entered.getQuantityText(), entered.getDispenseUnit(),
                entered.getDosageText(), entered.encodeComponents());
        assertThat(stored.isComponentDataComplete()).isTrue();
        DrugTo1 dto = new DrugTo1();
        dto.setMedicationDetails(stored);
        ObjectMapper mapper = restMapper();
        String json = mapper.writeValueAsString(dto);
        var details = mapper.readTree(json).path("medicationDetails");
        assertThat(details.path("components").isArray()).isTrue();
        assertThat(details.has("component")).isFalse();
        assertComplete(mapper.readValue(json, DrugTo1.class).getMedicationDetails());
        JAXBContext xml = JAXBContext.newInstance(DrugTo1.class);
        StringWriter body = new StringWriter();
        xml.createMarshaller().marshal(dto, body);
        assertComplete(((DrugTo1) xml.createUnmarshaller().unmarshal(new StringReader(body.toString()))).getMedicationDetails());
        assertThatThrownBy(() -> stored.validateQuantity(7)).isInstanceOf(IllegalArgumentException.class);
        assertThat(MedicationDetails.read("1/2 bottle", null, "250 mg", null).isComponentDataComplete()).isFalse();
    }

    @Test
    void shouldConvertFavoriteWithExactDetailsAndLegacyStrength_viaFavoriteConverter() {
        var converter = new io.github.carlos_emr.carlos.webserv.rest.conversion.FavoriteConverterImpl();
        var loggedInInfo = new io.github.carlos_emr.carlos.utility.LoggedInInfo();
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider();
        provider.setProviderNo("999998");
        loggedInInfo.setLoggedInProvider(provider);

        var optIn = new FavoriteTo1();
        optIn.setFavoriteName("Opt-in Suspension");
        optIn.setBrandName("Synthetic Suspension");
        optIn.setGenericName("Synthetic Suspension");
        optIn.setTakeMin(1f);
        optIn.setTakeMax(1f);
        optIn.setPrn(false);
        var details = new MedicationDetails();
        details.setQuantityText("7.5");
        details.setDispenseUnit("mL");
        details.setDosageText("250 mg/5 mL");
        details.setComponents(List.of(new MedicationDetails.Component("Ingredient A", "250", "mg/5 mL")));
        optIn.setMedicationDetails(details);

        var convertedOptIn = converter.getAsDomainObject(loggedInInfo, optIn);
        assertThat(convertedOptIn.isExactMedicationDetails()).isTrue();
        assertThat(convertedOptIn.getQuantity()).isEqualTo("7.5");
        assertThat(convertedOptIn.getUnitName()).isEqualTo("mL");
        assertThat(convertedOptIn.getDosage()).isEqualTo("250 mg/5 mL");
        assertThat(convertedOptIn.getMedicationComponents()).contains("Ingredient A");

        var clearedComponents = new FavoriteTo1();
        clearedComponents.setFavoriteName("Cleared Components");
        clearedComponents.setTakeMin(1f);
        clearedComponents.setTakeMax(1f);
        clearedComponents.setPrn(false);
        var emptyDetails = new MedicationDetails();
        emptyDetails.setQuantityText("30");
        emptyDetails.setDosageText("500 mg");
        clearedComponents.setMedicationDetails(emptyDetails);

        var convertedCleared = converter.getAsDomainObject(loggedInInfo, clearedComponents);
        assertThat(convertedCleared.isExactMedicationDetails()).isTrue();
        assertThat(convertedCleared.getMedicationComponents()).isNull();

        var legacy = new FavoriteTo1();
        legacy.setFavoriteName("Legacy Tablet");
        legacy.setTakeMin(1f);
        legacy.setTakeMax(1f);
        legacy.setPrn(false);
        legacy.setQuantity(30);
        legacy.setStrength(500f);
        legacy.setStrengthUnit("mg");
        legacy.setRepeats(2);

        var convertedLegacy = converter.getAsDomainObject(loggedInInfo, legacy);
        assertThat(convertedLegacy.isExactMedicationDetails()).isFalse();
        assertThat(convertedLegacy.getDosage()).isEqualTo("500 mg");
        assertThat(convertedLegacy.getQuantity()).isEqualTo("30");
        assertThat(convertedLegacy.getRepeat()).isEqualTo(2);

        convertedOptIn.setId(42);
        convertedOptIn.setDuration("10");
        convertedOptIn.setNosubs(true);
        var back = converter.getAsTransferObject(loggedInInfo, convertedOptIn);
        assertThat(back.getDuration()).isEqualTo(10);
        assertThat(back.getQuantity()).isNull();
        assertThat(back.getStrength()).isEqualTo(250f);
        assertThat(back.getStrengthUnit()).isEqualTo("mg/5 mL");
        assertThat(back.getNoSubstitutions()).isTrue();

        var legacyNoSubs = new FavoriteTo1();
        legacyNoSubs.setFavoriteName("Legacy NoSubs");
        legacyNoSubs.setTakeMin(1f);
        legacyNoSubs.setTakeMax(1f);
        legacyNoSubs.setPrn(false);
        legacyNoSubs.setNoSubstitutions(true);
        legacyNoSubs.setStrength(500f);
        legacyNoSubs.setStrengthUnit("mg");
        var convertedNoSubs = converter.getAsDomainObject(loggedInInfo, legacyNoSubs);
        assertThat(convertedNoSubs.isNosubs()).isTrue();
        assertThat(convertedNoSubs.getDosage()).isEqualTo("500 mg");

        var omittedPrnAndName = new FavoriteTo1();
        omittedPrnAndName.setProviderNo(12345);
        omittedPrnAndName.setTakeMin(1f);
        omittedPrnAndName.setTakeMax(1f);
        var convertedSparse = converter.getAsDomainObject(loggedInInfo, omittedPrnAndName);
        assertThat(convertedSparse.getProviderNo()).isEqualTo("999998");
        assertThat(convertedSparse.getName()).isEmpty();
        assertThat(convertedSparse.isPrn()).isFalse();
        assertThat(convertedSparse.isNosubs()).isFalse();

        var originalDrug = new io.github.carlos_emr.carlos.commn.model.Drug();
        originalDrug.setGenericName("Amoxicillin");
        originalDrug.setBrandName("Amoxil");
        originalDrug.setDrugForm("TAB");
        originalDrug.setDosage("500 mg");
        originalDrug.setUnit("mg");
        var updatedSameDosage = new io.github.carlos_emr.carlos.commn.model.Drug();
        updatedSameDosage.setGenericName("Amoxicillin");
        updatedSameDosage.setBrandName("Amoxil");
        updatedSameDosage.setDrugForm("TAB");
        updatedSameDosage.setExactMedicationDetails("7.5", "mL", "500 mg", null);
        updatedSameDosage.preserveUnchangedMedicationValues(originalDrug);
        assertThat(updatedSameDosage.getUnit()).isEqualTo("mg");

        // Clearing dosageText must NOT carry a product strength unit ("mg") forward as an administered dose unit.
        var clearedDosage = new io.github.carlos_emr.carlos.commn.model.Drug();
        clearedDosage.setGenericName("Amoxicillin");
        clearedDosage.setBrandName("Amoxil");
        clearedDosage.setDrugForm("TAB");
        clearedDosage.setExactMedicationDetails("30", "tabs", null, null);
        clearedDosage.preserveUnchangedMedicationValues(originalDrug);
        assertThat(clearedDosage.getDosage()).isNull();
        assertThat(clearedDosage.getUnit()).isNull();
        assertThat(io.github.carlos_emr.carlos.prescript.util.RxUtil.recordedDoseUnit(
                clearedDosage.getUnit(), clearedDosage.getDrugForm(), clearedDosage.getDosage(), clearedDosage.getCdsDoseText()))
                .isNull();

        // Changing dosageText derives the new strength unit instead of keeping the stale one.
        var changedDosage = new io.github.carlos_emr.carlos.commn.model.Drug();
        changedDosage.setGenericName("Amoxicillin");
        changedDosage.setBrandName("Amoxil");
        changedDosage.setDrugForm("TAB");
        changedDosage.setExactMedicationDetails("30", "tabs", "250 mcg", null);
        changedDosage.preserveUnchangedMedicationValues(originalDrug);
        assertThat(changedDosage.getUnit()).isEqualTo("mcg");

        // Explicit unit on changed dosageText is retained as supplied.
        var explicitUnitChangedDosage = new io.github.carlos_emr.carlos.commn.model.Drug();
        explicitUnitChangedDosage.setGenericName("Amoxicillin");
        explicitUnitChangedDosage.setBrandName("Amoxil");
        explicitUnitChangedDosage.setDrugForm("TAB");
        explicitUnitChangedDosage.setUnit("TAB");
        explicitUnitChangedDosage.setExactMedicationDetails("30", "tabs", "250 mcg", null);
        explicitUnitChangedDosage.preserveUnchangedMedicationValues(originalDrug);
        assertThat(explicitUnitChangedDosage.getUnit()).isEqualTo("TAB");

        // A verified administered dose unit ("TAB" matching drugForm) is preserved across dosageText clearing.
        var verifiedUnitOriginal = new io.github.carlos_emr.carlos.commn.model.Drug();
        verifiedUnitOriginal.setGenericName("Amoxicillin");
        verifiedUnitOriginal.setBrandName("Amoxil");
        verifiedUnitOriginal.setDrugForm("TAB");
        verifiedUnitOriginal.setDosage("500 mg");
        verifiedUnitOriginal.setUnit("TAB");
        var clearedWithVerifiedUnit = new io.github.carlos_emr.carlos.commn.model.Drug();
        clearedWithVerifiedUnit.setGenericName("Amoxicillin");
        clearedWithVerifiedUnit.setBrandName("Amoxil");
        clearedWithVerifiedUnit.setDrugForm("TAB");
        clearedWithVerifiedUnit.setExactMedicationDetails("30", "tabs", null, null);
        clearedWithVerifiedUnit.preserveUnchangedMedicationValues(verifiedUnitOriginal);
        assertThat(clearedWithVerifiedUnit.getUnit()).isEqualTo("TAB");
    }

    @Test
    void shouldPreserveArchiveSnapshotDateExactLongTermTextAndReloadedFavoriteComponents_whenLifecycleOrRenameRuns() throws Exception {
        var activeWithPriorDate = new io.github.carlos_emr.carlos.commn.model.Drug();
        java.util.Date initialDate = new java.util.Date(1_700_000_000_000L);
        activeWithPriorDate.setArchivedDate(initialDate);
        activeWithPriorDate.archive();
        activeWithPriorDate.getArchivedDate().setTime(1_800_000_000_000L);
        var snapshotField = io.github.carlos_emr.carlos.commn.model.Drug.class.getDeclaredField("archivedDateBeforeLifecycle");
        snapshotField.setAccessible(true);
        assertThat(snapshotField.get(activeWithPriorDate)).isEqualTo(new java.util.Date(1_700_000_000_000L));

        var drugDao = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.DrugDao.class);
        var favoriteDao = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.FavoriteDao.class);
        var originalBeans = new java.util.HashMap<Class<?>, Object>();
        try (var springUtils = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.utility.SpringUtils.class)) {
            originalBeans.put(io.github.carlos_emr.carlos.commn.dao.DrugDao.class, drugDao);
            originalBeans.put(io.github.carlos_emr.carlos.commn.dao.FavoriteDao.class, favoriteDao);
            springUtils.when(() -> io.github.carlos_emr.carlos.utility.SpringUtils.getBean(org.mockito.ArgumentMatchers.any(Class.class)))
                    .thenAnswer(inv -> originalBeans.get(inv.getArgument(0)));

            var exactSource = new io.github.carlos_emr.carlos.commn.model.Drug();
            try {
                var idField = io.github.carlos_emr.carlos.commn.model.Drug.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(exactSource, 91);
                for (String nullableField : List.of("takeMin", "takeMax", "repeat", "customInstructions",
                        "shortTerm", "customNote", "hideFromCpp", "startDateUnknown", "dispenseInternal")) {
                    var field = io.github.carlos_emr.carlos.commn.model.Drug.class.getDeclaredField(nullableField);
                    field.setAccessible(true);
                    field.set(exactSource, null);
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            exactSource.setProviderNo("999998");
            exactSource.setDemographicId(1001);
            exactSource.setBrandName("Synthetic Suspension");
            exactSource.setSpecial("Take as directed");
            exactSource.setExactMedicationDetails(null, "  mL  ", " 250 mg ", "[{\"name\":\"Amoxicillin\",\"strength\":\"250\",\"unit\":\"mg\"}]");
            org.mockito.Mockito.when(drugDao.find(91)).thenReturn(exactSource);
            org.mockito.Mockito.when(drugDao.getMaxPosition(1001)).thenReturn(1);
            var persistedHolder = new java.util.concurrent.atomic.AtomicReference<io.github.carlos_emr.carlos.commn.model.Drug>();
            org.mockito.Mockito.doAnswer(inv -> {
                io.github.carlos_emr.carlos.commn.model.Drug persisted = inv.getArgument(0);
                persisted.setId(92);
                persistedHolder.set(persisted);
                return null;
            }).when(drugDao).persist(org.mockito.ArgumentMatchers.any(io.github.carlos_emr.carlos.commn.model.Drug.class));

            var rxData = new io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData();
            var loadedRx = rxData.getPrescriptionIfPresent(91);
            loadedRx.setLongTerm(true);
            loadedRx.setShortTerm(false);
            assertThat(loadedRx.Save(loadedRx.getScript_no())).isTrue();

            var replacement = persistedHolder.get();
            assertThat(replacement.getLongTerm()).isTrue();
            assertThat(replacement.getQuantity()).isNull();
            assertThat(replacement.getUnitName()).isEqualTo("  mL  ");
            assertThat(replacement.getDosage()).isEqualTo(" 250 mg ");
            assertThat(replacement.getMedicationComponents()).contains("Amoxicillin");
            for (String nullableField : List.of("takeMin", "takeMax", "repeat", "customInstructions",
                    "shortTerm", "customNote", "hideFromCpp", "startDateUnknown", "dispenseInternal")) {
                var field = io.github.carlos_emr.carlos.commn.model.Drug.class.getDeclaredField(nullableField);
                field.setAccessible(true);
                assertThat(field.get(replacement)).as(nullableField).isNull();
            }

            // Explicit non-default values on loaded drugs (including hideFromCpp=true) must also survive Save().
            var explicitSource = new io.github.carlos_emr.carlos.commn.model.Drug();
            explicitSource.setId(95);
            explicitSource.setProviderNo("999998");
            explicitSource.setDemographicId(1001);
            explicitSource.setBrandName("Synthetic Hidden");
            explicitSource.setSpecial("Take 1 tab daily");
            explicitSource.setTakeMin(1f);
            explicitSource.setTakeMax(2f);
            explicitSource.setRepeat(3);
            explicitSource.setCustomInstructions(true);
            explicitSource.setShortTerm(true);
            explicitSource.setCustomNote(true);
            explicitSource.setHideFromCpp(true);
            explicitSource.setStartDateUnknown(true);
            explicitSource.setDispenseInternal(true);
            org.mockito.Mockito.when(drugDao.find(95)).thenReturn(explicitSource);
            var loadedExplicit = rxData.getPrescriptionIfPresent(95);
            loadedExplicit.setLongTerm(true);
            assertThat(loadedExplicit.Save(loadedExplicit.getScript_no())).isTrue();
            var explicitReplacement = persistedHolder.get();
            assertThat(explicitReplacement.getHideFromCpp()).isTrue();
            assertThat(explicitReplacement.getTakeMin()).isEqualTo(1f);
            assertThat(explicitReplacement.getTakeMax()).isEqualTo(2f);
            assertThat(explicitReplacement.getRepeat()).isEqualTo(3);
            assertThat(explicitReplacement.isCustomInstructions()).isTrue();
            assertThat(explicitReplacement.getShortTerm()).isTrue();
            assertThat(explicitReplacement.isCustomNote()).isTrue();
            assertThat(explicitReplacement.getStartDateUnknown()).isTrue();
            assertThat(explicitReplacement.getDispenseInternal()).isTrue();

            // Non-default overrides on a row that previously had SQL NULL columns must not revert to NULL.
            var modifiedFromNulls = new io.github.carlos_emr.carlos.commn.model.Drug();
            modifiedFromNulls.setTakeMin(1f);
            modifiedFromNulls.setTakeMax(1f);
            modifiedFromNulls.setRepeat(1);
            modifiedFromNulls.setCustomInstructions(true);
            modifiedFromNulls.setShortTerm(true);
            modifiedFromNulls.setCustomNote(true);
            modifiedFromNulls.setHideFromCpp(true);
            modifiedFromNulls.setStartDateUnknown(true);
            modifiedFromNulls.setDispenseInternal(true);
            modifiedFromNulls.restoreNullDefaultColumns(exactSource.nullDefaultColumnsMask());
            assertThat(modifiedFromNulls.getTakeMin()).isEqualTo(1f);
            assertThat(modifiedFromNulls.getTakeMax()).isEqualTo(1f);
            assertThat(modifiedFromNulls.getRepeat()).isEqualTo(1);
            assertThat(modifiedFromNulls.isCustomInstructions()).isTrue();
            assertThat(modifiedFromNulls.getShortTerm()).isTrue();
            assertThat(modifiedFromNulls.isCustomNote()).isTrue();
            assertThat(modifiedFromNulls.getHideFromCpp()).isTrue();
            assertThat(modifiedFromNulls.getStartDateUnknown()).isTrue();
            assertThat(modifiedFromNulls.getDispenseInternal()).isTrue();

            var staleRow = new io.github.carlos_emr.carlos.commn.model.Favorite();
            staleRow.setId(42);
            staleRow.setProviderNo("999998");
            staleRow.setName("Old Name");
            staleRow.setMedicationComponents("[{\"name\":\"Old\",\"strength\":\"10\",\"unit\":\"mg\"}]");
            var freshRow = new io.github.carlos_emr.carlos.commn.model.Favorite();
            freshRow.setId(42);
            freshRow.setProviderNo("999998");
            freshRow.setName("Old Name");
            freshRow.setMedicationComponents("[{\"name\":\"Updated\",\"strength\":\"20\",\"unit\":\"mg\"}]");

            org.mockito.Mockito.when(favoriteDao.find(42)).thenReturn(staleRow, freshRow);
            var favDto = rxData.getFavorite(42);
            favDto.setFavoriteName("Renamed Favorite");
            assertThat(favDto.Save()).isTrue();
            assertThat(freshRow.getName()).isEqualTo("Renamed Favorite");
            assertThat(freshRow.getMedicationComponents()).contains("Updated");

            org.mockito.Mockito.when(favoriteDao.createOrFind(org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(inv -> {
                        io.github.carlos_emr.carlos.commn.model.Favorite created = inv.getArgument(0);
                        created.setId(43);
                        return created;
                    });
            assertThat(io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.addToFavorites("999998", "From Rx", exactSource)).isTrue();
            org.mockito.Mockito.verify(favoriteDao).createOrFind(org.mockito.ArgumentMatchers.argThat(
                    created -> created.getMedicationComponents() != null && created.getMedicationComponents().contains("Amoxicillin")));
        }
    }

    private static ObjectMapper restMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.setAnnotationIntrospector(new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                new RestXmlAnnotationIntrospector(mapper.getTypeFactory())));
    }

    private static void assertComplete(MedicationDetails details) {
        assertThat(details.getQuantityText()).isEqualTo(" 7.50 ");
        assertThat(details.getDispenseUnit()).isEqualTo("mL");
        assertThat(details.getDosageText()).isEqualTo("250.0 mg/5 mL + 31.25 mg/5 mL");
        assertThat(details.getComponents()).extracting(MedicationDetails.Component::getName)
                .containsExactly("Ingredient A", "Ingredient B");
        assertThat(details.getComponents()).extracting(MedicationDetails.Component::getStrength)
                .containsExactly("250.0", "31.25");
        assertThat(details.getComponents()).extracting(MedicationDetails.Component::getUnit)
                .containsOnly("mg/5 mL");
    }
}
