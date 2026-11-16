/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsCategoryReadPermission;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService.Gate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The export's authorization passes over the real privilege evaluation
 * ({@link SecurityInfoManagerImpl}) and a small mutable stand-in for the authorization tables.
 * Every read of those tables is counted, so the tests show both what a pass decides (exactly
 * what the single checks decide from the same rows) and what it costs.
 */
@Tag("unit")
@Tag("cds-export")
@Tag("security")
class CdsExportAuthorizationPassUnitTest extends CarlosUnitTestBase {
    private static final List<String> OBJECTS =
            List.of(
                    "_admin",
                    "_demographic",
                    "_rx",
                    "_allergy",
                    "_prevention",
                    "_lab",
                    "_edoc",
                    "_appointment",
                    "_measurement",
                    "_casemgmt.notes",
                    "_lab_sensitive",
                    "_dxresearch",
                    "_eform",
                    "_eChart",
                    "_con",
                    "_form");

    private final List<SecObjPrivilege> privilegeTable = new ArrayList<>();
    private final List<Secuserrole> roleTable = new ArrayList<>();
    private final AtomicInteger roleReads = new AtomicInteger();
    private final AtomicInteger privilegeReads = new AtomicInteger();
    private final AtomicInteger providerReads = new AtomicInteger();
    private final AtomicInteger noteGrantReads = new AtomicInteger();
    private CdsExportAccessService access;
    private CdsExportNoteAccessDao notes;
    private final CdsExportActorDto actor = new CdsExportActorDto("operator$", "100", null, null);
    private final CdsExportCommand command =
            new CdsExportCommand(
                    "test",
                    "100",
                    null,
                    CdsLogicalProfile.ONTARIO_MD_5_2,
                    CdsExportCommand.Selection.SELECTED_PATIENTS,
                    List.of(),
                    List.of(27, 28, 29),
                    EnumSet.allOf(CdsCategory.class),
                    "recipient",
                    "audit");

    @BeforeEach
    void prepare() {
        var roles = mock(SecuserroleDao.class);
        when(roles.findActiveByProviderNo(any()))
                .thenAnswer(
                        call -> {
                            roleReads.incrementAndGet();
                            return roleTable.stream()
                                    .filter(row -> row.getProviderNo().equals(call.getArgument(0)))
                                    .toList();
                        });
        var privileges = mock(SecObjPrivilegeDao.class);
        when(privileges.findByObjectNames(any()))
                .thenAnswer(
                        call -> {
                            privilegeReads.incrementAndGet();
                            Collection<String> names = call.getArgument(0);
                            return privilegeTable.stream()
                                    .filter(row -> names.contains(row.getId().getObjectName()))
                                    .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                                    .toList();
                        });
        registerMock(SecObjPrivilegeDao.class, privileges);
        var security = new SecurityInfoManagerImpl();
        injectDependency(security, "secUserRoleDao", roles);

        var provider = new Provider();
        provider.setProviderNo("100");
        provider.setStatus("1");
        var providers = mock(ProviderDao.class);
        when(providers.getProvider("100"))
                .thenAnswer(
                        call -> {
                            providerReads.incrementAndGet();
                            return provider;
                        });
        notes = mock(CdsExportNoteAccessDao.class);
        when(notes.canReadChart(anyString(), anyInt())).thenReturn(true);
        when(notes.canRead(anyString(), any(), any(), anyBoolean()))
                .thenAnswer(
                        call -> {
                            noteGrantReads.incrementAndGet();
                            return true;
                        });
        when(notes.findScope(anyInt(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(
                        call -> note(call.<Integer>getArgument(0), call.<Long>getArgument(1)));
        when(notes.findScopes(anyInt(), any()))
                .thenAnswer(
                        call -> {
                            int patient = call.getArgument(0);
                            List<Long> ids = call.getArgument(1);
                            return ids.stream().map(id -> note(patient, id)).toList();
                        });
        Properties policy = new Properties();
        policy.setProperty("cds.export.operator.operator$.providers", "100");
        policy.setProperty("FILTER_ON_FACILITY", "false");
        access =
                new CdsExportAccessService(
                        security,
                        new CdsExportConfigurationService(policy),
                        providers,
                        mock(SecurityDao.class),
                        mock(ProviderFacilityDao.class),
                        notes);

        var role = new Secuserrole();
        role.setProviderNo("100");
        role.setRoleName("doctor");
        roleTable.add(role);
        for (String object : OBJECTS) grant(object, "doctor", "x", 0);
    }

    private static CdsExportNoteAccessDto note(int patient, long id) {
        return new CdsExportNoteAccessDto(id, patient, "17", "1");
    }

    private void grant(String object, String role, String right, int priority) {
        var row = new SecObjPrivilege();
        row.setId(new SecObjPrivilegePrimaryKey(role, object));
        row.setPrivilege(right);
        row.setPriority(priority);
        privilegeTable.add(row);
    }

    private void revoke(String object) {
        privilegeTable.removeIf(row -> row.getId().getObjectName().equals(object));
    }

    private void resetCounts() {
        roleReads.set(0);
        privilegeReads.set(0);
        providerReads.set(0);
        noteGrantReads.set(0);
    }

    private List<Gate> gates() {
        var gates = new ArrayList<Gate>(List.of(Gate.PATIENT, Gate.PHARMACY, Gate.CASE_MANAGEMENT));
        for (CdsCategory category : command.categories()) gates.add(Gate.category(category, false));
        gates.add(Gate.category(CdsCategory.LABORATORY_RESULTS, true));
        return gates;
    }

    /** The refusal of one single, uncached check, or null when it passes. */
    private String single(int patient, Gate gate) {
        try {
            access.requireGate(actor, command, patient, gate);
            return null;
        } catch (SecurityException refused) {
            return refused.getMessage();
        }
    }

    private String decided(CdsExportAccessService.PatientDecisions decisions, Gate gate) {
        try {
            decisions.require(gate);
            return null;
        } catch (SecurityException refused) {
            return refused.getMessage();
        }
    }

    /** One patient as an export worker authorizes it: a session, then the later passes. */
    private void exportOnePatient(int patient, int reauthorizations) {
        var live = new CdsExportPatientAuthorization.Live(access, actor, command);
        var session = new CdsExportPatientAuthorization(patient, live);
        // Capture, read-back, reconciliation and emission each ask everything again.
        for (int step = 0; step < 12; step++) {
            session.patient(patient);
            session.pharmacy(patient);
            session.caseManagement(patient);
            for (CdsCategory category : command.categories())
                session.category(patient, category, false);
            session.category(patient, CdsCategory.LABORATORY_RESULTS, true);
            session.record(patient, "_con");
            session.note(CdsCategory.CLINICAL_NOTES, note(patient, 91));
        }
        session.finish();
        for (int pass = 0; pass < reauthorizations; pass++) {
            var again = CdsExportPatientAuthorization.pass(patient, live);
            again.patient(patient);
            again.pharmacy(patient);
            again.caseManagement(patient);
            for (CdsCategory category : command.categories())
                again.category(patient, category, false);
            again.notes(CdsCategory.CLINICAL_NOTES, List.of(note(patient, 91)));
        }
    }

    @Test
    @DisplayName("should decide every gate as its single check does whichever right is missing")
    void shouldDecideEveryGateAsItsSingleCheckDoes_whicheverRightIsMissing() {
        var changes = new ArrayList<Runnable>();
        changes.add(() -> {});
        for (String object : OBJECTS) changes.add(() -> revoke(object));
        // Patient-specific rows: a lockout, a read-only override and a row for another role.
        changes.add(() -> grant("_demographic$27", "doctor", "o", 0));
        changes.add(() -> grant("_eChart$27", "doctor", "o", 0));
        changes.add(() -> grant("_lab$27", "doctor", "o", 0));
        changes.add(() -> grant("_rx$27", "100", "o", 1));
        changes.add(() -> grant("_edoc$27", "nurse", "o", 0));
        changes.add(() -> grant("_casemgmt.notes$27", "doctor", "r", 0));
        for (Runnable change : changes) {
            var before = new ArrayList<>(privilegeTable);
            change.run();

            CdsExportAccessService.PatientDecisions decisions = null;
            String whole = null;
            try {
                decisions = access.decidePatient(actor, command, 27);
            } catch (SecurityException refused) {
                // Without the right to export at all the pass is refused as a whole, which is
                // what every single check answers too.
                whole = refused.getMessage();
            }

            for (Gate gate : gates())
                assertThat(decisions == null ? whole : decided(decisions, gate))
                        .as("%s %s %s", gate.kind(), gate.category(), gate.sensitiveLaboratory())
                        .isEqualTo(single(27, gate));
            privilegeTable.clear();
            privilegeTable.addAll(before);
        }
    }

    @Test
    @DisplayName("should refuse the whole opening pass when the exporter may no longer export")
    void shouldRefuseTheWholeOpeningPass_whenTheExporterMayNoLongerExport() {
        revoke("_admin");

        assertThatThrownBy(() -> access.decidePatient(actor, command, 27))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> access.decidePatient(actor, command, 30))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CDS_EXPORT_AUTHORIZATION_DENIED");
    }

    @Test
    @DisplayName("should read each authorization table once for the opening pass of one patient")
    void shouldReadEachAuthorizationTableOnce_forTheOpeningPassOfOnePatient() {
        // The same decisions as single checks, each in its own transaction with its own reads.
        for (Gate gate : gates()) assertThat(single(27, gate)).isNull();
        int singleRoleReads = roleReads.get(),
                singlePrivilegeReads = privilegeReads.get(),
                singleProviderReads = providerReads.get();
        resetCounts();

        var decisions = access.decidePatient(actor, command, 27);

        for (Gate gate : gates()) assertThat(decided(decisions, gate)).isNull();
        assertThat(List.of(roleReads.get(), privilegeReads.get(), providerReads.get()))
                .containsExactly(1, 1, 1);
        // What the same gates cost one check at a time (the cost of every pass before).
        assertThat(List.of(singleRoleReads, singlePrivilegeReads, singleProviderReads))
                .containsExactly(104, 186, 20);
    }

    @Test
    @DisplayName("should cost the same few reads for every further patient of a roster")
    void shouldCostTheSameFewReads_forEveryFurtherPatientOfARoster() {
        exportOnePatient(27, 6);
        int roles = roleReads.get(),
                privileges = privilegeReads.get(),
                providers = providerReads.get(),
                grants = noteGrantReads.get();
        // A session's opening and closing pass, then two transactions for each later pass.
        assertThat(List.of(roles, privileges, providers, grants)).containsExactly(14, 14, 14, 8);

        exportOnePatient(28, 6);
        exportOnePatient(29, 6);

        assertThat(List.of(roleReads.get(), privilegeReads.get(), providerReads.get(), noteGrantReads.get()))
                .containsExactly(3 * roles, 3 * privileges, 3 * providers, 3 * grants);
    }

    @Test
    @DisplayName("should hold the patient when a right is revoked between the opening and the closing pass")
    void shouldHoldThePatient_whenARightIsRevokedBetweenTheOpeningAndTheClosingPass() {
        for (Runnable revocation :
                List.<Runnable>of(
                        () -> revoke("_rx"),
                        () -> revoke("_demographic"),
                        () -> revoke("_admin"),
                        () -> revoke("_eChart"),
                        () -> grant("_eChart$27", "doctor", "o", 0),
                        () -> roleTable.clear())) {
            var before = new ArrayList<>(privilegeTable);
            var roles = new ArrayList<>(roleTable);
            var live = new CdsExportPatientAuthorization.Live(access, actor, command);
            var session = new CdsExportPatientAuthorization(27, live);
            session.patient(27);
            session.pharmacy(27);
            session.caseManagement(27);
            session.category(27, CdsCategory.MEDICATIONS_AND_TREATMENTS, false);
            session.note(CdsCategory.CLINICAL_NOTES, note(27, 91));

            revocation.run();

            // The session still answers its patient's later steps from what it decided ...
            session.patient(27);
            session.pharmacy(27);
            // ... and the closing pass reads the database again and refuses the patient.
            assertThatThrownBy(session::finish).isInstanceOf(SecurityException.class);
            privilegeTable.clear();
            privilegeTable.addAll(before);
            roleTable.clear();
            roleTable.addAll(roles);
        }
    }

    @Test
    @DisplayName("should hold the patient when a note's program grant is withdrawn while it is read")
    void shouldHoldThePatient_whenANoteGrantIsWithdrawnWhileItIsRead() {
        var live = new CdsExportPatientAuthorization.Live(access, actor, command);
        var session = new CdsExportPatientAuthorization(27, live);
        session.note(CdsCategory.CLINICAL_NOTES, note(27, 91));

        when(notes.canRead(anyString(), any(), any(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(session::finish).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should refuse the next patient when a right is revoked between two patients")
    void shouldRefuseTheNextPatient_whenARightIsRevokedBetweenTwoPatients() {
        var live = new CdsExportPatientAuthorization.Live(access, actor, command);
        var first = new CdsExportPatientAuthorization(27, live);
        first.patient(27);
        first.category(27, CdsCategory.LABORATORY_RESULTS, false);
        first.finish();

        revoke("_lab");

        // The next patient's opening pass reads the database; nothing of the first is carried.
        var second = new CdsExportPatientAuthorization(28, live);
        assertThatThrownBy(() -> second.patient(28)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> second.category(28, CdsCategory.LABORATORY_RESULTS, false))
                .isInstanceOf(SecurityException.class);
        // A right that is still held still passes.
        second.category(28, CdsCategory.APPOINTMENTS, false);
    }

    @Test
    @DisplayName("should keep no row once a pass has ended")
    void shouldKeepNoRow_onceAPassHasEnded() {
        var decisions = access.decidePatient(actor, command, 27);
        assertThat(decided(decisions, Gate.PATIENT)).isNull();

        revoke("_demographic");

        // A single check right after reads the database itself, on the same thread.
        assertThat(single(27, Gate.PATIENT)).isEqualTo("CDS_EXPORT_AUTHORIZATION_DENIED");
        assertThat(decided(access.decidePatient(actor, command, 27), Gate.PATIENT))
                .isEqualTo("CDS_EXPORT_AUTHORIZATION_DENIED");
    }

    @Test
    @DisplayName("should refuse the closing pass when a question no longer answers yes")
    void shouldRefuseTheClosingPass_whenAQuestionNoLongerAnswersYes() {
        when(notes.canReadRestrictedProgram("100", 10034)).thenReturn(true);
        var live = new CdsExportPatientAuthorization.Live(access, actor, command);
        var session = new CdsExportPatientAuthorization(27, live);
        assertThat(session.restrictedProgram(27, CdsCategory.REPORTS, "10034")).isTrue();
        assertThat(session.record(27, "_con")).isTrue();

        when(notes.canReadRestrictedProgram("100", 10034)).thenReturn(false);

        assertThatThrownBy(session::finish)
                .isInstanceOf(SecurityException.class)
                .hasMessage("CDS_EXPORT_ACCESS_CHANGED");
    }

    @Test
    @DisplayName("should leave a record's printed form out without holding the patient")
    void shouldAnswerARecordObjectNo_withoutRefusingAnyGate() {
        revoke("_con");

        var live = new CdsExportPatientAuthorization.Live(access, actor, command);
        var session = new CdsExportPatientAuthorization(27, live);

        session.patient(27);
        session.category(27, CdsCategory.REPORTS, false);
        assertThat(session.record(27, "_con")).isFalse();
        assertThat(session.record(27, "_form")).isTrue();
        session.finish();
    }

    @Test
    @DisplayName("should name the security objects a category's check reads")
    void shouldReadAheadEveryObject_aCategoryCheckAsksAbout() {
        // The read-ahead list covers every category's object, so no gate needs a read of its own.
        for (CdsCategory category : CdsCategory.values())
            assertThat(OBJECTS).contains(CdsCategoryReadPermission.object(category));
    }
}
