/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Key Pair Creation ({@code admin/ViewKeygenCreateKey}, #6055): a request that names a service
 * stores a key pair, so only a POST may; the created pair is the whole response and no page is
 * rendered after it.
 */
@Tag("unit")
@Tag("security")
@Tag("admin")
@DisplayName("ViewKeygenCreateKey2Action")
class ViewKeygenCreateKey2ActionUnitTest extends CarlosUnitTestBase {

    private static final String SERVICE = "FAKE-lab.service_1";
    private static final String CLIENT_PRIVATE_KEY = "FAKEclientPrivateKeyBase64==";
    private static final String SERVER_PUBLIC_KEY = "FAKEserverPublicKeyBase64==";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<KeyPairGen> keys;

    @BeforeEach
    void prepare() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        user = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(user, "_admin", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenAnswer(inv -> response);
        // Every KeyPairGen call is stubbed or refused, so nothing reaches a DAO.
        keys = mockStatic(KeyPairGen.class);
    }

    @AfterEach
    void close() {
        if (keys != null) keys.close();
        if (servlet != null) servlet.close();
    }

    private String execute() throws Exception {
        return new ViewKeygenCreateKey2Action(security).execute();
    }

    private void post(String name, String type) {
        request.setMethod("POST");
        request.addParameter("name", name);
        if (type != null) request.addParameter("type", type);
    }

    private void assertNoKeyWork() {
        keys.verifyNoInteractions();
    }

    private void assertPageMessage(String expectedFragment, boolean error) {
        assertThat((String) request.getAttribute(ViewKeygenCreateKey2Action.MESSAGE_ATTRIBUTE)).contains(expectedFragment);
        assertThat(request.getAttribute(ViewKeygenCreateKey2Action.ERROR_ATTRIBUTE)).isEqualTo(error);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Nested
    @DisplayName("method and privilege")
    class MethodAndPrivilege {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "post"})
        @DisplayName("should refuse a request naming a service unless it is a POST, before any key lookup")
        void shouldRefuseNamedRequest_whenMethodIsNotPost(String method) throws Exception {
            request.setMethod(method);
            request.addParameter("name", SERVICE);
            request.addParameter("type", "FAKEPW");

            assertThat(execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertNoKeyWork();
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD"})
        @DisplayName("should refuse an empty name by GET too, since the parameter itself is the write")
        void shouldRefuseEmptyName_whenSentByGet(String method) throws Exception {
            request.setMethod(method);
            request.addParameter("name", "");

            assertThat(execute()).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertNoKeyWork();
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD", "POST"})
        @DisplayName("should open the empty form when no service is named")
        void shouldOpenForm_withoutName(String method) throws Exception {
            request.setMethod(method);

            assertThat(execute()).isEqualTo("success");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(request.getAttribute(ViewKeygenCreateKey2Action.MESSAGE_ATTRIBUTE)).isNull();
            assertNoKeyWork();
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "POST"})
        @DisplayName("should require _admin write before anything else")
        void shouldThrowSecurityException_withoutAdminWrite(String method) {
            request.setMethod(method);
            request.addParameter("name", SERVICE);
            request.addParameter("type", "FAKEPW");
            when(security.hasPrivilege(user, "_admin", "w", null)).thenReturn(false);

            assertThatThrownBy(ViewKeygenCreateKey2ActionUnitTest.this::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin)");
            assertNoKeyWork();
        }
    }

    @Nested
    @DisplayName("service key pair")
    class ServiceKeyPair {

        @BeforeEach
        void serverKeyExists() {
            keys.when(() -> KeyPairGen.checkName(anyString())).thenReturn(false);
            keys.when(KeyPairGen::getPublic).thenReturn(SERVER_PUBLIC_KEY);
            keys.when(() -> KeyPairGen.createKeys(anyString(), any())).thenReturn(CLIENT_PRIVATE_KEY);
        }

        @Test
        @DisplayName("should send the created pair as an uncacheable attachment and render nothing after it")
        void shouldStreamKeyFile_forCreatedServicePair() throws Exception {
            post(SERVICE, "FAKEPW");

            assertThat(execute()).isEqualTo("none");

            keys.verify(() -> KeyPairGen.createKeys(SERVICE, "FAKEPW"));
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).isEqualTo("-------- Service Name --------\n" + SERVICE + "\n------------------------------\n"
                    + "----- Client Private Key -----\n" + CLIENT_PRIVATE_KEY + "\n------------------------------\n"
                    + "------ Oscar Public Key ------\n" + SERVER_PUBLIC_KEY + "\n------------------------------");
            assertThat(response.getContentLength()).isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
            assertThat(response.getContentType()).startsWith("text/plain");
            assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=keyPair.key");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(request.getAttribute(ViewKeygenCreateKey2Action.MESSAGE_ATTRIBUTE)).isNull();
        }

        @Test
        @DisplayName("should store the typed message type when Other is chosen")
        void shouldUseOtherType_whenOtherIsChosen() throws Exception {
            post(SERVICE, "OTHER");
            request.addParameter("otherType", " FAKE-TYPE ");

            assertThat(execute()).isEqualTo("none");
            keys.verify(() -> KeyPairGen.createKeys(SERVICE, "FAKE-TYPE"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("should refuse Other without a typed message type and store nothing")
        void shouldRefuse_whenOtherTypeIsBlank(String otherType) throws Exception {
            post(SERVICE, "OTHER");
            request.addParameter("otherType", otherType);

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("specify the message type", true);
            keys.verify(() -> KeyPairGen.createKeys(anyString(), any()), never());
        }

        @Test
        @DisplayName("should refuse a request without a type and store nothing")
        void shouldRefuse_whenTypeIsMissing() throws Exception {
            post(SERVICE, null);

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("specify the message type", true);
            keys.verify(() -> KeyPairGen.createKeys(anyString(), any()), never());
        }

        @Test
        @DisplayName("should refuse a type longer than its column and store nothing")
        void shouldRefuse_whenTypeIsTooLong() throws Exception {
            post(SERVICE, "OTHER");
            request.addParameter("otherType", "T".repeat(ViewKeygenCreateKey2Action.MAX_COLUMN_LENGTH + 1));

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("at most 100 characters", true);
            keys.verify(() -> KeyPairGen.createKeys(anyString(), any()), never());
        }

        @Test
        @DisplayName("should refuse a service that already has a pair and store nothing")
        void shouldRefuse_whenServiceAlreadyHasPair() throws Exception {
            keys.when(() -> KeyPairGen.checkName(SERVICE)).thenReturn(true);
            post(SERVICE, "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("already been created for the service '" + SERVICE + "'", true);
            keys.verify(() -> KeyPairGen.createKeys(anyString(), any()), never());
        }

        @Test
        @DisplayName("should refuse before storing a pair when the server key pair is missing")
        void shouldRefuseWithoutStoring_whenServerKeyIsMissing() throws Exception {
            keys.when(KeyPairGen::getPublic).thenReturn(null);
            post(SERVICE, "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("CARLOS key pair has not been created yet", true);
            // Storing first used to leave a pair nobody received under a name that could not be reused.
            keys.verify(() -> KeyPairGen.createKeys(anyString(), any()), never());
        }

        @Test
        @DisplayName("should report a failed creation on the page")
        void shouldReportFailure_whenCreationFails() throws Exception {
            keys.when(() -> KeyPairGen.createKeys(anyString(), any())).thenReturn(null);
            post(SERVICE, "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("Could not create key pair", true);
        }

        @Test
        @DisplayName("should end the response quietly when the browser goes away mid-download")
        void shouldReturnNone_whenDownloadWriteFails() throws Exception {
            HttpServletResponse failing = mock(HttpServletResponse.class);
            when(failing.getOutputStream()).thenThrow(new IOException("FAKE connection reset"));
            servlet.when(ServletActionContext::getResponse).thenReturn(failing);
            post(SERVICE, "FAKEPW");

            try (LogCapture log = LogCapture.forLogger(ViewKeygenCreateKey2Action.class)) {
                assertThat(execute()).isEqualTo("none");
                assertThat(log.messages()).anySatisfy(m -> assertThat(m).contains("could not be sent"));
                assertThat(log.messages()).noneSatisfy(m -> assertThat(m).contains(CLIENT_PRIVATE_KEY));
                assertThat(log.messages()).noneSatisfy(m -> assertThat(m).contains(SERVICE));
            }
        }
    }

    @Nested
    @DisplayName("service name validation")
    class NameValidation {

        @ParameterizedTest
        @ValueSource(strings = {"bad name", "a/b", "<script>", "naïve", "x;y"})
        @DisplayName("should refuse a name with characters outside letters, digits, . _ -")
        void shouldRefuse_whenNameHasInvalidCharacters(String name) throws Exception {
            post(name, "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("only letters, numbers, periods, hyphens and underscores", true);
            assertNoKeyWork();
        }

        @Test
        @DisplayName("should refuse an empty name sent by POST")
        void shouldRefuse_whenNameIsEmpty() throws Exception {
            post("", "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("Please enter a service name", true);
            assertNoKeyWork();
        }

        @Test
        @DisplayName("should refuse a name longer than the service column")
        void shouldRefuse_whenNameIsTooLong() throws Exception {
            post("n".repeat(ViewKeygenCreateKey2Action.MAX_COLUMN_LENGTH + 1), "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("at most 100 characters", true);
            assertNoKeyWork();
        }
    }

    @Nested
    @DisplayName("server key pair (oscar)")
    class ServerKeyPair {

        @Test
        @DisplayName("should create the server pair once and report it on the page without a download")
        void shouldReportSuccess_forServerPair() throws Exception {
            keys.when(() -> KeyPairGen.checkName("oscar")).thenReturn(false);
            keys.when(() -> KeyPairGen.createKeys("oscar", null)).thenReturn("success");
            post("oscar", "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("CARLOS key pair created successfully", false);
            keys.verify(() -> KeyPairGen.createKeys("oscar", null));
        }

        @Test
        @DisplayName("should refuse to replace an existing server pair")
        void shouldRefuse_whenServerPairExists() throws Exception {
            keys.when(() -> KeyPairGen.checkName("oscar")).thenReturn(true);
            post("oscar", "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("oscar key pair has already been created", true);
            keys.verify(() -> KeyPairGen.createKeys(anyString(), any()), never());
        }

        @Test
        @DisplayName("should report a failed server pair creation")
        void shouldReportFailure_whenServerPairFails() throws Exception {
            keys.when(() -> KeyPairGen.checkName("oscar")).thenReturn(false);
            keys.when(() -> KeyPairGen.createKeys("oscar", null)).thenReturn(null);
            post("oscar", "FAKEPW");

            assertThat(execute()).isEqualTo("success");
            assertPageMessage("Could not create the oscar key pair", true);
        }
    }
}
