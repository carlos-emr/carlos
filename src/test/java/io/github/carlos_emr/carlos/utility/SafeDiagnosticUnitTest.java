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
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxDsMessageTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.HashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

/** Regression tests for credential and patient-data boundaries in diagnostic strings. */
@Tag("unit")
@Tag("security")
class SafeDiagnosticUnitTest {
    private static final String SENSITIVE = "FAKE-SENSITIVE-MARKER\r\nforged-event";

    @Test
    void shouldPreserveInteractionFields_withoutFormattingInputMap() {
        HashMap<String, Object> interaction = new HashMap<>() {
            @Override public String toString() { throw new AssertionError("interaction dump"); }
        };
        interaction.put("name", SENSITIVE);
        interaction.put("body", SENSITIVE);
        RxDsMessageTo1 message = new RxDsMessageTo1(interaction, null, Locale.CANADA);
        assertThat(message.getName()).isEqualTo(SENSITIVE);
        assertThat(message.getBody()).isEqualTo(SENSITIVE);
        assertThat(message.toString()).doesNotContain(SENSITIVE);
    }

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
        assertThat(new LoggedInInfo())
                .hasToString("LoggedInInfo[providerNo=null, facilityId=null]");
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
        assertThat(value.toString()).isNotBlank().doesNotContain(SENSITIVE, "FAKE-SENSITIVE", "forged-event");
    }

    static Stream<Object> patientObjects() {
        return Stream.of(new Demographic(123), new Drug(), new CaseManagementCPP(),
                new EDoc(), new NoteTo1(), new DemographicTransfer());
    }

    @ParameterizedTest
    @MethodSource("formerlyReflectiveTypes")
    void shouldOmitPopulatedFields_forEveryFormerReflectiveDiagnostic(String typeName) throws Exception {
        Object value = mock(Class.forName(typeName), CALLS_REAL_METHODS);
        doCallRealMethod().when(value).toString();
        fillStrings(value);
        assertThat(value.toString()).isNotBlank().doesNotContain(SENSITIVE, "FAKE-SENSITIVE", "forged-event");
    }

    static Stream<String> formerlyReflectiveTypes() {
        return Stream.of(
                "io.github.carlos_emr.carlos.billing.CA.filters.CodeFilter",
                "io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.WCBCodes",
                "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.WCBForm",
                "io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction$IssueDisplay",
                "io.github.carlos_emr.carlos.commn.model.AbstractModel",
                "io.github.carlos_emr.carlos.commn.model.Clinic",
                "io.github.carlos_emr.carlos.commn.model.CtlDocumentPK",
                "io.github.carlos_emr.carlos.commn.model.Drug",
                "io.github.carlos_emr.carlos.commn.model.FormBPMH",
                "io.github.carlos_emr.carlos.commn.model.OscarJobType",
                "io.github.carlos_emr.carlos.dashboard.display.beans.DashboardBean",
                "io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean",
                "io.github.carlos_emr.carlos.dashboard.display.beans.GraphPlot",
                "io.github.carlos_emr.carlos.dashboard.display.beans.IndicatorBean",
                "io.github.carlos_emr.carlos.dashboard.display.beans.IndicatorPanelBean",
                "io.github.carlos_emr.carlos.dashboard.display.beans.PanelBean",
                "io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML",
                "io.github.carlos_emr.carlos.dashboard.query.Column",
                "io.github.carlos_emr.carlos.dashboard.query.DrillDownAction",
                "io.github.carlos_emr.carlos.dashboard.query.Parameter",
                "io.github.carlos_emr.carlos.dashboard.query.RangeLowerLimit",
                "io.github.carlos_emr.carlos.dashboard.query.RangeUpperLimit",
                "io.github.carlos_emr.carlos.documentManager.EDoc",
                "io.github.carlos_emr.carlos.encounter.data.EctProviderData",
                "io.github.carlos_emr.carlos.entities.Billingmaster",
                "io.github.carlos_emr.carlos.entities.WCB",
                "io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug",
                "io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean",
                "io.github.carlos_emr.carlos.form.util.FormTransportContainer",
                "io.github.carlos_emr.carlos.integration.fhir.builder.ReferenceKey",
                "io.github.carlos_emr.carlos.integration.fhir.model.Destination",
                "io.github.carlos_emr.carlos.integration.fhir.model.Sender",
                "io.github.carlos_emr.carlos.integration.fhir.resources.Settings",
                "io.github.carlos_emr.carlos.model.BaseObject",
                "io.github.carlos_emr.carlos.prescript.data.RxDrugData$DrugMonograph",
                "io.github.carlos_emr.carlos.prescript.data.RxDrugData$DrugMonograph$DrugComponent",
                "io.github.carlos_emr.carlos.prescript.data.RxDrugData$DrugSearch",
                "io.github.carlos_emr.carlos.prescript.data.RxDrugData$MinDrug",
                "io.github.carlos_emr.carlos.prescript.data.RxDrugData$Tag",
                "io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData",
                "io.github.carlos_emr.carlos.utility.LoggedInInfo",
                "io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1",
                "io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionSearchTo1",
                "io.github.carlos_emr.carlos.webserv.rest.to.model.PrintRxTo1",
                "io.github.carlos_emr.carlos.webserv.rest.to.model.RxDsMessageTo1",
                "io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.AllergyTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer2",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.DocumentTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.FacilityTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.MeasurementMapTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.MeasurementTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.PrescriptionTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.PreventionTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.ProgramProviderTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.ProgramTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderPropertyTransfer",
                "io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer");
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
