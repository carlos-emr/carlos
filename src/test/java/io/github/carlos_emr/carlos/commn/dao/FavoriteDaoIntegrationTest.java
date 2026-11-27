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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.builders.ProviderTestBuilder;
import io.github.carlos_emr.carlos.commn.model.Favorite;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link FavoriteDao} covering persist, findByProviderNo,
 * and findByEverything operations.
 *
 * @since 2026-03-07
 * @see FavoriteDao
 */
@DisplayName("Favorite Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class FavoriteDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FavoriteDao dao;

    @BeforeEach
    void persistFavoriteOwner() {
        hibernateTemplate.save(ProviderTestBuilder.aProvider().withProviderNo("FAVFIX").build());
        hibernateTemplate.flush();
    }

    /**
     * Helper to create a Favorite with specific provider number and name.
     */
    private Favorite createFavorite(String providerNo, String name) throws Exception {
        Favorite entity = new Favorite();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setProviderNo(providerNo);
        entity.setName(name);
        dao.persist(entity);
        return entity;
    }

    @Test
    @Tag("update")
    void shouldRetainSelectedFavorite_whenEditedFieldsMatchAnotherFavorite() {
        var first = favoriteForSave("Original");
        var duplicate = favoriteForSave("Replacement");
        first.setDispenseInternal(true);
        assertThat(first.Save()).isTrue();
        assertThat(duplicate.Save()).isTrue();
        Integer firstId = first.getFavoriteId();
        Integer duplicateId = duplicate.getFavoriteId();
        first.setFavoriteName("Replacement");
        // Editing one favorite must not overwrite the other row's clinical metadata.
        first.setAtcCode("EDITED");

        assertThat(first.Save()).isTrue();

        assertThat(dao.find(firstId).getName()).isEqualTo("Replacement");
        assertThat(dao.find(firstId).getAtc()).isEqualTo("EDITED");
        assertThat(dao.find(firstId).isDispenseInternal()).isTrue();
        assertThat(dao.find(duplicateId).isDispenseInternal()).isFalse();
        assertThat(dao.find(duplicateId).getAtc()).isEqualTo("ORIGINAL");
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(2);
    }

    @Test
    void shouldPersistAndDeduplicateFavorite_withoutInstructions() {
        var favorite = favoriteForSave("No instructions");
        favorite.setSpecial(null);
        assertThat(favorite.Save()).isTrue();
        hibernateTemplate.flush();
        hibernateTemplate.clear();
        assertThat(dao.find(favorite.getFavoriteId()).getSpecial()).isEmpty();

        var duplicate = favoriteForSave("No instructions");
        duplicate.setSpecial("");
        assertThat(duplicate.Save()).isTrue();
        assertThat(duplicate.getFavoriteId()).isEqualTo(favorite.getFavoriteId());
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(1);
    }

    @Test
    void shouldMatchDispensingMode_whenCreatingOtherwiseIdenticalFavorites() {
        var external = favoriteForSave("Dispensing");
        external.setDispenseInternal(null); // Legacy absent flag has the same meaning as false.
        assertThat(external.Save()).isTrue();
        var internal = favoriteForSave("Dispensing");
        internal.setDispenseInternal(true);
        assertThat(internal.Save()).isTrue();
        assertThat(internal.getFavoriteId()).isNotEqualTo(external.getFavoriteId());
        var sameExternal = favoriteForSave("Dispensing");
        sameExternal.setDispenseInternal(false);
        assertThat(sameExternal.Save()).isTrue();
        var sameInternal = favoriteForSave("Dispensing");
        sameInternal.setDispenseInternal(true);
        assertThat(sameInternal.Save()).isTrue();

        assertThat(sameExternal.getFavoriteId()).isEqualTo(external.getFavoriteId());
        assertThat(sameInternal.getFavoriteId()).isEqualTo(internal.getFavoriteId());
        assertThat(dao.find(external.getFavoriteId()).isDispenseInternal()).isFalse();
        assertThat(dao.find(internal.getFavoriteId()).isDispenseInternal()).isTrue();
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "atc", "regionalIdentifier", "unit", "method", "route", "drugForm", "dosage"})
    void shouldKeepDistinctClinicalValues_whenCreatingFavorites(String field) {
        var original = favoriteForSave("Clinical metadata");
        original.setSpecial("Patient's synthetic instructions");
        assertThat(original.Save()).isTrue();
        var changed = favoriteForSave("Clinical metadata");
        changed.setSpecial("Patient's synthetic instructions");
        setClinicalField(changed, field);
        assertThat(changed.Save()).isTrue();
        assertThat(changed.getFavoriteId()).isNotEqualTo(original.getFavoriteId());
        var repeated = favoriteForSave("Clinical metadata");
        repeated.setSpecial("Patient's synthetic instructions");
        setClinicalField(repeated, field);
        assertThat(repeated.Save()).isTrue();
        assertThat(repeated.getFavoriteId()).isEqualTo(changed.getFavoriteId());
        var repeatedOriginal = favoriteForSave("Clinical metadata");
        repeatedOriginal.setSpecial("Patient's synthetic instructions");
        assertThat(repeatedOriginal.Save()).isTrue();
        assertThat(repeatedOriginal.getFavoriteId()).isEqualTo(original.getFavoriteId());
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(2);
    }

    @Test
    @Tag("create")
    @DisplayName("should create distinct favorite when opt-in medicationDetails explicitly clears components")
    void shouldNotMatchFavoriteWithComponents_whenOptInDetailsExplicitlyClearsComponents() {
        Favorite withComponents = new Favorite();
        withComponents.setProviderNo("FAVFIX");
        withComponents.setName("Combo");
        withComponents.setBn("ComboBrand");
        withComponents.setGn("ComboGeneric 500mg/125mg");
        withComponents.setDosage("500mg/125mg");
        withComponents.setSpecial("Take 1 tab BID");
        withComponents.setMedicationComponents("[{\"name\":\"Amoxicillin\",\"strength\":\"500\",\"unit\":\"mg\"}]");
        withComponents.setExactMedicationDetails(true);
        Favorite persisted = dao.createOrFind(withComponents);

        // Omitted medicationDetails (legacy client) still matches the existing favorite.
        Favorite legacyCandidate = new Favorite();
        legacyCandidate.setProviderNo("FAVFIX");
        legacyCandidate.setName("Combo");
        legacyCandidate.setBn("ComboBrand");
        legacyCandidate.setGn("ComboGeneric 500mg/125mg");
        legacyCandidate.setDosage("500mg/125mg");
        legacyCandidate.setSpecial("Take 1 tab BID");
        legacyCandidate.setMedicationComponents(null);
        legacyCandidate.setExactMedicationDetails(false);
        assertThat(dao.createOrFind(legacyCandidate).getId()).isEqualTo(persisted.getId());

        // Explicit opt-in medicationDetails with cleared null components creates a distinct row.
        Favorite clearedCandidate = new Favorite();
        clearedCandidate.setProviderNo("FAVFIX");
        clearedCandidate.setName("Combo");
        clearedCandidate.setBn("ComboBrand");
        clearedCandidate.setGn("ComboGeneric 500mg/125mg");
        clearedCandidate.setDosage("500mg/125mg");
        clearedCandidate.setSpecial("Take 1 tab BID");
        clearedCandidate.setMedicationComponents(null);
        clearedCandidate.setExactMedicationDetails(true);
        Favorite cleared = dao.createOrFind(clearedCandidate);

        assertThat(cleared.getId()).isNotEqualTo(persisted.getId());
        assertThat(cleared.getMedicationComponents()).isNull();
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(2);
    }

    @Test
    void shouldReuseFavorite_whenOptionalClinicalMetadataIsNull() {
        var original = favoriteForSave("Legacy null metadata");
        original.setRegionalIdentifier(null);
        original.setDrugForm(null);
        assertThat(original.Save()).isTrue();
        var repeated = favoriteForSave("Legacy null metadata");
        repeated.setRegionalIdentifier(null);
        repeated.setDrugForm(null);
        assertThat(repeated.Save()).isTrue();

        assertThat(repeated.getFavoriteId()).isEqualTo(original.getFavoriteId());
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(1);
    }

    @Test
    void reusesEquivalentStrengthSpellingAndKeepsRecordedComponents() {
        var original = favoriteForSave("Combination");
        original.setGN("Synthetic 250.0mg");
        original.setDosage("250.0mg");
        assertThat(original.Save()).isTrue();

        var equivalent = favoriteForSave("Combination");
        equivalent.setGN("Synthetic 250mg");
        equivalent.setDosage("250mg");
        assertThat(equivalent.Save()).isTrue();
        assertThat(equivalent.getFavoriteId()).isEqualTo(original.getFavoriteId());

        String components = "[{\"name\":\"Synthetic\",\"strength\":\"250\",\"unit\":\"mg\"}]";
        var documented = favoriteForSave("Combination");
        documented.setGN("Synthetic 250mg");
        documented.setDosage("250mg");
        documented.setMedicationComponents(components);
        assertThat(documented.Save()).isTrue();
        assertThat(documented.getFavoriteId()).isNotEqualTo(original.getFavoriteId());
        assertThat(dao.find(documented.getFavoriteId()).getMedicationComponents()).isEqualTo(components);
        assertThat(dao.find(original.getFavoriteId()).getMedicationComponents()).isNull();
        assertThat(dao.find(original.getFavoriteId()).getDosage()).isEqualTo("250.0mg");

        var repeated = favoriteForSave("Combination");
        repeated.setGN("Synthetic 250mg");
        repeated.setDosage("250mg");
        repeated.setMedicationComponents(components);
        assertThat(repeated.Save()).isTrue();
        assertThat(repeated.getFavoriteId()).isEqualTo(documented.getFavoriteId());
        assertThat(dao.findByProviderNo("FAVFIX")).hasSize(2);

        Favorite legacyNulls = new Favorite();
        legacyNulls.setProviderNo("FAVFIX");
        legacyNulls.setName("LegacyNullBounds");
        legacyNulls.setBn("Synthetic brand");
        legacyNulls.setGcnSeqno("0");
        legacyNulls.setCustomName("Synthetic drug");
        org.springframework.test.util.ReflectionTestUtils.setField(legacyNulls, "takeMin", null);
        org.springframework.test.util.ReflectionTestUtils.setField(legacyNulls, "takeMax", null);
        legacyNulls.setFrequencyCode("OD");
        legacyNulls.setDuration("7");
        legacyNulls.setDurationUnit("D");
        legacyNulls.setQuantity("7");
        org.springframework.test.util.ReflectionTestUtils.setField(legacyNulls, "repeat", null);
        legacyNulls.setNosubs(false);
        legacyNulls.setPrn(false);
        legacyNulls.setSpecial("Legacy PRN text");
        legacyNulls.setGn("Synthetic generic");
        legacyNulls.setAtc("ORIGINAL");
        legacyNulls.setRegionalIdentifier("");
        legacyNulls.setUnit("");
        legacyNulls.setUnitName("tablet");
        legacyNulls.setMethod("");
        legacyNulls.setRoute("");
        legacyNulls.setDrugForm("");
        org.springframework.test.util.ReflectionTestUtils.setField(legacyNulls, "customInstructions", null);
        legacyNulls.setDosage("");
        dao.persist(legacyNulls);
        hibernateTemplate.flush();
        hibernateTemplate.clear();

        var resavedLegacy = new io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Favorite(
                0, "FAVFIX", "LegacyNullBounds", "Synthetic brand", "0", "Synthetic drug", 0, 0, "OD", "7", "D",
                "7", 0, false, false, "Legacy PRN text", "Synthetic generic", "ORIGINAL",
                "", "", "tablet", "", "", "", false, "");
        assertThat(resavedLegacy.Save()).isTrue();
        assertThat(resavedLegacy.getFavoriteId()).isEqualTo(legacyNulls.getId());
    }

    private void setClinicalField(io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Favorite favorite, String field) {
        switch (field) {
            case "atc" -> favorite.setAtcCode("CHANGED");
            case "regionalIdentifier" -> favorite.setRegionalIdentifier("12345678");
            case "unit" -> favorite.setUnit("mg");
            case "method" -> favorite.setMethod("Take");
            case "route" -> favorite.setRoute("PO");
            case "drugForm" -> favorite.setDrugForm("Capsule");
            case "dosage" -> favorite.setDosage("20 mg");
            default -> throw new IllegalArgumentException(field);
        }
    }

    private io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Favorite favoriteForSave(String name) {
        return new io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Favorite(
                0, "FAVFIX", name, "Synthetic brand", "0", "Synthetic drug", 1, 1, "OD", "7", "D",
                "7", 0, false, false, "Synthetic instructions", "Synthetic generic", "ORIGINAL",
                "", "", "tablet", "", "", "", false, "");
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist entity and assign generated ID")
        void shouldPersistEntity_withGeneratedId() throws Exception {
            Favorite entity = new Favorite();
            EntityDataGenerator.generateTestDataForModelClass(entity);
            dao.persist(entity);

            assertThat(entity.getId()).isPositive();
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @Test
        @Tag("read")
        @DisplayName("should find favorites by provider number")
        void shouldFindFavorites_byProviderNo() throws Exception {
            createFavorite("P001", "Aspirin");
            createFavorite("P001", "Ibuprofen");
            createFavorite("P002", "Tylenol");
            hibernateTemplate.flush();

            List<Favorite> result = dao.findByProviderNo("P001");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(f -> f.getProviderNo().equals("P001"));
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when provider has no favorites")
        void shouldReturnEmptyList_whenProviderHasNoFavorites() throws Exception {
            List<Favorite> result = dao.findByProviderNo("NONEXISTENT");

            assertThat(result).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should find exact match with findByEverything")
        void shouldFindExactMatch_withFindByEverything() throws Exception {
            Favorite fav = new Favorite();
            fav.setProviderNo("P100");
            fav.setName("TestFav");
            fav.setBn("BrandX");
            fav.setGcnSeqno("GCN001");
            fav.setCustomName("Custom1");
            fav.setTakeMin(1.0f);
            fav.setTakeMax(2.0f);
            fav.setFrequencyCode("BID");
            fav.setDuration("7");
            fav.setDurationUnit("days");
            fav.setQuantity("30");
            fav.setRepeat(3);
            fav.setNosubs(false);
            fav.setPrn(false);
            fav.setSpecial("Take with food");
            fav.setGn("GenericX");
            fav.setUnitName("mg");
            fav.setCustomInstructions(false);
            dao.persist(fav);
            hibernateTemplate.flush();

            Favorite found = dao.findByEverything(
                    "P100", "TestFav", "BrandX", "GCN001", "Custom1",
                    1.0f, 2.0f, "BID", "7", "days", "30", 3,
                    false, false, "Take with food", "GenericX", "mg", false);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(fav.getId());
            assertThat(found.getProviderNo()).isEqualTo("P100");
        }

        @Test
        @Tag("read")
        @DisplayName("should return null from findByEverything when no exact match")
        void shouldReturnNull_whenNoExactMatchInFindByEverything() throws Exception {
            Favorite found = dao.findByEverything(
                    "NONE", "NONE", "NONE", "NONE", "NONE",
                    0, 0, "NONE", "NONE", "NONE", "NONE", 0,
                    false, false, "NONE", "NONE", "NONE", false);

            assertThat(found).isNull();
        }
    }
}
