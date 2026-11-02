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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regression tests for credential and patient-data boundaries in diagnostic strings. */
@Tag("unit")
@Tag("security")
class SafeDiagnosticUnitTest {
    private static final String SENSITIVE = "FAKE-SENSITIVE-MARKER\r\nforged-event";

    @Test
    void shouldOmitAllCredentialFields_forSecurityRecord() throws Exception {
        Security security = new Security();
        fillStrings(security);
        security.setId(42);
        assertThat(security.toString()).isEqualTo("Security[id=42]")
                .doesNotContain(SENSITIVE, "password", "pin", "mfaSecret", "oneIdKey");
    }

    @Test
    void shouldAvoidSessionAndNestedDumps_forLoggedInInfo() {
        HttpSession session = mock(HttpSession.class);
        Security security = mock(Security.class);
        Provider provider = mock(Provider.class);
        Facility facility = mock(Facility.class);
        when(session.toString()).thenThrow(new AssertionError("session dump"));
        when(security.toString()).thenThrow(new AssertionError("security dump"));
        when(provider.toString()).thenThrow(new AssertionError("provider dump"));
        when(facility.toString()).thenThrow(new AssertionError("facility dump"));
        when(provider.getProviderNo()).thenReturn("999998\r\nforged-event");
        when(facility.getId()).thenReturn(7);
        LoggedInInfo info = new LoggedInInfo(session, facility, provider, security, Locale.CANADA);
        info.setIp(SENSITIVE);
        assertThat(info.toString()).isEqualTo("LoggedInInfo[providerNo="
                + LogSafe.sanitize("999998\r\nforged-event") + ", facilityId=7]")
                .doesNotContain("\r", "\n", SENSITIVE);
    }

    @Test
    void shouldSupportBackgroundThreads_withNoLoginContext() {
        assertThat(new LoggedInInfo().toString())
                .isEqualTo("LoggedInInfo[providerNo=null, facilityId=null]");
    }

    @Test
    void shouldAvoidHashCodeAndAssociations_forUnsavedModel() {
        AbstractModel<Integer> model = new AbstractModel<>() {
            @Override public Integer getId() { return null; }
            @Override public int hashCode() { throw new AssertionError("hash traversal"); }
        };
        assertThat(model.toString()).endsWith("[id=null]");
    }

    @Test
    void shouldOmitArbitraryKeys_forStringAndCompositeIdentifiers() {
        AbstractModel<Object> model = new AbstractModel<>() {
            @Override public Object getId() {
                return new Object() {
                    @Override public String toString() { throw new AssertionError("key dump"); }
                };
            }
        };
        AbstractModel<String> textKey = new AbstractModel<>() {
            @Override public String getId() { return SENSITIVE; }
        };
        assertThat(model.toString()).endsWith("[id=protected]");
        assertThat(textKey.toString()).endsWith("[id=protected]");
    }

    @ParameterizedTest
    @MethodSource("patientObjects")
    void shouldOmitPatientFields_forModelsAndTransferObjects(Object value) throws Exception {
        fillStrings(value);
        assertThat(value.toString()).doesNotContain(SENSITIVE, "FAKE-SENSITIVE", "forged-event");
    }

    static Stream<Object> patientObjects() {
        return Stream.of(new Demographic(123), new Drug(), new CaseManagementCPP(),
                new EDoc(), new NoteTo1(), new DemographicTransfer());
    }

    private static void fillStrings(Object value) throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())
                        && !Modifier.isFinal(field.getModifiers())) {
                    field.setAccessible(true);
                    field.set(value, SENSITIVE);
                }
            }
        }
    }
}
