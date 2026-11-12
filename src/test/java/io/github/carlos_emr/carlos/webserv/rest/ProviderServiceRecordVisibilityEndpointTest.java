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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.OscarLogManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Who sees which part of a provider's record on {@code /providerService}: the provider themself and
 * administrators see all of it; anyone else gets the provider's number, names, specialty, status, team
 * and type, with contact, billing, licence and personal details left out.
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ProviderService provider record visibility")
class ProviderServiceRecordVisibilityEndpointTest extends CarlosRestTestBase {

    private static final String SELF = "201";
    private static final String OTHER = "202";
    /** Fields that only the provider and administrators see. */
    private static final List<String> WHOLE_RECORD_ONLY = List.of(
            "ohipNo", "billingNo", "rmaNo", "hsoNo", "practitionerNo", "email", "phone", "workPhone",
            "address", "comments", "dob", "sex");

    @Mock
    private ProviderDao mockProviderDao;
    @Mock
    private ProviderManager2 mockProviderManager;
    @Mock
    private OscarLogManager mockOscarLogManager;
    @Mock
    private DemographicManager mockDemographicManager;
    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        ProviderService service = new ProviderService();
        injectDependency(service, "providerDao", mockProviderDao);
        injectDependency(service, "providerManager", mockProviderManager);
        injectDependency(service, "oscarLogManager", mockOscarLogManager);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    /** A FAKE provider with every detail filled in. */
    private static Provider provider(String providerNo) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName("Fake" + providerNo);
        provider.setLastName("FAKE-Provider");
        provider.setSpecialty("FM");
        provider.setStatus("1");
        provider.setTeam("FAKE-Team");
        provider.setProviderType("doctor");
        provider.setProviderActivity("00");
        provider.setOhipNo("999" + providerNo);
        provider.setBillingNo("B" + providerNo);
        provider.setRmaNo("R" + providerNo);
        provider.setHsoNo("H" + providerNo);
        provider.setPractitionerNo("P" + providerNo);
        provider.setEmail("fake" + providerNo + "@example.invalid");
        provider.setPhone("555-0" + providerNo);
        provider.setWorkPhone("555-1" + providerNo);
        provider.setAddress("1 FAKE Street");
        provider.setComments("FAKE note");
        provider.setDob(new Date(0));
        provider.setSex("F");
        return provider;
    }

    @BeforeEach
    void signInAsSelf() {
        Provider self = provider(SELF);
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(self);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(SELF);
        when(mockProviderDao.getProvider(SELF)).thenReturn(provider(SELF));
        when(mockProviderDao.getProvider(OTHER)).thenReturn(provider(OTHER));
        when(mockProviderDao.getActiveProviders()).thenReturn(List.of(provider(SELF), provider(OTHER)));
    }

    private void grantAdminRead() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull())).thenReturn(true);
    }

    private static void assertSummary(JsonNode record, String providerNo) {
        assertThat(record.path("providerNo").asText()).isEqualTo(providerNo);
        assertThat(record.path("firstName").asText()).isEqualTo("Fake" + providerNo);
        assertThat(record.path("lastName").asText()).isEqualTo("FAKE-Provider");
        assertThat(record.path("specialty").asText()).isEqualTo("FM");
        for (String field : WHOLE_RECORD_ONLY) {
            JsonNode value = record.path(field);
            assertThat(value.isMissingNode() || value.isNull() || (value.isObject() && allNull(value)))
                    .as("%s of provider %s is left out (%s)", field, providerNo, value).isTrue();
        }
    }

    private static boolean allNull(JsonNode object) {
        for (JsonNode child : object) {
            if (!child.isNull()) {
                return false;
            }
        }
        return true;
    }

    private static void assertWholeRecord(JsonNode record, String providerNo) {
        assertThat(record.path("providerNo").asText()).isEqualTo(providerNo);
        assertThat(record.path("ohipNo").asText()).isEqualTo("999" + providerNo);
        assertThat(record.path("billingNo").asText()).isEqualTo("B" + providerNo);
        assertThat(record.path("email").asText()).isEqualTo("fake" + providerNo + "@example.invalid");
    }

    private JsonNode get(String path) {
        Response response = request().path(path).get();
        assertThat(response.getStatus()).as("GET %s", path).isEqualTo(200);
        return responseJson(response);
    }

    @Test
    void shouldAnswerTheSummary_forAnotherProvidersRecord() {
        assertSummary(get("/providerService/providerjson/" + OTHER), OTHER);
        assertSummary(get("/providerService/provider/" + OTHER), OTHER);
    }

    @Test
    void shouldAnswerTheWholeRecord_forTheCallersOwnRecord() {
        assertWholeRecord(get("/providerService/providerjson/" + SELF), SELF);
        assertWholeRecord(get("/providerService/provider/" + SELF), SELF);
        assertWholeRecord(get("/providerService/provider/me"), SELF);
    }

    @Test
    void shouldAnswerTheWholeRecord_forAdministrator() {
        grantAdminRead();

        assertWholeRecord(get("/providerService/providerjson/" + OTHER), OTHER);
        assertWholeRecord(get("/providerService/provider/" + OTHER), OTHER);
    }

    @Test
    void shouldListOthersAsSummaries_inTheProviderList() {
        JsonNode content = get("/providerService/providers_json").path("content");

        assertThat(content).hasSize(2);
        assertWholeRecord(content.get(0), SELF);
        assertSummary(content.get(1), OTHER);
    }

    @Test
    void shouldKeepTheProviderType_inListSummaries() {
        JsonNode other = get("/providerService/providers_json").path("content").get(1);

        assertThat(other.path("providerNo").asText()).isEqualTo(OTHER);
        assertThat(other.path("providerType").asText()).isEqualTo("00");
        assertThat(other.path("team").asText()).isEqualTo("FAKE-Team");
    }

    @Test
    void shouldListEveryoneWhole_forAdministrator() {
        grantAdminRead();

        JsonNode content = get("/providerService/providers_json").path("content");

        assertWholeRecord(content.get(0), SELF);
        assertWholeRecord(content.get(1), OTHER);
    }

    @Test
    void shouldAnswerSummaries_forProviderSearch() {
        when(mockProviderManager.search(any(LoggedInInfo.class), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of(provider(OTHER)));

        Response response = request().path("/providerService/providers/search").type("application/json")
                .post("{\"active\": true}");

        assertThat(response.getStatus()).isEqualTo(200);
        assertSummary(responseJson(response).path("content").get(0), OTHER);
    }

    @Test
    void shouldRefuseTheProviderNumberSuggestion_forNonAdministrator() {
        Response response = request().path("/providerService/suggestProviderNo").get();

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(mockProviderManager);
    }

    @Test
    void shouldRefuseTheTeamList_withoutTheAppointmentRight() {
        Response response = request().path("/providerService/getActiveTeams").get();

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(mockProviderManager);
    }

    @Test
    void shouldRefuse_whenNoPrincipalIsPresent() {
        mockServletRequest.removeAttribute(new LoggedInInfo().LOGGED_IN_INFO_KEY);
        mockServletRequest.setSession(null);

        assertThatThrownBy(() -> request().path("/providerService/providerjson/" + OTHER).get().close())
                .hasRootCauseInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mockSecurityInfoManager);
    }
}
