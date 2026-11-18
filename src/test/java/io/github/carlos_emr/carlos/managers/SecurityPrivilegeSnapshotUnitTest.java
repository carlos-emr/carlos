/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A privilege snapshot reads one identity's roles and security objects once and must answer
 * every question exactly as {@link SecurityInfoManager#hasPrivilegeReadOnly} and
 * {@link SecurityInfoManager#isAllowedAccessToPatientRecord} answer it from the same rows.
 */
@Tag("unit")
@Tag("security")
class SecurityPrivilegeSnapshotUnitTest extends CarlosUnitTestBase {
    private static final List<String> OBJECTS = List.of("_demographic", "_eChart", "_lab", "_rx", "_admin");
    private static final List<String> RIGHTS = List.of("r", "u", "w", "d");

    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final HttpSession session = mock(HttpSession.class);
    private final SecuserroleDao roles = mock(SecuserroleDao.class);
    private final SecObjPrivilegeDao privileges = mock(SecObjPrivilegeDao.class);
    private final SecurityInfoManagerImpl security = new SecurityInfoManagerImpl();
    private final List<SecObjPrivilege> stored = new ArrayList<>();

    @BeforeEach
    void prepare() {
        registerMock(SecObjPrivilegeDao.class, privileges);
        injectDependency(security, "secUserRoleDao", roles);
        when(info.getLoggedInProviderNo()).thenReturn("900001");
        when(info.getSession()).thenReturn(session);
        when(roles.findActiveByProviderNo("900001")).thenReturn(List.of(role("doctor"), role("nurse")));
        // The stored rows in priority order, as the DAO returns them for any set of names.
        when(privileges.findByObjectNames(any()))
                .thenAnswer(
                        call -> {
                            Collection<String> names = call.getArgument(0);
                            return stored.stream()
                                    .filter(row -> names.contains(row.getId().getObjectName()))
                                    .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                                    .toList();
                        });
    }

    private static Secuserrole role(String name) {
        Secuserrole role = new Secuserrole();
        role.setRoleName(name);
        role.setProviderNo("900001");
        return role;
    }

    private void grant(String object, String role, String right, int priority) {
        SecObjPrivilege value = new SecObjPrivilege();
        value.setId(new SecObjPrivilegePrimaryKey(role, object));
        value.setPrivilege(right);
        value.setPriority(priority);
        stored.add(value);
    }

    private static List<String> names(String... patients) {
        var names = new ArrayList<>(OBJECTS);
        for (String patient : patients) for (String object : OBJECTS) names.add(object + "$" + patient);
        return names;
    }

    /** Every question of the matrix, asked of both the snapshot and the ordinary check. */
    private void assertSameAnswers(SecurityInfoManager.PrivilegeSnapshot snapshot, String... patients) {
        var targets = new ArrayList<String>(Arrays.asList(patients));
        targets.add(null);
        for (String object : OBJECTS)
            for (String right : RIGHTS)
                for (String target : targets)
                    assertThat(snapshot.hasPrivilege(object, right, target))
                            .as("%s %s %s", object, right, target)
                            .isEqualTo(security.hasPrivilegeReadOnly(info, object, right, target));
        for (String patient : patients)
            assertThat(snapshot.isAllowedAccessToPatientRecord(Integer.valueOf(patient)))
                    .as("record %s", patient)
                    .isEqualTo(security.isAllowedAccessToPatientRecord(info, Integer.valueOf(patient)));
    }

    @Test
    @DisplayName("should answer every question as the ordinary check does for role, override and priority rules")
    void shouldAnswerAsTheOrdinaryCheck_forRoleOverrideAndPriorityRules() {
        grant("_demographic", "doctor", "x", 0);
        grant("_eChart", "doctor", "r", 0);
        grant("_lab", "nurse", "w", 0);
        grant("_rx", "receptionist", "x", 0);
        // Patient 10: a patient-specific row for one of the provider's roles replaces the general one.
        grant("_demographic$10", "doctor", "r", 0);
        grant("_lab$10", "nurse", "o", 0);
        // Patient 11: a row for a role the provider does not hold falls back to the general row.
        grant("_eChart$11", "receptionist", "o", 1);
        // Patient 12: the provider number is a role name; a positive priority stops later roles.
        grant("_eChart$12", "900001", "o", 0);
        grant("_rx$12", "nurse", "r", 1);
        grant("_rx$12", "doctor", "x", 0);
        // Patient 13: the legacy "only read" token.
        grant("_eChart$13", "doctor", "|or|", 0);

        var snapshot = security.readOnlySnapshot(info, names("10", "11", "12", "13"));

        assertSameAnswers(snapshot, "10", "11", "12", "13");
        // The expectations themselves, so two wrong answers cannot agree.
        assertThat(snapshot.hasPrivilege("_demographic", "w", null)).isTrue();
        assertThat(snapshot.hasPrivilege("_demographic", "w", "10")).isFalse();
        assertThat(snapshot.hasPrivilege("_demographic", "r", "10")).isTrue();
        assertThat(snapshot.hasPrivilege("_lab", "r", "10")).isFalse();
        assertThat(snapshot.hasPrivilege("_eChart", "r", "11")).isTrue();
        assertThat(snapshot.hasPrivilege("_rx", "w", "12")).isFalse();
        assertThat(snapshot.hasPrivilege("_rx", "r", null)).isFalse();
        assertThat(snapshot.isAllowedAccessToPatientRecord(12)).isFalse();
        assertThat(snapshot.isAllowedAccessToPatientRecord(13)).isTrue();
    }

    @Test
    @DisplayName("should read the roles and the named objects once for any number of questions")
    void shouldReadRolesAndNamedObjectsOnce_forAnyNumberOfQuestions() {
        grant("_demographic", "doctor", "x", 0);
        grant("_lab$10", "nurse", "o", 0);
        var snapshot = security.readOnlySnapshot(info, names("10"));
        verifyNoInteractions(roles, privileges);

        for (int pass = 0; pass < 3; pass++)
            for (String object : OBJECTS)
                for (String right : RIGHTS) {
                    snapshot.hasPrivilege(object, right, "10");
                    snapshot.hasPrivilege(object, right, null);
                }
        snapshot.isAllowedAccessToPatientRecord(10);

        verify(roles, times(1)).findActiveByProviderNo("900001");
        verify(privileges, times(1)).findByObjectNames(any());
    }

    @Test
    @DisplayName("should answer an object that was not named ahead from its own read")
    void shouldAnswerAnUnnamedObject_fromItsOwnRead() {
        grant("_edoc", "doctor", "r", 0);
        grant("_edoc$10", "doctor", "o", 0);
        var snapshot = security.readOnlySnapshot(info, List.of("_demographic"));

        assertThat(snapshot.hasPrivilege("_edoc", "r", null)).isTrue();
        assertThat(snapshot.hasPrivilege("_edoc", "r", "10")).isFalse();
        assertThat(snapshot.hasPrivilege("_edoc", "r", "10")).isFalse();

        // The named set once, then each unnamed object once however often it is asked.
        verify(privileges, times(3)).findByObjectNames(any());
        verify(roles, times(1)).findActiveByProviderNo("900001");
    }

    @Test
    @DisplayName("should not see a grant written after the snapshot read its rows")
    void shouldNotSeeALaterGrant_afterTheRowsWereRead() {
        var before = security.readOnlySnapshot(info, names());
        assertThat(before.hasPrivilege("_lab", "r", null)).isFalse();

        grant("_lab", "doctor", "r", 0);

        // The old snapshot is a view of the moment it was read; a new one reads the database.
        assertThat(before.hasPrivilege("_lab", "r", null)).isFalse();
        assertThat(security.readOnlySnapshot(info, names()).hasPrivilege("_lab", "r", null)).isTrue();
    }

    @Test
    @DisplayName("should refuse while the rows cannot be read and read them again for the next question")
    void shouldRefuseAndReadAgain_whenTheRowsCannotBeRead() {
        grant("_lab", "doctor", "r", 0);
        when(roles.findActiveByProviderNo("900001"))
                .thenThrow(new IllegalStateException("synthetic outage"))
                .thenReturn(List.of(role("doctor")));
        var snapshot = security.readOnlySnapshot(info, names());

        assertThat(snapshot.hasPrivilege("_lab", "r", null)).isFalse();
        assertThat(snapshot.hasPrivilege("_lab", "r", null)).isTrue();
    }

    @Test
    @DisplayName("should refuse while the security objects cannot be read, without reading them one by one")
    void shouldRefuseWithoutSingleReads_whenTheObjectReadFails() {
        grant("_lab", "doctor", "r", 0);
        var snapshot = security.readOnlySnapshot(info, names());
        clearInvocations(privileges);
        doThrow(new IllegalStateException("synthetic outage")).when(privileges).findByObjectNames(any());

        assertThat(snapshot.hasPrivilege("_lab", "r", null)).isFalse();

        // One failed batch read; the failure is not answered by further reads.
        verify(privileges, times(1)).findByObjectNames(any());
    }

    @Test
    @DisplayName("should never change the session's account-lock state")
    void shouldNeverMarkTheSession_whenAPatientIsLockedOut() {
        grant("_eChart$10", "doctor", "o", 0);
        grant("_eChart", "doctor", "x", 0);
        var snapshot = security.readOnlySnapshot(info, names("10"));

        assertThat(snapshot.hasPrivilege("_eChart", "r", "10")).isFalse();

        verifyNoInteractions(session);
        verify(info, never()).setSession(any());
    }

    @Test
    @DisplayName("should fall back to the single-object reader when a stored name cannot be attributed")
    void shouldUseTheSingleObjectReader_whenAStoredNameCannotBeAttributed() {
        grant("_lab", "doctor", "r", 0);
        // The database's collation can return a row whose stored name is not letter-for-letter a
        // requested one; the batch reader cannot attribute it and the snapshot guesses nothing.
        SecObjPrivilege stray = new SecObjPrivilege();
        stray.setId(new SecObjPrivilegePrimaryKey("doctor", "_láb"));
        stray.setPrivilege("x");
        stray.setPriority(0);
        doAnswer(
                        call -> {
                            Collection<String> names = call.getArgument(0);
                            var rows = new ArrayList<SecObjPrivilege>();
                            if (names.contains("_lab")) {
                                rows.add(stray);
                                rows.addAll(stored);
                            }
                            return rows;
                        })
                .when(privileges)
                .findByObjectNames(any());
        var snapshot = security.readOnlySnapshot(info, names());

        // Exactly what the ordinary check decides from the same two rows.
        for (String right : RIGHTS)
            assertThat(snapshot.hasPrivilege("_lab", right, null))
                    .as(right)
                    .isEqualTo(security.hasPrivilegeReadOnly(info, "_lab", right, null));
        assertThat(snapshot.hasPrivilege("_lab", "r", null)).isTrue();
    }

    @Test
    @DisplayName("should answer each question from the database when an implementation has no snapshot")
    void shouldAnswerFromTheDatabaseEachTime_forTheInterfaceDefault() {
        SecurityInfoManager plain = mock(SecurityInfoManager.class);
        when(plain.readOnlySnapshot(any(), any())).thenCallRealMethod();
        when(plain.hasPrivilegeReadOnly(info, "_lab", "r", "10")).thenReturn(true);
        when(plain.isAllowedAccessToPatientRecord(info, 10)).thenReturn(true);
        var snapshot = plain.readOnlySnapshot(info, List.of("_lab"));
        clearInvocations(plain);

        assertThat(snapshot.hasPrivilege("_lab", "r", "10")).isTrue();
        assertThat(snapshot.hasPrivilege("_lab", "r", "10")).isTrue();
        assertThat(snapshot.hasPrivilege("_lab", "w", "10")).isFalse();
        assertThat(snapshot.isAllowedAccessToPatientRecord(10)).isTrue();

        verify(plain, times(2)).hasPrivilegeReadOnly(info, "_lab", "r", "10");
        verify(plain, times(1)).isAllowedAccessToPatientRecord(info, 10);
    }
}
