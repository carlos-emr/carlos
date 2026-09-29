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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SmsPatientRestrictionLookup}: which object names count as an entry for one patient.
 *
 * @since 2026-09-29
 */
@Tag("unit")
@Tag("security")
@DisplayName("SMS patient restriction lookup")
class SmsPatientRestrictionLookupUnitTest {
    private static final String DEMOGRAPHIC_PATTERN = "_demographic$%";
    private static final String ECHART_PATTERN = "_eChart$%";

    private final SecObjPrivilegeDao dao = mock(SecObjPrivilegeDao.class);
    private final SmsPatientRestrictionLookup lookup = new SmsPatientRestrictionLookup(dao);

    @Test
    @DisplayName("should return the patients named by _demographic$ and _eChart$ entries")
    void shouldReturnPatients_whenEntriesUseEitherPrefix() {
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN)).thenReturn(entries("_demographic$5", "_demographic$12"));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("_eChart$7"));

        assertThat(lookup.patientsWithOwnEntries()).containsExactly(5, 7, 12);
        verify(dao).findByObjectName(DEMOGRAPHIC_PATTERN);
        verify(dao).findByObjectName(ECHART_PATTERN);
        verifyNoMoreInteractions(dao);
    }

    @Test
    @DisplayName("should return a patient once when several entries name them")
    void shouldReturnPatientOnce_whenSeveralEntriesNameThem() {
        // One entry per role or user, and the same patient under both prefixes.
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN))
                .thenReturn(entries("_demographic$5", "_demographic$5", "_demographic$6"));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("_eChart$5"));

        assertThat(lookup.patientsWithOwnEntries()).containsExactly(5, 6);
    }

    @Test
    @DisplayName("should trust the database's match for the prefix and read only the number after it")
    void shouldIncludeName_whenPrefixDiffersOnlyWhereTheDatabaseMatchedIt() {
        // The LIKE wildcard "_" matches any first character, and the collation ignores accents: the database
        // returned these names for the prefix, so each only costs a check if it names a patient too many.
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN)).thenReturn(entries("Xdemographic$5", "_dèmographic$6"));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("XeChart$8", "_ëChart$9"));

        assertThat(lookup.patientsWithOwnEntries()).containsExactly(5, 6, 8, 9);
    }

    @Test
    @DisplayName("should ignore a name that has no whole number right after the prefix's length")
    void shouldIgnoreName_whenNoNumberFollowsThePrefixLength() {
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN))
                .thenReturn(entries("_demographicX$6", "_demographic", "_demographic$", "_demographic.other$7"));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("_eChartX$9", "_eChart"));

        assertThat(lookup.patientsWithOwnEntries()).isEmpty();
    }

    @Test
    @DisplayName("should ignore a name whose ending is not a whole number that fits an int")
    void shouldIgnoreName_whenSuffixIsNotAWholeNumber() {
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN)).thenReturn(entries(
                "_demographic$abc", "_demographic$-5", "_demographic$+5", "_demographic$5a", "_demographic$5.0",
                "_demographic$ 5", "_demographic$5,6", "_demographic$99999999999", "_demographic$4294967301",
                "_demographic$2147483648", "_demographic$٥"));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("_eChart$-1", "_eChart$x"));

        assertThat(lookup.patientsWithOwnEntries()).isEmpty();
    }

    @Test
    @DisplayName("should include zero and the largest int, the ends of what a demographic number can be")
    void shouldIncludeName_whenNumberIsZeroOrTheLargestInt() {
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN)).thenReturn(entries("_demographic$0"));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("_eChart$2147483647"));

        assertThat(lookup.patientsWithOwnEntries()).containsExactly(0, Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("should skip an entry without a name and still return the others")
    void shouldSkipEntry_whenItHasNoName() {
        SecObjPrivilege noId = new SecObjPrivilege();
        SecObjPrivilege noName = new SecObjPrivilege();
        noName.setId(new SecObjPrivilegePrimaryKey("doctor", null));
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN))
                .thenReturn(Arrays.asList(noId, noName, entry("_demographic$5")));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(List.of());

        assertThat(lookup.patientsWithOwnEntries()).containsExactly(5);
    }

    @Test
    @DisplayName("should count a name the database treats as the same: other letter case or spaces at the end")
    void shouldReturnPatient_whenNameDiffersOnlyAsTheDatabaseIgnores() {
        when(dao.findByObjectName(DEMOGRAPHIC_PATTERN)).thenReturn(entries("_DEMOGRAPHIC$5", "_demographic$6  "));
        when(dao.findByObjectName(ECHART_PATTERN)).thenReturn(entries("_echart$7"));

        assertThat(lookup.patientsWithOwnEntries()).containsExactly(5, 6, 7);
    }

    @Test
    @DisplayName("should return nothing when there are no per-patient entries")
    void shouldReturnNothing_whenThereAreNoEntries() {
        when(dao.findByObjectName(anyString())).thenReturn(List.of());

        assertThat(lookup.patientsWithOwnEntries()).isEmpty();
    }

    @Test
    @DisplayName("should let a failed read through, so the page fails instead of showing everything")
    void shouldFail_whenEntriesCannotBeRead() {
        when(dao.findByObjectName(anyString())).thenThrow(new IllegalStateException("synthetic read failure"));

        assertThatThrownBy(lookup::patientsWithOwnEntries).isInstanceOf(IllegalStateException.class);
    }

    private static List<SecObjPrivilege> entries(String... objectNames) {
        return Arrays.stream(objectNames).map(SmsPatientRestrictionLookupUnitTest::entry).toList();
    }

    private static SecObjPrivilege entry(String objectName) {
        SecObjPrivilege entry = new SecObjPrivilege();
        entry.setId(new SecObjPrivilegePrimaryKey("doctor", objectName));
        return entry;
    }
}
