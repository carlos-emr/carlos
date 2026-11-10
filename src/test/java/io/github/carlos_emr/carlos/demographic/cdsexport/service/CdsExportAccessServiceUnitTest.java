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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportAccessServiceUnitTest {
    private SecurityInfoManager permissions;
    private ProviderDao providers;
    private SecurityDao accounts;
    private Provider provider, otherProvider;
    private Security account;
    private LoggedInInfo session;
    private CdsExportAccessService access;
    private CdsExportConfigurationService configuration;
    private io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao notes;

    @BeforeEach
    void initializeCurrentPolicy() {
        permissions = mock(SecurityInfoManager.class);
        providers = mock(ProviderDao.class);
        accounts = mock(SecurityDao.class);
        provider = provider("100");
        otherProvider = provider("200");
        when(providers.getProvider("100")).thenReturn(provider);
        when(providers.getProvider("200")).thenReturn(otherProvider);
        account = new Security();
        account.setSecurityNo(7);
        account.setProviderNo("100");
        when(accounts.find(7)).thenReturn(account);
        session = new LoggedInInfo();
        session.setLoggedInProvider(provider);
        session.setLoggedInSecurity(account);
        when(permissions.hasPrivilegeReadOnly(
                        any(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        Properties policy = new Properties();
        policy.setProperty("cds.export.operator.operator$.providers", "100");
        policy.setProperty("cds.export.operator.web-7.providers", "200");
        configuration = new CdsExportConfigurationService(policy);
        notes = mock(io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao.class);
        access =
                new CdsExportAccessService(
                        permissions,
                        configuration,
                        providers,
                        accounts,
                        mock(ProviderFacilityDao.class),
                        notes);
    }

    @Test
    void shouldObserveDelegatedDisclosureIdentity_withBothProvidersCurrentlyAuthorized() {
        var actor = access.webActor(session, command("200"));
        access.requireObserve(actor, document(actor));
        access.requireScope(actor, command("200"));
        for (String providerNo : List.of("100", "200")) {
            verify(permissions, atLeastOnce())
                    .hasPrivilegeReadOnly(
                            argThat(info -> providerNo.equals(info.getLoggedInProviderNo())),
                            eq("_demographic"),
                            eq("r"),
                            isNull());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"100", "200"})
    void shouldRefuseDelegatedMetadata_whenEitherProviderLosesAdministration(String denied) {
        var actor = access.webActor(session, command("200"));
        when(permissions.hasPrivilegeReadOnly(any(), anyString(), eq("w"), isNull()))
                .thenAnswer(
                        call ->
                                !denied.equals(
                                        call.<LoggedInInfo>getArgument(0).getLoggedInProviderNo()));
        assertThatThrownBy(() -> access.requireObserve(actor, document(actor)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseDelegatedMetadata_whenDelegationIsRemoved() {
        var actor = access.webActor(session, command("200"));
        var properties =
                (Properties)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                configuration, "properties");
        properties.remove("cds.export.operator.web-7.providers");
        assertThatThrownBy(() -> access.requireObserve(actor, document(actor)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldVerifyPharmacyMatchesOwningRoutesGlobalAndPatientRxChecks_forBothIdentities() {
        for (String denied : List.of("100", "200")) {
            var command = demographicCommand("200");
            var actor = access.webActor(session, command);
            when(permissions.hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), isNull()))
                    .thenAnswer(
                            call ->
                                    !denied.equals(
                                            call.<LoggedInInfo>getArgument(0)
                                                    .getLoggedInProviderNo()));
            assertThatThrownBy(() -> access.requirePharmacyRead(actor, command, 27))
                    .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        }
    }

    @Test
    void shouldVerifyPharmacyRequiresItsOwnPatientRxRead_withoutSelectingMedicationForExport() {
        var command = demographicCommand("100");
        var actor = access.webActor(session, command);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), eq("27")))
                .thenReturn(false);
        assertThat(access.requirePatient(actor, command, 27)).isNotNull();
        assertThatThrownBy(() -> access.requirePharmacyRead(actor, command, 27))
                .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
    }

    @Test
    void
            shouldVerifyAnAllowedPharmacyReadStillChecksTheActualPatientAndCurrentNativePrivilege_forAccessServiceContract() {
        var command = demographicCommand("100");
        var actor = access.webActor(session, command);
        assertThat(access.requirePharmacyRead(actor, command, 27).getLoggedInProviderNo())
                .isEqualTo("100");
        verify(permissions).hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), eq("27"));
        clearInvocations(permissions, providers, accounts);
        assertThatThrownBy(() -> access.requirePharmacyRead(actor, command, 28))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(permissions, providers, accounts);
    }

    @Test
    void
            shouldVerifyDelegatedPharmacyReadsRequireBothTheInitiatingAndEffectiveProvidersRxRights_forAccessServiceContract() {
        for (String denied : List.of("100", "200")) {
            var command = demographicCommand("200");
            var actor = access.webActor(session, command);
            when(permissions.hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), eq("27")))
                    .thenAnswer(
                            call ->
                                    !denied.equals(
                                            call.<LoggedInInfo>getArgument(0)
                                                    .getLoggedInProviderNo()));
            assertThatThrownBy(() -> access.requirePharmacyRead(actor, command, 27))
                    .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        }
    }

    private static CdsExportCommand demographicCommand(String provider) {
        return new CdsExportCommand(
                "pharmacy-rights",
                provider,
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                java.util.Set.of(CdsCategory.DEMOGRAPHICS),
                "recipient",
                "audit");
    }

    @Test
    void shouldRequireTheExactLiveNoteScope_beforeCheckingItsProgramGrant() {
        var command = noteCommand("100");
        var actor = access.webActor(session, command);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "17", "1");
        when(notes.findScope(27, 91)).thenReturn(scope);
        when(notes.canRead("100", scope, null, true)).thenReturn(true);
        access.requireNoteRead(actor, command, CdsCategory.CLINICAL_NOTES, scope);
        verify(notes).canRead("100", scope, null, true);
        clearInvocations(notes);
        when(notes.findScope(27, 91))
                .thenReturn(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNoteAccessDto(91, 27, "18", "1"));
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.CLINICAL_NOTES, scope))
                .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        verify(notes, never()).canRead(anyString(), any(), nullable(Integer.class), anyBoolean());
    }

    @Test
    void shouldDenyMissingNotesAndChangedReporterRoles_beforeCheckingProgramGrants() {
        var command = noteCommand("100");
        var actor = access.webActor(session, command);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "17", "1");
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.CLINICAL_NOTES, scope))
                .isInstanceOf(SecurityException.class);
        when(notes.findScope(27, 91))
                .thenReturn(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNoteAccessDto(91, 27, "17", "2"));
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.CLINICAL_NOTES, scope))
                .isInstanceOf(SecurityException.class);
        verify(notes, never()).canRead(anyString(), any(), nullable(Integer.class), anyBoolean());
    }

    @Test
    void shouldRequireProgramRightsForBothDelegatedIdentities_onEveryCall() {
        var command = noteCommand("200");
        var actor = access.webActor(session, command);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "17", "1");
        when(notes.findScope(27, 91)).thenReturn(scope);
        for (String denied : List.of("100", "200")) {
            when(notes.canRead(anyString(), eq(scope), isNull(), eq(true)))
                    .thenAnswer(call -> !denied.equals(call.getArgument(0)));
            assertThatThrownBy(
                            () ->
                                    access.requireNoteRead(
                                            actor, command, CdsCategory.CLINICAL_NOTES, scope))
                    .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        }
        when(notes.canRead(anyString(), eq(scope), isNull(), eq(true))).thenReturn(true);
        access.requireNoteRead(actor, command, CdsCategory.CLINICAL_NOTES, scope);
        when(notes.canRead("100", scope, null, true)).thenReturn(false);
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.CLINICAL_NOTES, scope))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldDenyNoteReadsOutsideTheSelectedPatientAndCategory_beforeAnyLookup() {
        var command = noteCommand("100");
        var actor = access.webActor(session, command);
        clearInvocations(permissions, providers, accounts);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "17", "1");
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.IMMUNIZATIONS, scope))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.FAMILY_HISTORY, scope))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor,
                                        command,
                                        CdsCategory.CLINICAL_NOTES,
                                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                .CdsExportNoteAccessDto(91, 28, "17", "1")))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(notes, permissions, providers, accounts);
    }

    @Test
    void shouldCheckCurrentPatientAndCategoryRights_beforeReadingNoteAccessMetadata() {
        var command = noteCommand("100");
        var actor = access.webActor(session, command);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "17", "1");
        when(permissions.hasPrivilegeReadOnly(any(), eq("_casemgmt.notes"), eq("r"), eq("27")))
                .thenReturn(false);
        assertThatThrownBy(
                        () ->
                                access.requireNoteRead(
                                        actor, command, CdsCategory.CLINICAL_NOTES, scope))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(notes);
    }

    @Test
    void shouldRejectMalformedOrOverflowingNoteScopes_withoutNumericCoercion() {
        for (String value : List.of("01", "1x", " 1", "-1", "2147483648", "4294967296"))
            assertThatThrownBy(
                            () ->
                                    new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                            .CdsExportNoteAccessDto(91, 27, value, "1"))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportNoteAccessDto(91, 27, "1", "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportNoteAccessDto(91, 27, "0", "1")
                                .toString())
                .isEqualTo("CdsExportNoteAccessDto[protected]");
    }

    @Test
    void shouldCheckBothIdentitiesChartRights_beforeReadingNoteMetadata() {
        var command = noteCommand("200");
        var actor = access.webActor(session, command);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "", "1");
        for (String denied : List.of("100", "200")) {
            for (String patient : new String[] {null, "27"}) {
                reset(permissions);
                when(permissions.hasPrivilegeReadOnly(
                                any(), anyString(), anyString(), nullable(String.class)))
                        .thenReturn(true);
                when(permissions.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
                when(permissions.hasPrivilegeReadOnly(any(), eq("_eChart"), eq("r"), eq(patient)))
                        .thenAnswer(
                                call ->
                                        !denied.equals(
                                                call.<LoggedInInfo>getArgument(0)
                                                        .getLoggedInProviderNo()));
                assertThatThrownBy(
                                () ->
                                        access.requireNoteRead(
                                                actor, command, CdsCategory.CLINICAL_NOTES, scope))
                        .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
                verifyNoInteractions(notes);
            }
        }
    }

    @Test
    void shouldApplyTheTrustedFacilityPolicy_toUnscopedImportedNotes() {
        var policy = new Properties();
        policy.setProperty("FILTER_ON_FACILITY", "false");
        configuration = new CdsExportConfigurationService(policy);
        access =
                new CdsExportAccessService(
                        permissions,
                        configuration,
                        providers,
                        accounts,
                        mock(ProviderFacilityDao.class),
                        notes);
        var command = noteCommand("100");
        var actor = access.webActor(session, command);
        var scope =
                new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto(
                        91, 27, "", "1");
        when(notes.findScope(27, 91)).thenReturn(scope);
        when(notes.canRead("100", scope, null, false)).thenReturn(true);
        access.requireNoteRead(actor, command, CdsCategory.CLINICAL_NOTES, scope);
        verify(notes).canRead("100", scope, null, false);
        policy.setProperty("FILTER_ON_FACILITY", "sometimes");
        assertThatThrownBy(() -> new CdsExportConfigurationService(policy).filterNotesOnFacility())
                .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
    }

    @Test
    void shouldRequireTheCompleteHistoryScope_beforeReadingPatientIssues() {
        var command = noteCommand("100");
        var actor = access.webActor(session, command);
        clearInvocations(notes);
        assertThatThrownBy(() -> access.requireCaseManagementRead(actor, command, 27))
                .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        verifyNoInteractions(notes);
    }

    @Test
    void shouldRequireBothCurrentProgramDomains_forCompleteIssueReads() {
        var command = historyCommand("200");
        var actor = access.webActor(session, command);
        for (String denied : List.of("100", "200")) {
            when(notes.canReadChart(anyString(), eq(27)))
                    .thenAnswer(call -> !denied.equals(call.getArgument(0)));
            assertThatThrownBy(() -> access.requireCaseManagementRead(actor, command, 27))
                    .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        }
        when(notes.canReadChart(anyString(), eq(27))).thenReturn(true);
        access.requireCaseManagementRead(actor, command, 27);
    }

    @Test
    void shouldRequireCurrentChartRead_beforeLookingUpAnyIssueDomain() {
        var command = historyCommand("100");
        var actor = access.webActor(session, command);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_eChart"), eq("r"), eq("27")))
                .thenReturn(false);
        assertThatThrownBy(() -> access.requireCaseManagementRead(actor, command, 27))
                .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        verifyNoInteractions(notes);
    }

    @Test
    void shouldRecheckChartDomain_withoutRequiringANoteToExist() {
        var command = historyCommand("100");
        var actor = access.webActor(session, command);
        when(notes.canReadChart("100", 27)).thenReturn(true);
        access.requireCaseManagementRead(actor, command, 27);
        when(notes.canReadChart("100", 27)).thenReturn(false);
        assertThatThrownBy(() -> access.requireCaseManagementRead(actor, command, 27))
                .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
        verify(notes, never()).findScope(anyInt(), anyLong());
    }

    private static CdsExportCommand historyCommand(String provider) {
        var categories = java.util.EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.DEMOGRAPHICS);
        return new CdsExportCommand(
                "issue-rights",
                provider,
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                categories,
                "recipient",
                "audit");
    }

    private static CdsExportCommand noteCommand(String provider) {
        return new CdsExportCommand(
                "note-rights",
                provider,
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                java.util.Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.CLINICAL_NOTES),
                "recipient",
                "audit");
    }

    @Test
    void shouldRecheckAnAcceptedWebActorsLiveAccount_insteadOfDependingOnItsBrowserSession() {
        var actor = access.webActor(session, command("100"));
        assertThat(access.requirePatient(actor, command("100"), 27).getLoggedInProviderNo())
                .isEqualTo("100");
        account.setBExpireset(1);
        account.setDateExpiredate(
                java.util.Date.from(java.time.Instant.parse("2000-01-01T00:00:00Z")));
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 27))
                .isInstanceOf(SecurityException.class);
        verify(accounts, atLeast(3)).find(7);
    }

    @Test
    void shouldRejectClinicalReadsOutsideAnExplicitSelection_evenWhenChartPermissionsAllowThem() {
        var actor = access.webActor(session, command("100"));
        clearInvocations(permissions, providers, accounts);
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 28))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(
                        () ->
                                access.requireCategory(
                                        actor, command("100"), 28, CdsCategory.DEMOGRAPHICS, false))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(permissions, providers, accounts);
    }

    @Test
    void shouldRefuseARevokedCategoryPermission_andAChangedPatientRestriction() {
        var actor = access.webActor(session, command("100"));
        when(permissions.hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), eq("27")))
                .thenReturn(false);
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 27))
                .isInstanceOf(SecurityException.class);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), eq("27")))
                .thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(any(), eq(27))).thenReturn(false);
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 27))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseInactiveProviders_andAStaleOrForgedSessionBinding() {
        var actor = access.webActor(session, command("100"));
        provider.setStatus("0");
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 27))
                .isInstanceOf(SecurityException.class);
        provider.setStatus("1");
        session.setLoggedInProvider(otherProvider);
        assertThatThrownBy(() -> access.webActor(session, command("100")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> access.webActor(null, command("100")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRequireCurrentRights_forBothTheDelegatingAndEffectiveWebProvider() {
        var actor = access.webActor(session, command("200"));
        when(permissions.hasPrivilegeReadOnly(
                        argThat(c -> c != null && "100".equals(c.getLoggedInProviderNo())),
                        eq("_lab"),
                        eq("r"),
                        eq("27")))
                .thenReturn(false);
        assertThatThrownBy(() -> access.requirePatient(actor, command("200"), 27))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRequireExportDelegation_andNeverSubstituteImportEnablementOrOrdinaryEditingRights() {
        var actor = access.delegatedActor("operator$", command("100"));
        assertThat(access.requirePatient(actor, command("100"), 27)).isNotNull();
        assertThatThrownBy(() -> access.delegatedActor("unregistered", command("100")))
                .isInstanceOf(SecurityException.class);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_cds_export"), eq("w"), isNull()))
                .thenReturn(false);
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 27))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseFacilityOrProviderScopeSubstitution_forAccessServiceContract() {
        var actor = access.webActor(session, command("100"));
        assertThatThrownBy(() -> access.requirePatient(actor, command("200"), 27))
                .isInstanceOf(SecurityException.class);
        var changed =
                new CdsExportActorDto(actor.operator(), actor.providerNo(), actor.securityNo(), 3);
        assertThatThrownBy(() -> access.requirePatient(changed, command("100"), 27))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRequireSensitiveLabRights_whenActualSourceMetadataIdentifiesSensitiveResults() {
        var actor = access.webActor(session, command("100"));
        when(permissions.hasPrivilegeReadOnly(any(), eq("_lab_sensitive"), eq("r"), eq("27")))
                .thenReturn(false);
        assertThat(
                        access.requireCategory(
                                actor, command("100"), 27, CdsCategory.LABORATORY_RESULTS, false))
                .isNotNull();
        assertThatThrownBy(
                        () ->
                                access.requireCategory(
                                        actor,
                                        command("100"),
                                        27,
                                        CdsCategory.LABORATORY_RESULTS,
                                        true))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void
            shouldAllowProtectedOwnerMetadataAfterAnUnrelatedCategoryRightIsRevoked_withoutAuthorizingClinicalRead() {
        var actor = access.webActor(session, command("100"));
        var document = document(actor);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_rx"), eq("r"), nullable(String.class)))
                .thenReturn(false);
        access.requireObserve(actor, document);
        assertThatThrownBy(() -> access.requirePatient(actor, command("100"), 27))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseAnotherOperatorsMetadata_withoutCurrentAdministratorAuthority() {
        var actor = access.delegatedActor("operator$", command("100"));
        when(permissions.hasPrivilegeReadOnly(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
        var owner = new CdsExportActorDto("web-7", "100", 7, null);
        assertThatThrownBy(() -> access.requireObserve(actor, document(owner)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseAUnixAccountWhoseNameMatchesTheBrowserOwner_withoutAdministratorAuthority() {
        var properties = new Properties();
        properties.setProperty("cds.export.operator.web-7.providers", "100");
        var isolated =
                new CdsExportAccessService(
                        permissions,
                        new CdsExportConfigurationService(properties),
                        providers,
                        accounts,
                        mock(ProviderFacilityDao.class),
                        notes);
        var unix = isolated.delegatedActor("web-7", command("100"));
        var web = new CdsExportActorDto("web-7", "100", 7, null);
        when(permissions.hasPrivilegeReadOnly(any(), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);
        assertThatThrownBy(() -> isolated.requireObserve(unix, document(web)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldKeepObservationAvailable_whenExportAdmissionIsDisabled() {
        assertThat(configuration.enabled()).isFalse();
        assertThatThrownBy(configuration::requireEnabled)
                .hasMessage("CDS_EXPORT_ADMISSION_DISABLED");
        assertThat(access.operatorSession(session).operator()).isEqualTo("web-7");
    }

    @Test
    void shouldRequireConfiguredFacilityScopes_andRefuseMalformedConfiguration() {
        var properties = new Properties();
        properties.setProperty("cds.export.operator.operator$.providers", "100");
        properties.setProperty("cds.export.operator.operator$.facilities", "3");
        var policy = new CdsExportConfigurationService(properties);
        policy.requireDelegation("operator$", "100", 3);
        assertThatThrownBy(() -> policy.requireDelegation("operator$", "100", null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> policy.requireDelegation("operator$", "100", 4))
                .isInstanceOf(SecurityException.class);
        properties.setProperty("cds.export.enabled", "tru");
        assertThatThrownBy(() -> new CdsExportConfigurationService(properties).enabled())
                .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
        properties.setProperty("cds.export.operator.operator$.providers", "100,");
        assertThatThrownBy(
                        () ->
                                new CdsExportConfigurationService(properties)
                                        .requireDelegation("operator$", "100", 3))
                .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
    }

    private static Provider provider(String number) {
        var provider = new Provider();
        provider.setProviderNo(number);
        provider.setStatus("1");
        return provider;
    }

    private static CdsExportCommand command(String number) {
        return new CdsExportCommand(
                "test",
                number,
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                EnumSet.allOf(CdsCategory.class),
                "recipient",
                "audit");
    }

    private static CdsExportJobDocumentDto document(CdsExportActorDto actor) {
        return new CdsExportJobDocumentDto(
                1,
                actor,
                command("100"),
                "a".repeat(64),
                "instance",
                "b".repeat(64),
                "c".repeat(64));
    }
}
