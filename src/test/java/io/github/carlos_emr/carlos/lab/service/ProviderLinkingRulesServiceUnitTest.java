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
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("lab")
@DisplayName("ProviderLinkingRulesService")
class ProviderLinkingRulesServiceUnitTest extends CarlosUnitTestBase {

    private static final String KEY = "provider_linking_rules";

    private PropertyDao propertyDao;
    private SecurityInfoManager security;
    private LoggedInInfo admin;
    private ProviderLinkingRulesService service;

    @BeforeEach
    void setUp() {
        propertyDao = mock(PropertyDao.class);
        security = mock(SecurityInfoManager.class);
        admin = mock(LoggedInInfo.class);
        service = new ProviderLinkingRulesService(propertyDao, security);
    }

    private static Property row(String providerNo, String value) {
        Property property = new Property();
        property.setName(KEY);
        property.setProviderNo(providerNo);
        property.setValue(value);
        return property;
    }

    @Test
    @DisplayName("should be off when no row exists, so no seed data is needed")
    void shouldBeDisabled_whenNoRowExists() {
        when(propertyDao.findByName(KEY)).thenReturn(List.of());
        assertThat(service.isEnabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "", "yes", "TRUE", "1"})
    @DisplayName("should be off for any value other than true")
    void shouldBeDisabled_forValuesOtherThanTrue(String value) {
        when(propertyDao.findByName(KEY)).thenReturn(List.of(row(null, value)));
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should read a global row stored with an empty provider as well as NULL")
    void shouldBeEnabled_whenGlobalRowHasEmptyProvider() {
        // property.provider_no defaults to '' in the schema, so a row seeded by SQL is not NULL.
        when(propertyDao.findByName(KEY)).thenReturn(List.of(row("", "true")));
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should ignore a provider-scoped row of the same name")
    void shouldIgnoreProviderScopedRow_whenReadingClinicSetting() {
        when(propertyDao.findByName(KEY)).thenReturn(List.of(row("999998", "true")));
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should refuse a change without _admin write and write nothing")
    void shouldThrowSecurityException_whenAdminWriteMissing() {
        assertThatThrownBy(() -> service.setEnabled(admin, true))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verify(propertyDao, never()).persist(any());
        verify(propertyDao, never()).merge(any());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should create the global row on first use and audit the change")
    void shouldCreateGlobalRow_whenNoneExists() {
        when(security.hasPrivilege(admin, "_admin", "w", null)).thenReturn(true);
        when(propertyDao.findByName(KEY)).thenReturn(List.of());

        assertThat(service.setEnabled(admin, true)).isTrue();

        ArgumentCaptor<Property> saved = ArgumentCaptor.forClass(Property.class);
        verify(propertyDao).persist(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo(KEY);
        assertThat(saved.getValue().getValue()).isEqualTo("true");
        assertThat(saved.getValue().getProviderNo()).isNull();
        logActionMock.verify(() -> LogAction.addLog(eq(admin), eq(LogConst.UPDATE),
                eq(ProviderLinkingRulesService.AUDIT_CONTENT), isNull(), isNull(), eq("enabled=true")));
    }

    // AbstractModel.equals compares ids and these unsaved rows have none, so rows are matched by
    // identity with same().
    @Test
    @DisplayName("should update every global row but never a provider's own row")
    void shouldUpdateEveryGlobalRow_whenRowsExist() {
        when(security.hasPrivilege(admin, "_admin", "w", null)).thenReturn(true);
        Property first = row(null, "true");
        Property duplicate = row("", "true");
        Property providerRow = row("999998", "true");
        when(propertyDao.findByName(KEY)).thenReturn(List.of(first, duplicate, providerRow));

        assertThat(service.setEnabled(admin, false)).isFalse();

        assertThat(first.getValue()).isEqualTo("false");
        assertThat(duplicate.getValue()).isEqualTo("false");
        assertThat(providerRow.getValue()).isEqualTo("true");
        verify(propertyDao).merge(same(first));
        verify(propertyDao).merge(same(duplicate));
        verify(propertyDao, never()).merge(same(providerRow));
        verify(propertyDao, never()).persist(any());
        logActionMock.verify(() -> LogAction.addLog(eq(admin), anyString(), anyString(), isNull(), isNull(),
                eq("enabled=false")));
    }
}
