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
package io.github.carlos_emr.carlos.commn.model;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import jakarta.persistence.Column;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what {@link SecurityArchive#SecurityArchive(Security)} copies out of a security record
 * (issue #5120): every archived setting including the MFA on/off flag, and never the MFA secret.
 *
 * @since 2026-10-22
 */
@DisplayName("SecurityArchive copy of a security record")
@Tag("unit")
@Tag("fast")
@Tag("security")
class SecurityArchiveUnitTest {

    private static Security record(boolean usingMfa) {
        Security security = new Security();
        security.setId(42);
        security.setUserName("fakeuser");
        security.setPassword("not-a-real-hash");
        security.setProviderNo("999990");
        security.setPin("not-a-real-pin-hash");
        security.setBExpireset(1);
        security.setDateExpiredate(new Date(1_800_000_000_000L));
        security.setBLocallockset(0);
        security.setBRemotelockset(1);
        security.setForcePasswordReset(Boolean.FALSE);
        security.setPasswordUpdateDate(new Date(1_700_000_000_000L));
        security.setPinUpdateDate(new Date(1_700_000_100_000L));
        security.setLastUpdateUser("999998");
        security.setLastUpdateDate(new Date(1_700_000_200_000L));
        security.setUsingMfa(usingMfa);
        security.setMfaSecret("not-a-real-mfa-secret");
        return security;
    }

    @Test
    void shouldArchiveMfaOn_whenTheRecordUsesMfa() {
        assertThat(new SecurityArchive(record(true)).getUsingMfa()).isTrue();
    }

    @Test
    void shouldArchiveMfaOff_whenTheRecordDoesNotUseMfa() {
        assertThat(new SecurityArchive(record(false)).getUsingMfa()).isFalse();
    }

    @Test
    void shouldCopyEveryOtherArchivedSetting_fromTheRecord() {
        Security source = record(true);

        SecurityArchive archive = new SecurityArchive(source);

        assertThat(archive.getSecurityNo()).isEqualTo(42);
        assertThat(archive.getUserName()).isEqualTo("fakeuser");
        assertThat(archive.getPassword()).isEqualTo("not-a-real-hash");
        assertThat(archive.getProviderNo()).isEqualTo("999990");
        assertThat(archive.getPin()).isEqualTo("not-a-real-pin-hash");
        assertThat(archive.getBExpireset()).isEqualTo(1);
        assertThat(archive.getDateExpiredate()).isEqualTo(source.getDateExpiredate());
        assertThat(archive.getBLocallockset()).isZero();
        assertThat(archive.getBRemotelockset()).isEqualTo(1);
        assertThat(archive.getForcePasswordReset()).isFalse();
        assertThat(archive.getPasswordUpdateDate()).isEqualTo(source.getPasswordUpdateDate());
        assertThat(archive.getPinUpdateDate()).isEqualTo(source.getPinUpdateDate());
        assertThat(archive.getLastUpdateUser()).isEqualTo("999998");
        assertThat(archive.getLastUpdateDate()).isEqualTo(source.getLastUpdateDate());
    }

    @Test
    void shouldLeaveMfaUnrecorded_forAnEmptyArchiveRow() {
        // NULL is what rows archived before V1.0.59 hold: their MFA setting was never kept.
        assertThat(new SecurityArchive().getUsingMfa()).isNull();
    }

    @Test
    void shouldMapTheMfaFlagToTheSecurityTablesColumnName_forTheArchiveTable() throws Exception {
        Column column = SecurityArchive.class.getDeclaredField("usingMfa").getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("usingMfa");
    }

    @Test
    void shouldHaveNoPlaceForTheMfaSecret_inTheArchiveEntity() {
        // The archive keeps old password and PIN hashes for history checks, but the MFA secret
        // must never be copied: a new field for it would put a live second factor in the archive.
        assertThat(Arrays.stream(SecurityArchive.class.getDeclaredFields())
                .map(SecurityArchiveUnitTest::persistedName)
                .map(name -> name.toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.contains("secret"));
    }

    private static String persistedName(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column != null && !column.name().isEmpty() ? field.getName() + "/" + column.name() : field.getName();
    }
}
