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

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsClinicalPersisterUnitTest {
    @TempDir Path directory;
    private final CdsClinicalNormalizationService normalization = new CdsClinicalNormalizationService(new CdsFieldParser());
    private final DrugDao drugs = mock(DrugDao.class);
    private final PartialDateDao dates = mock(PartialDateDao.class);
    private final CdsMedicationPersister medications = new CdsMedicationPersister(drugs, dates, normalization);

    @AfterEach
    void clearTransactionMarker() { TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test
    void shouldKeepSourcePrescribingValues_whenDinIsOnlyRepresentative() throws Exception {
        Drug drug = candidate(Map.of("DrugIdentificationNumber", "01234567", "DrugName", "Source formulation",
                "Strength/Amount", "10/5", "Strength/UnitOfMeasure", "mg/mL", "Dosage", "1/2–1",
                "DosageUnitOfMeasure", "mL", "PrescriptionStatus", "Active"));
        assertThat(drug.getBrandName()).isEqualTo("Source formulation");
        assertThat(drug.getRegionalIdentifier()).isEqualTo("01234567");
        assertThat(drug.getDosage()).isEqualTo("10/5 mg/mL");
        assertThat(drug.getDosageDisplay()).isEqualTo("1/2–1");
        assertThat(drug.getSpecial()).contains("1/2–1", "mL");
        assertThat(drug.getRxDate()).isNull();
        assertThat(drug.getEndDate()).isNull();
        assertThat(drug.isCurrent()).isTrue();
        verifyNoInteractions(drugs, dates);
    }

    @Test
    void shouldKeepPrivateNotesOffPrescriptionOutput_whenPharmacyInstructionsAreAlsoPresent() throws Exception {
        Drug drug = candidate(Map.of("Notes", "PRIVATE-CLINICIAN-SENTINEL",
                "PrescriptionInstructions", "PHARMACY-SENTINEL", "Dosage", "1", "Frequency", "OD"));
        drug.setId(71);
        var prescription = new RxPrescriptionData().toPrescription(drug, 42);
        String rendered = RxPrescriptionData.getFullOutLine(
                prescription.getSpecial() + "\n" + prescription.getSpecialInstruction());
        assertThat(prescription.getComment()).isEqualTo("PRIVATE-CLINICIAN-SENTINEL");
        assertThat(rendered).contains("PHARMACY-SENTINEL").doesNotContain("PRIVATE-CLINICIAN-SENTINEL");
    }

    @Test
    void shouldRetainUnknownIndicatorsAndCurrentness_whenSourceDoesNotAssertThem() throws Exception {
        Drug drug = candidate(Map.of());
        assertThat(drug.getCdsCurrentness()).isEqualTo("UNKNOWN");
        assertThat(drug.isArchived()).isFalse();
        assertThat(drug.isCurrent()).isFalse();
        assertThat(drug.getNoSubs()).isNull();
        assertThat(drug.getNonAuthoritative()).isNull();
        assertThat(drug.getLongTerm()).isNull();
        assertThat(drug.getPatientCompliance()).isNull();
        assertThat(drug.getPastMed()).isNull();
        drug.setId(72);
        var prescription = new RxPrescriptionData().toPrescription(drug, 42);
        assertThat(prescription.getCdsSubstitutionNotAllowed()).isNull();
        assertThat(prescription.getCdsNonAuthoritative()).isNull();
        assertThat(prescription.isCurrent()).isFalse();
        assertThatThrownBy(prescription::requireSafeImportedRenewal)
                .hasMessageContaining("CDS_MEDICATION_RECONCILIATION_REQUIRED");
        Drug roundTrip = new Drug(prescription);
        assertThat(roundTrip.getNoSubs()).isNull();
        assertThat(roundTrip.getNonAuthoritative()).isNull();
    }

    @Test
    void shouldHonorLaterClinicalArchive_whenImportedStatusWasActive() throws Exception {
        Drug drug = candidate(Map.of("PrescriptionStatus", "Active"));
        assertThat(drug.isCurrent()).isTrue();
        drug.setArchived(true);
        assertThat(drug.isCurrent()).isFalse();
    }

    @Test
    void shouldRequireClinicalReviewBeforeRenewal_whenImportedDoseIsUnstructured() throws Exception {
        Drug drug = candidate(Map.of("PrescriptionStatus", "Active", "Dosage", "1/2–1",
                "SubstitutionNotAllowed", "N", "NonAuthoritativeIndicator", "N"));
        drug.setId(74);
        var data = new RxPrescriptionData();
        var prescription = data.toPrescription(drug, 42);
        assertThatThrownBy(() -> data.newPrescription("999998", 42, prescription))
                .hasMessageContaining("CDS_MEDICATION_RECONCILIATION_REQUIRED");
        assertThat(drug.getDosageDisplay()).isEqualTo("1/2–1");
    }

    @Test
    void shouldRetainSafeStructuredDoseThroughRenewal_whenSourceDecisionsAreKnown() throws Exception {
        Drug drug = candidate(Map.of("PrescriptionStatus", "Active", "Dosage", "1.25",
                "SubstitutionNotAllowed", "N", "NonAuthoritativeIndicator", "N",
                "Notes", "PRIVATE-OLD-HISTORY"));
        drug.setId(75);
        var data = new RxPrescriptionData();
        var old = data.toPrescription(drug, 42);
        var renewed = data.newPrescription("999998", 42, old);
        assertThat(renewed.getTakeMin()).isEqualTo(1.25f);
        assertThat(renewed.getTakeMax()).isEqualTo(1.25f);
        assertThat(renewed.getComment()).isNull();
        assertThat(old.getComment()).isEqualTo("PRIVATE-OLD-HISTORY");
        assertThat(drug.getCdsCurrentness()).isEqualTo("CURRENT");
    }

    @Test
    void shouldKeepSourceAuthorSeparate_whenOnlyImportingProviderIsKnown() throws Exception {
        Drug drug = candidate(Map.of("PrescribedBy/Name/FirstName", "Synthetic",
                "PrescribedBy/Name/LastName", "Prescriber", "PrescribedBy/OHIPPhysicianId", "123456"));
        assertThat(drug.getProviderNo()).isEmpty();
        assertThat(drug.getOutsideProviderName()).isEqualTo("Synthetic Prescriber");
        assertThat(drug.getOutsideProviderOhip()).isEqualTo("123456");
    }

    @Test
    void shouldRefuseWritesOutsideUnit_whenCalledWithoutSpringProxy() throws Exception {
        var source = normalization.medication(medication(Map.of()));
        assertThatThrownBy(() -> medications.write(42, "999998", source))
                .hasMessageContaining("requires an owning transaction");
        verifyNoInteractions(drugs, dates);
    }

    @Test
    void shouldPropagateRequiredWriteFailure_whenNativeDaoCannotPersist() throws Exception {
        var source = normalization.medication(medication(Map.of()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        doThrow(new IllegalStateException("synthetic database failure")).when(drugs).persist(any());
        assertThatThrownBy(() -> medications.write(42, "999998", source))
                .hasMessage("synthetic database failure");
        verifyNoInteractions(dates);
    }

    @Test
    void shouldPreserveAdverseReactionWithoutSubtype_whenSourceOnlySaysAr() throws Exception {
        var source = normalization.allergy(CdsClinicalFixture.record(directory,
                "AllergiesAndAdverseReactions", Map.of("OffendingAgentDescription", "Synthetic agent",
                        "ReactionType", "AR", "Severity", "NO", "PropertyOfOffendingAgent", "UK")));
        var allergies = new CdsAllergyPersister(mock(AllergyDao.class), dates, normalization);
        var allergy = allergies.candidate(42, source);
        assertThat(allergy.getReactionType()).isEqualTo("AR");
        assertThat(allergy.isNonDrug()).isNull();
        assertThat(allergy.getSeverityOfReactionDesc()).isEqualTo("No Reaction");
        assertThat(allergy.getEntryDate()).isNull();
        assertThat(allergy.getStartDate()).isNull();
        assertThat(allergy.getProviderNo()).isNull();
    }

    @Test
    void shouldRetainUndatedAdministrationAndNativeManufacturer_whenSourceDateIsAbsent() throws Exception {
        var source = normalization.immunization(CdsClinicalFixture.record(directory,
                "Immunizations", Map.of("ImmunizationName", "Synthetic vaccine",
                        "RefusedFlag/ynIndicatorsimple", "N", "Manufacturer", "Synthetic manufacturer")));
        PreventionDao preventions = mock(PreventionDao.class);
        PreventionExtDao extensions = mock(PreventionExtDao.class);
        doAnswer(call -> {
            Prevention written = call.getArgument(0);
            ReflectionTestUtils.setField(written, "id", 73);
            return null;
        }).when(preventions).persist(any());
        var writer = new CdsImmunizationPersister(preventions, extensions, dates, normalization, "");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThat(writer.write(42, "999998", source)).containsEntry("preventions", "73");
        ArgumentCaptor<Prevention> saved = ArgumentCaptor.forClass(Prevention.class);
        verify(preventions).persist(saved.capture());
        Prevention prevention = saved.getValue();
        assertThat(prevention.isRefused()).isFalse();
        assertThat(prevention.getPreventionDate()).isNull();
        assertThat(prevention.getProviderNo()).isEmpty();
        assertThat(prevention.getCreatorProviderNo()).isEqualTo("999998");
        ArgumentCaptor<PreventionExt> extra = ArgumentCaptor.forClass(PreventionExt.class);
        verify(extensions, atLeastOnce()).persist(extra.capture());
        assertThat(extra.getAllValues()).anySatisfy(ext -> {
            assertThat(ext.getkeyval()).isEqualTo("manufacture");
            assertThat(ext.getVal()).isEqualTo("Synthetic manufacturer");
        });
    }

    private Drug candidate(Map<String, String> extra) throws Exception {
        return medications.candidate(42, normalization.medication(medication(extra)));
    }

    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto medication(Map<String, String> extra)
            throws Exception {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("DrugName", "Synthetic medicine");
        source.putAll(extra);
        return CdsClinicalFixture.record(directory, "MedicationsAndTreatments", source);
    }
}
