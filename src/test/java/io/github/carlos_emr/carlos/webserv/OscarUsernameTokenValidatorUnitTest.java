/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.message.token.UsernameToken;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class OscarUsernameTokenValidatorUnitTest extends CarlosUnitTestBase {
    @BeforeAll
    static void initializeXmlSecurity() { org.apache.xml.security.Init.init(); }
    @Test
    void shouldAuthenticatePersistedTokens_throughTheWsSecurityValidatorBoundary() throws Exception {
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        var passwords = mock(SecurityManager.class);
        registerMock(SecurityManager.class, passwords);
        var tokens = mock(SoapTokenService.class);
        registerMock(SoapTokenService.class, tokens);
        var accounts = mock(SecurityDao.class);
        var account = new Security();
        account.setId(41);
        account.setPassword("FAKE-hash");
        when(accounts.find(Integer.valueOf(41))).thenReturn(account);
        String value = "ct1." + "A".repeat(43);
        when(tokens.validate(account, value)).thenReturn(true);
        var token = mock(UsernameToken.class);
        when(token.getName()).thenReturn("41");
        when(token.getPassword()).thenReturn(value);
        var request = new MockHttpServletRequest();
        var message = new SoapMessage(new MessageImpl());
        message.put(AbstractHTTPDestination.HTTP_REQUEST, request);
        var data = new RequestData();
        data.setMsgContext(message);
        var validator = new OscarUsernameTokenValidator();
        injectDependency(validator, "securityDao", accounts);
        validator.verifyPlaintextPassword(token, data);
        assertThat(LoggedInInfo.getLoggedInInfoFromRequest(request).getLoggedInSecurity()).isSameAs(account);
        verifyNoInteractions(passwords);
        verify(tokens, never()).issue(any());
    }

    @Test
    void shouldRejectMalformedAccountIdentifiers_withoutLoggingTheirRawText() {
        var accounts = mock(SecurityDao.class);
        var validator = new OscarUsernameTokenValidator();
        injectDependency(validator, "securityDao", accounts);
        var token = mock(UsernameToken.class);
        when(token.getName()).thenReturn("FAKE-PRIVATE-CREDENTIAL");
        try (LogCapture logs = LogCapture.forLogger(OscarUsernameTokenValidator.class)) {
            assertThatThrownBy(() -> validator.verifyPlaintextPassword(token, new RequestData()))
                    .isInstanceOf(WSSecurityException.class);
            assertThat(logs.messages()).contains("SOAP username-token account identifier is invalid")
                    .allSatisfy(message -> assertThat(message).doesNotContain("FAKE-PRIVATE-CREDENTIAL"));
        }
        verifyNoInteractions(accounts);
    }
}
