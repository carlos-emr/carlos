/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.signature.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * {@link SaveSignatureUpload2Action} accepts a signature-pad upload only for a key issued to the
 * uploading session by the page that opened the pad
 * ({@link DigitalSignatureUtils#generateSignatureRequestId}). On this release line
 * {@code SaveSignatureUpload2ActionTest} is on the Surefire unmatched baseline, so CI never runs it;
 * these cases live here, in a class CI runs.
 */
@DisplayName("SaveSignatureUpload2Action issued-key gate")
@Tag("unit")
@Tag("signature")
class SaveSignatureUpload2ActionIssuedKeyUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private DigitalSignatureManager mockDigitalSignatureManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private SaveSignatureUpload2Action action;
    private String issuedKey;
    private String otherSessionsKey;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");
        // Issued as the page that opens the pad issues it: to this request's session.
        issuedKey = DigitalSignatureUtils.generateSignatureRequestId(mockRequest.getSession());
        otherSessionsKey = DigitalSignatureUtils.generateSignatureRequestId(new MockHttpSession());

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DigitalSignatureManager.class, mockDigitalSignatureManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull()))
                .thenReturn(true);

        action = new SaveSignatureUpload2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        for (String key : new String[] {issuedKey, otherSessionsKey}) {
            if (key != null) new File(DigitalSignatureUtils.getTempFilePath(key)).delete();
        }
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000000000000000000000000", "9999981700000000000"})
    @DisplayName("should return 403 and write nothing when the key was never issued")
    void shouldReturn403AndWriteNothing_whenKeyWasNeverIssued(String unknownKey) throws Exception {
        mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, unknownKey);
        mockRequest.setContent(new byte[] {1, 2, 3});

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(new File(DigitalSignatureUtils.getTempFilePath(unknownKey))).doesNotExist();
        verifyNoInteractions(mockDigitalSignatureManager);
    }

    @Test
    @DisplayName("should return 403, write nothing and save nothing when the key belongs to another session")
    void shouldReturn403AndSaveNothing_whenKeyBelongsToAnotherSession() throws Exception {
        mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, otherSessionsKey);
        mockRequest.setParameter("saveToDB", "true");
        mockRequest.setParameter("demographicNo", "1");
        mockRequest.setContent(new byte[] {1, 2, 3});

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(new File(DigitalSignatureUtils.getTempFilePath(otherSessionsKey))).doesNotExist();
        verifyNoInteractions(mockDigitalSignatureManager);
    }

    @Test
    @DisplayName("should write the capture when the key was issued to this session")
    void shouldWriteCapture_whenKeyWasIssuedToThisSession() throws Exception {
        mockRequest.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, issuedKey);
        byte[] payload = "FAKE signature capture".getBytes(StandardCharsets.US_ASCII);
        mockRequest.setContent(payload);

        action.execute();

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        File capture = new File(DigitalSignatureUtils.getTempFilePath(issuedKey));
        assertThat(Files.readAllBytes(capture.toPath())).isEqualTo(payload);
    }
}
