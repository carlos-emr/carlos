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
package io.github.carlos_emr.carlos.admin.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceClientDao;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for admin JSON endpoints that replaced legacy JSP-backed Ajax
 * handlers.
 *
 * @since 2026-05-21
 */
@Tag("unit")
@Tag("admin")
@DisplayName("Admin JSON 2Action unit tests")
class AdminJsonActionsUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ServiceClientDao serviceClientDao;
    private ServiceAccessTokenDao serviceAccessTokenDao;
    private CarlosMethodSecurity methodSecurity;
    private SecurityInfoManager securityInfoManager;
    private PublicKeyDao publicKeyDao;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        serviceClientDao = mock(ServiceClientDao.class);
        serviceAccessTokenDao = mock(ServiceAccessTokenDao.class);
        methodSecurity = mock(CarlosMethodSecurity.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        publicKeyDao = mock(PublicKeyDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(ServiceClientDao.class, serviceClientDao);
        registerMock(ServiceAccessTokenDao.class, serviceAccessTokenDao);
        registerMock(CarlosMethodSecurity.class, methodSecurity);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PublicKeyDao.class, publicKeyDao);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoStatic != null) {
            loggedInInfoStatic.close();
        }
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    @Test
    @DisplayName("should allow read privilege for client list")
    void shouldAllowReadPrivilege_forClientList() throws Exception {
        request.setMethod("GET");
        request.addParameter("method", "list");
        when(methodSecurity.hasPrivilege("_admin", "r")).thenReturn(true);
        when(serviceClientDao.findAll()).thenReturn(List.of(client()));

        String result = new ClientManage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(methodSecurity, never()).hasAdminWrite();
        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get(0).get("name").asText()).isEqualTo("client");
    }

    @Test
    @DisplayName("should omit client secret from list response")
    void shouldOmitClientSecret_fromListResponse() throws Exception {
        request.setMethod("GET");
        request.addParameter("method", "list");
        when(methodSecurity.hasPrivilege("_admin", "r")).thenReturn(true);
        when(serviceClientDao.findAll()).thenReturn(List.of(client()));

        new ClientManage2Action().execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString()).get(0);
        assertThat(json.has("secret")).isFalse();
        assertThat(json.get("key").asText()).isEqualTo("client-key");
    }

    @Test
    @DisplayName("should omit token credentials from listTokens response")
    void shouldOmitTokenCredentials_fromListTokensResponse() throws Exception {
        request.setMethod("GET");
        request.addParameter("method", "listTokens");
        when(methodSecurity.hasPrivilege("_admin", "r")).thenReturn(true);
        when(serviceAccessTokenDao.findAll()).thenReturn(List.of(accessToken()));

        new ClientManage2Action().execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString()).get(0);
        assertThat(json.has("tokenId")).isFalse();
        assertThat(json.has("tokenSecret")).isFalse();
        assertThat(json.get("id").asInt()).isEqualTo(42);
    }

    @Test
    @DisplayName("should reject GET for client add mutation")
    void shouldRejectGet_forClientAddMutation() throws Exception {
        request.setMethod("GET");
        request.addParameter("method", "add");
        when(methodSecurity.hasAdminWrite()).thenReturn(true);

        String result = new ClientManage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(serviceClientDao, serviceAccessTokenDao);
    }

    @Test
    @DisplayName("should return delete failure for empty client id")
    void shouldReturnDeleteFailure_forEmptyClientId() throws Exception {
        request.setMethod("POST");
        request.addParameter("method", "delete");
        when(methodSecurity.hasAdminWrite()).thenReturn(true);

        new ClientManage2Action().execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("error").asText()).startsWith("Delete Failure:");
    }

    @Test
    @DisplayName("should return bad request for missing public key id")
    void shouldReturnBadRequest_forMissingPublicKeyId() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), nullable(String.class)))
                .thenReturn(true);

        String result = new GetPublicKey2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("error").asText()).contains("required");
    }

    @Test
    @DisplayName("should require write privilege for public key details")
    void shouldRequireWritePrivilege_forPublicKeyDetails() {
        request.addParameter("id", "service");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), nullable(String.class)))
                .thenReturn(false);

        GetPublicKey2Action action = new GetPublicKey2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
        verifyNoInteractions(publicKeyDao);
    }

    @Test
    @DisplayName("should return not found for unknown public key id")
    void shouldReturnNotFound_forUnknownPublicKeyId() throws Exception {
        request.addParameter("id", "unknown");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), nullable(String.class)))
                .thenReturn(true);

        new GetPublicKey2Action().execute();

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("error").asText()).contains("not found");
    }

    @Test
    @DisplayName("should return public key details for known public key id")
    void shouldReturnPublicKeyDetails_forKnownPublicKeyId() throws Exception {
        request.addParameter("id", "service");
        PublicKey publicKey = new PublicKey();
        publicKey.setService("service");
        publicKey.setType("type");
        publicKey.setBase64EncodedPrivateKey("private-key");
        publicKey.setMatchingProfessionalSpecialistId(7);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), nullable(String.class)))
                .thenReturn(true);
        when(publicKeyDao.find("service")).thenReturn(publicKey);

        new GetPublicKey2Action().execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("base64EncodedPrivateKey").asText()).isEqualTo("private-key");
        assertThat(json.get("matchingProfessionalSpecialistId").asInt()).isEqualTo(7);
        assertThat(response.getHeader("Cache-Control")).contains("no-store").contains("no-cache");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        logActionMock.verify(() -> LogAction.addLog(
                eq(loggedInInfo),
                eq(LogConst.READ),
                eq("PublicKey"),
                eq("service"),
                eq(""),
                eq("private key accessed via API")));
    }

    private static ServiceClient client() {
        ServiceClient client = new ServiceClient();
        client.setId(1);
        client.setName("client");
        client.setKey("client-key");
        client.setSecret("secret");
        client.setUri("https://example.invalid");
        client.setLifetime(3600);
        return client;
    }

    private static ServiceAccessToken accessToken() {
        ServiceAccessToken token = new ServiceAccessToken();
        token.setId(42);
        token.setClientId(1);
        token.setTokenId("token-id");
        token.setTokenSecret("token-secret");
        token.setLifetime(3600);
        token.setIssued(1234);
        token.setProviderNo("999999");
        return token;
    }
}
