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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated("Clinical temporal projections use the actual default reader timezone")
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
        assertThat(roundTrip.getCdsProjectionIncomplete()).isTrue();
    }

    @Test
    void shouldRetainOversizedStrengthAsExplicitSourceReference_withRenewalBlocked() throws Exception {
        Drug drug = candidate(Map.of("Strength/Amount", "X".repeat(100000), "Strength/UnitOfMeasure", "mg",
                "Dosage", "1", "PrescriptionStatus", "Active", "SubstitutionNotAllowed", "N", "NonAuthoritativeIndicator", "N"));
        assertThat(drug.getCdsProjectionIncomplete()).isTrue();
        assertThat(drug.getDosage()).isEqualTo("[Source strength: see imported medication details]");
        assertThat(drug.getSpecial()).contains("Source strength: see imported medication details").doesNotContain("XXXXXXXXXX");
        drug.setId(73);
        var bridge = new RxPrescriptionData().toPrescription(drug, 42);
        assertThat(bridge.getCdsProjectionIncomplete()).isTrue();
        assertThatThrownBy(bridge::requireSafeImportedRenewal).hasMessageContaining("CDS_MEDICATION_RECONCILIATION_REQUIRED");
        assertThat(new Drug(bridge).getCdsProjectionIncomplete()).isTrue();
    }

    @Test
    void shouldAllowRenewal_onlyForExplicitlyCompleteProjection() throws Exception {
        LinkedHashMap<String, String> source = new LinkedHashMap<>();
        source.put("Strength/Amount", "10"); source.put("Strength/UnitOfMeasure", "mg");
        source.put("Dosage", "1"); source.put("DosageUnitOfMeasure", "tablet"); source.put("Route", "oral");
        source.put("Frequency", "OD"); source.put("Quantity", "30"); source.put("NumberOfRefills", "0");
        source.put("PrescriptionStatus", "Active"); source.put("SubstitutionNotAllowed", "N"); source.put("NonAuthoritativeIndicator", "N");
        Drug drug = candidate(source); drug.setId(74);
        assertThat(drug.getCdsProjectionIncomplete()).isFalse();
        var bridge = new RxPrescriptionData().toPrescription(drug, 42);
        assertThatCode(bridge::requireSafeImportedRenewal).doesNotThrowAnyException();
        bridge.setCdsProjectionIncomplete(null);
        assertThatThrownBy(bridge::requireSafeImportedRenewal).hasMessageContaining("CDS_MEDICATION_RECONCILIATION_REQUIRED");
        bridge.setCdsCurrentness(null);
        assertThatCode(bridge::requireSafeImportedRenewal).doesNotThrowAnyException();
    }

    @Test
    void shouldProjectTwoRepresentableStrengthParts_withoutArbitraryCombinedLimit() throws Exception {
        Drug drug = candidate(Map.of("Strength/Amount", "A".repeat(32768), "Strength/UnitOfMeasure", "U".repeat(32768)));
        assertThat(drug.getDosage()).hasSize(65537);
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
        Drug drug = candidate(Map.ofEntries(Map.entry("PrescriptionStatus", "Active"), Map.entry("Dosage", "1.25"),
                Map.entry("SubstitutionNotAllowed", "N"), Map.entry("NonAuthoritativeIndicator", "N"),
                Map.entry("Notes", "PRIVATE-OLD-HISTORY"), Map.entry("Strength/Amount", "10"),
                Map.entry("Strength/UnitOfMeasure", "mg"), Map.entry("DosageUnitOfMeasure", "tablet"),
                Map.entry("Route", "oral"), Map.entry("Frequency", "OD"), Map.entry("Quantity", "30"), Map.entry("NumberOfRefills", "0")));
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
    void shouldUseOnlyExplicitResolvedSourceAuthor_withoutChangingSourceIdentityOrAuthority() throws Exception {
        var source = normalization.medication(medication(Map.of("PrescribedBy/Name/FirstName", "Source",
                "PrescribedBy/Name/LastName", "Author", "PrescribedBy/OHIPPhysicianId", "001234")));
        var match = new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto("T002",
                io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto.Outcome.MATCHED_IDENTIFIERS);
        Drug drug = medications.candidate(42, source, match);
        assertThat(drug.getProviderNo()).isEqualTo("T002");
        assertThat(drug.getOutsideProviderName()).isEqualTo("Source Author");
        assertThat(drug.getOutsideProviderOhip()).isEqualTo("001234");
        assertThat(drug.getNonAuthoritative()).isNull();
        assertThat(drug.getScriptNo()).isNull();
        assertThat(source.prescriberFirstName()).isEqualTo("Source");
        verifyNoInteractions(drugs, dates);
    }

    @Test
    void shouldRetainBothMaximumSourceNameParts_withTheirSeparator() throws Exception {
        Drug source = candidate(Map.of("PrescribedBy/Name/FirstName", "F".repeat(50),
                "PrescribedBy/Name/LastName", "L".repeat(50)));
        assertThat(source.getOutsideProviderName()).hasSize(101);
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

    @Test void shouldRefuseUnrepresentableNativeDatesInCompletePreflight_beforeAnyClinicalDaoWrite() throws Exception {
        withTemporalZone(() -> {
            var parser = new CdsFieldParser();
            var preventions = mock(PreventionDao.class);
            var extensions = mock(PreventionExtDao.class);
            var writer = new CdsImmunizationPersister(preventions, extensions, dates, normalization, "America/Toronto");
            var service = new CdsImportNativeService(parser, normalization, medications,
                    new CdsAllergyPersister(mock(AllergyDao.class), dates, normalization), writer,
                    mock(CdsDemographicPersister.class), mock(CdsHistoryPersister.class),
                    mock(CdsAppointmentPersister.class), mock(CdsNativePreparationService.class),
                    mock(CdsProviderResolver.class), mock(CdsSourceFieldService.class),
                    mock(CdsImportAccessService.class), mock(CdsImportLedgerService.class),
                    mock(CdsTargetReceiptService.class), java.util.List.of());
            for (String[] source : java.util.List.of(
                    new String[] {"FullDate", "1582-10-10", "CDS_NATIVE_DATE_REQUIRES_REVIEW"},
                    new String[] {"FullDateTime", "2020-11-01T01:30:00", "CDS_LOCAL_TIME_REQUIRES_REVIEW"},
                    new String[] {"FullDateTime", "2020-11-01T01:30:00-04:00", "CDS_NATIVE_TIMESTAMP_REQUIRES_REVIEW"})) {
                var record = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                        "ImmunizationName", "Synthetic vaccine", "RefusedFlag/ynIndicatorsimple", "N",
                        "Date/" + source[0], source[1]));
                var error = catchThrowableOfType(() -> service.validate(record, null),
                        io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException.class);
                assertThat(error.code()).isEqualTo(source[2]);
                assertThat(error.field()).isEqualTo("Date");
                assertThat(normalization.immunization(record).date().original()).isEqualTo(source[1]);
            }
            var medication = medication(Map.of("PrescriptionWrittenDate/FullDate", "1582-10-10"));
            var error = catchThrowableOfType(() -> service.validate(medication, null),
                    io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException.class);
            assertThat(error.code()).isEqualTo("CDS_NATIVE_DATE_REQUIRES_REVIEW");
            assertThat(error.field()).isEqualTo("PrescriptionWrittenDate");
            verifyNoInteractions(drugs, dates, preventions, extensions);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        });
    }

    @Test void shouldKeepCalendarOnlyHistoricalImmunizationAsDate_withCompleteOriginal() throws Exception {
        withTemporalZone(() -> {
            var preventions = mock(PreventionDao.class);
            var extensions = mock(PreventionExtDao.class);
            var writer = new CdsImmunizationPersister(preventions, extensions, dates, normalization, "America/Toronto");
            var source = normalization.immunization(CdsClinicalFixture.record(directory, "Immunizations",
                    Map.of("ImmunizationName", "Synthetic vaccine", "RefusedFlag/ynIndicatorsimple", "N",
                            "Date/FullDate", "1500-01-01")));
            writer.validate(source);
            var candidate = writer.candidate(42, "999998", source);
            assertThat(io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO
                    .from(candidate.getPreventionDate(), null).getOriginalValue()).isEqualTo("1500-01-01");
            assertThat(source.date().original()).isEqualTo("1500-01-01");
            assertThat(source.date().precision()).isEqualTo(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision.FULL_DATE);
            verifyNoInteractions(drugs, dates, preventions, extensions);
        });
    }

    @Test void shouldFloorFractionsInImmunizationDatetimeZeroProjection_withoutMovingClinicalDay() throws Exception {
        withTemporalZone(() -> {
            var preventions = mock(PreventionDao.class);
            var extensions = mock(PreventionExtDao.class);
            var writer = new CdsImmunizationPersister(preventions, extensions, dates, normalization, "America/Toronto");
            String lexical = "2020-12-31T23:59:59.999999-05:00";
            var source = normalization.immunization(CdsClinicalFixture.record(directory, "Immunizations",
                    Map.of("ImmunizationName", "Synthetic vaccine", "RefusedFlag/ynIndicatorsimple", "N",
                            "Date/FullDateTime", lexical)));
            writer.validate(source);
            var candidate = writer.candidate(42, "999998", source);
            assertThat(candidate.getPreventionDate().toInstant()).isEqualTo(java.time.Instant.parse("2021-01-01T04:59:59Z"));
            assertThat(io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO
                    .from(candidate.getPreventionDate(), null).getOriginalValue()).isEqualTo("2020-12-31");
            assertThat(source.date().original()).isEqualTo(lexical);
            assertThat(source.date().offsetDateTime().getNano()).isEqualTo(999999000);
            verifyNoInteractions(drugs, dates, preventions, extensions);
        });
    }

    @Test void shouldReportAdjustedCalendarAnchorsAndProjectedSubseconds_fromOwningUnit() throws Exception {
        withTemporalZone(() -> {
            for (String[] choice : java.util.List.of(
                    new String[] {"FullDate", "1500-01-01", "CDS_CALENDAR_STORAGE_ANCHOR_NORMALIZED"},
                    new String[] {"FullDateTime", "2020-12-31T23:59:59.999999-05:00", "CDS_IMMUNIZATION_SUBSECOND_PRECISION_RETAINED_IN_SOURCE"})) {
                var parser = new CdsFieldParser();
                var record = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                        "ImmunizationName", "Synthetic vaccine", "RefusedFlag/ynIndicatorsimple", "N", "Date/" + choice[0], choice[1]));
                var source = normalization.immunization(record);
                var preventions = mock(PreventionDao.class);var extensions = mock(PreventionExtDao.class);
                doAnswer(call -> { Prevention written = call.getArgument(0); ReflectionTestUtils.setField(written, "id", 73); return null; }).when(preventions).persist(any());
                var writer = new CdsImmunizationPersister(preventions, extensions, dates, normalization, "America/Toronto");
                var preparation = mock(CdsNativePreparationService.class);var ledger = mock(CdsImportLedgerService.class);
                var receipts = mock(CdsTargetReceiptService.class);
                var service = new CdsImportNativeService(parser, normalization, medications,
                        new CdsAllergyPersister(mock(AllergyDao.class), dates, normalization), writer,
                        mock(CdsDemographicPersister.class), mock(CdsHistoryPersister.class), mock(CdsAppointmentPersister.class),
                        preparation, mock(CdsProviderResolver.class), mock(CdsSourceFieldService.class),
                        mock(CdsImportAccessService.class), ledger, receipts, java.util.List.of());
                var command = mock(io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand.class);
                when(command.providerNo()).thenReturn("999998");
                var unit = new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportUnitDto(
                        "patient", "unit", "Immunizations", 1, record.sha256(), "protected-spool", "EXPECTED", null, null);
                when(preparation.load("run", 1, "unit", record.effectiveSha256(), command,
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto.class)).thenReturn(source);
                var entity = mock(jakarta.persistence.EntityManager.class);
                @SuppressWarnings("unchecked") jakarta.persistence.TypedQuery<Integer> query = mock(jakarta.persistence.TypedQuery.class, RETURNS_SELF);
                when(entity.createQuery(anyString(), eq(Integer.class))).thenReturn(query);when(query.getSingleResult()).thenReturn(42);
                ReflectionTestUtils.setField(service, "entityManager", entity);
                var receipt = new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto("{\"preventions\":\"73\"}", "a".repeat(64), false);
                when(receipts.capture(anyMap(), eq("run"), eq("unit"))).thenReturn(receipt);
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try (var unitBoundary = mockStatic(CdsImportUnitService.class)) {
                    assertThat(service.write("run", 1, unit, record, command, null, null, "source.xml")).isSameAs(receipt);
                } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
                verify(ledger).diagnostic(eq("run"), eq(1L), eq("patient"), eq("unit"), eq("WARNING"),
                        eq(choice[2]), eq("NORMALIZATION"), eq("Date"), anyString(), isNull(), eq(false));
                var extra = ArgumentCaptor.forClass(PreventionExt.class);verify(extensions, atLeastOnce()).persist(extra.capture());
                assertThat(extra.getAllValues()).anySatisfy(value -> {
                    assertThat(value.getkeyval()).isEqualTo("cdsDateOriginal");assertThat(value.getVal()).isEqualTo(choice[1]);
                });
            }
        });
    }

    @Nested
    class NativeReceiptMetadataTimeout {
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement metadataQuery = mock(PreparedStatement.class);
        private final PreparedStatement digestQuery = mock(PreparedStatement.class);
        private final ResultSet metadataRows = mock(ResultSet.class);
        private final ResultSet digestRows = mock(ResultSet.class);
        private final ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        private final AtomicBoolean timeoutConfigured = new AtomicBoolean();

        @BeforeEach void jdbcFixture() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(metadataQuery, digestQuery);
            doAnswer(call -> { timeoutConfigured.set(true); return null; }).when(metadataQuery).setQueryTimeout(30);
            when(metadataQuery.executeQuery()).thenAnswer(call -> {
                assertThat(timeoutConfigured.get()).as("metadata deadline configured before driver execution").isTrue();
                return metadataRows;
            });
            when(metadataRows.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(1);
            when(metadata.getColumnName(1)).thenReturn("demographic_no");
            when(metadata.getColumnTypeName(1)).thenReturn("INT");
            when(metadata.getPrecision(1)).thenReturn(10);
            when(digestQuery.executeQuery()).thenReturn(digestRows);
            when(digestRows.next()).thenReturn(true, false);
            when(digestRows.getLong(2)).thenReturn(1L);
            when(digestRows.getString(3)).thenReturn("a".repeat(64));
        }

        @Test void shouldSetMetadataDeadline_beforeExecutionAndNativeRowHashing() throws Exception {
            assertThat(CdsTargetReceiptService.rowDigest(connection, "demographic", "1")).matches("[0-9a-f]{64}");
            var order = inOrder(connection, metadataQuery, metadataRows, digestQuery);
            order.verify(connection).prepareStatement("SELECT * FROM `demographic` WHERE 1=0");
            order.verify(metadataQuery).setQueryTimeout(30);
            order.verify(metadataQuery).executeQuery();
            order.verify(metadataRows).getMetaData();
            order.verify(digestQuery).setString(1, "1");
            order.verify(digestQuery).setMaxRows(2);
            order.verify(digestQuery).setQueryTimeout(30);
            order.verify(digestQuery).executeQuery();
            verify(metadataRows).close(); verify(metadataQuery).close();
            verify(digestRows).close(); verify(digestQuery).close();
        }

        @Test void shouldPropagateMetadataTimeout_withoutStartingTheNativeRowQuery() throws Exception {
            var failure = new SQLTimeoutException("synthetic metadata lock deadline");
            doThrow(failure).when(metadataQuery).executeQuery();
            assertThatThrownBy(() -> CdsTargetReceiptService.rowDigest(connection, "demographic", "1")).isSameAs(failure);
            var order = inOrder(metadataQuery);
            order.verify(metadataQuery).setQueryTimeout(30);
            order.verify(metadataQuery).executeQuery();
            verify(metadataQuery).close();
            verify(connection, times(1)).prepareStatement(anyString());
            verifyNoInteractions(digestQuery, digestRows);
        }

        @Test void shouldRefuseUnsupportedMetadataDeadline_beforeAnyQueryExecution() throws Exception {
            var failure = new SQLException("synthetic unsupported query timeout");
            doThrow(failure).when(metadataQuery).setQueryTimeout(30);
            assertThatThrownBy(() -> CdsTargetReceiptService.rowDigest(connection, "demographic", "1")).isSameAs(failure);
            verify(metadataQuery, never()).executeQuery();
            verify(metadataQuery).close();
            verify(connection, times(1)).prepareStatement(anyString());
            verifyNoInteractions(metadataRows, digestQuery, digestRows);
        }
    }

    @FunctionalInterface private interface TemporalCheck { void run() throws Exception; }
    private static void withTemporalZone(TemporalCheck check) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try { TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto")); check.run(); }
        finally { TimeZone.setDefault(previous); }
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
