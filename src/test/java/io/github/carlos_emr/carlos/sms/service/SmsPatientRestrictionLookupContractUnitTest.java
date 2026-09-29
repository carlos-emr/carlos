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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Ties {@link SmsPatientRestrictionLookup} to how the real {@link SecurityInfoManagerImpl} restricts patients.
 * <p>
 * The SMS queue page checks access only for the patients the lookup names: those with an object name of
 * their own, {@code _demographic$<number>} or {@code _eChart$<number>}. That is safe only while
 * {@code SecurityInfoManagerImpl} gives a patient without such an entry the same answer as any other
 * patient, and looks up no other per-patient name. If a change to {@code SecurityInfoManagerImpl} breaks
 * these tests, {@code SmsPatientRestrictionLookup} must change with it, or the page could show a restricted
 * patient's messages.
 *
 * @since 2026-09-29
 */
@Tag("unit")
@Tag("security")
@DisplayName("SMS patient restriction lookup against SecurityInfoManagerImpl")
class SmsPatientRestrictionLookupContractUnitTest extends CarlosUnitTestBase {
    private static final String PROVIDER_NO = "999998";
    private static final String ROLE = "doctor";
    // Distinctive numbers, so a name that carries one can only have been built for that patient.
    private static final int PATIENT = 48271;
    private static final int OTHER_PATIENT = 48272;

    private final SecObjPrivilegeDao secObjPrivilegeDao = mock(SecObjPrivilegeDao.class);
    private final SecuserroleDao secUserRoleDao = mock(SecuserroleDao.class);
    // The manager's other privilege DAO. Nothing per patient may be read through it.
    private final SecobjprivilegeDao otherPrivilegeDao = mock(SecobjprivilegeDao.class);
    private final LoggedInInfo viewer = mock(LoggedInInfo.class);
    /** The security rows, by object name, that both the manager and the lookup read. */
    private final Map<String, List<SecObjPrivilege>> rows = new HashMap<>();
    /** Every object name the manager looked up. */
    private final List<String> namesLookedUp = new ArrayList<>();
    private SecurityInfoManagerImpl securityInfoManager;

    @BeforeEach
    void setUp() {
        // OscarRoleObjectPrivilege, which the manager reads privileges through, gets this DAO from SpringUtils.
        registerMock(SecObjPrivilegeDao.class, secObjPrivilegeDao);
        securityInfoManager = new SecurityInfoManagerImpl();
        injectDependency(securityInfoManager, "secUserRoleDao", secUserRoleDao);
        injectDependency(securityInfoManager, "secobjprivilegeDao", otherPrivilegeDao);

        when(viewer.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(viewer.getSession()).thenReturn(mock(HttpSession.class));
        Secuserrole role = new Secuserrole();
        role.setProviderNo(PROVIDER_NO);
        role.setRoleName(ROLE);
        when(secUserRoleDao.findActiveByProviderNo(PROVIDER_NO)).thenReturn(List.of(role));

        // What the manager reads: exact object names.
        when(secObjPrivilegeDao.findByObjectNames(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            namesLookedUp.addAll(names);
            return names.stream().flatMap(name -> rows.getOrDefault(name, List.of()).stream()).toList();
        });
        // What the lookup reads: names starting with a prefix (the "%" at the end of its LIKE pattern).
        when(secObjPrivilegeDao.findByObjectName(anyString())).thenAnswer(invocation -> {
            String prefix = invocation.<String>getArgument(0).replace("%", "");
            return rows.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .flatMap(entry -> entry.getValue().stream())
                    .toList();
        });
    }

    @ParameterizedTest(name = "general _demographic privilege \"{0}\" gives read {1}")
    @CsvSource({"'', false", "r, true", "w, true", "x, true", "o, false"})
    @DisplayName("should treat a patient without an entry of their own like any patient, whatever the general right")
    void shouldAnswerAsForAnyPatient_whenPatientHasNoEntryOfTheirOwn(String generalPrivilege, boolean readsDemographics) {
        if (!generalPrivilege.isEmpty()) {
            grant("_demographic", generalPrivilege);
        }
        // Another patient's entries say nothing about this one.
        grant("_demographic$" + OTHER_PATIENT, "o");
        grant("_eChart$" + OTHER_PATIENT, "o");

        assertThat(securityInfoManager.isAllowedAccessToPatientRecord(viewer, PATIENT)).isTrue();
        // Stated outright, not only compared: hasPrivilege answers false when it fails, so two failures would
        // otherwise compare equal.
        assertThat(securityInfoManager.hasPrivilege(
                viewer, "_demographic", SecurityInfoManager.READ, (String) null)).isEqualTo(readsDemographics);
        assertThat(securityInfoManager.hasPrivilege(viewer, "_demographic", SecurityInfoManager.READ, PATIENT))
                .isEqualTo(readsDemographics);
        assertThat(new SmsPatientRestrictionLookup(secObjPrivilegeDao).patientsWithOwnEntries())
                .doesNotContain(PATIENT);
    }

    @Test
    @DisplayName("should restrict a patient whose own _demographic entry gives no rights, and name them in the lookup")
    void shouldRestrictPatient_whenTheirOwnDemographicEntryGivesNoRights() {
        grant("_demographic", "r");
        grant("_demographic$" + PATIENT, "o");

        assertThat(securityInfoManager.isAllowedAccessToPatientRecord(viewer, PATIENT)).isFalse();
        assertThat(new SmsPatientRestrictionLookup(secObjPrivilegeDao).patientsWithOwnEntries())
                .contains(PATIENT);
    }

    @Test
    @DisplayName("should restrict a patient whose own _eChart entry gives no rights, and name them in the lookup")
    void shouldRestrictPatient_whenTheirOwnEChartEntryGivesNoRights() {
        grant("_demographic", "r");
        grant("_eChart$" + PATIENT, "o");

        assertThat(securityInfoManager.isAllowedAccessToPatientRecord(viewer, PATIENT)).isFalse();
        assertThat(new SmsPatientRestrictionLookup(secObjPrivilegeDao).patientsWithOwnEntries())
                .contains(PATIENT);
    }

    @Test
    @DisplayName("should look up no per-patient name other than those the lookup reads")
    void shouldLookUpOnlyPrefixedNames_whenCheckingOnePatient() {
        grant("_demographic", "r");

        securityInfoManager.isAllowedAccessToPatientRecord(viewer, PATIENT);
        securityInfoManager.hasPrivilege(viewer, "_demographic", SecurityInfoManager.READ, PATIENT);

        // Every name read is either the general object or a per-patient name the lookup reads, whatever form a
        // new per-patient name took (with or without "$").
        List<String> allowed = new ArrayList<>(List.of("_demographic"));
        SmsPatientRestrictionLookup.PREFIXES.forEach(prefix -> allowed.add(prefix + PATIENT));
        assertThat(namesLookedUp).isNotEmpty().allSatisfy(name -> assertThat(allowed).contains(name));
        assertThat(namesLookedUp).anySatisfy(name -> assertThat(name).contains(String.valueOf(PATIENT)));
        // And they were read only this way: no other finder, and not through the manager's other DAO.
        verify(secObjPrivilegeDao, atLeastOnce()).findByObjectNames(any());
        verifyNoMoreInteractions(secObjPrivilegeDao);
        verifyNoInteractions(otherPrivilegeDao);
    }

    /** A row for the viewer's role on {@code objectName}. */
    private void grant(String objectName, String privilege) {
        SecObjPrivilege row = new SecObjPrivilege();
        row.setId(new SecObjPrivilegePrimaryKey(ROLE, objectName));
        row.setPrivilege(privilege);
        row.setPriority(0);
        rows.computeIfAbsent(objectName, ignored -> new ArrayList<>()).add(row);
    }
}
