/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilderConfig;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.User2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class McedtCredentialConsumerUnitTest extends CarlosUnitTestBase {
    private McedtCredentialService credentials;
    @BeforeEach void prepare() {credentials=mock(McedtCredentialService.class);registerMock(McedtCredentialService.class,credentials);registerMock(UserPropertyDAO.class,mock(UserPropertyDAO.class));}

    @Test
    void shouldConfirmPasswordChange_afterCredentialCommit() throws Exception {
        var request=new MockHttpServletRequest();
        try(MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());verify(credentials).savePassword("FAKE-input");assertEquals("true",request.getSession().getAttribute("isPassChange"));
        }
    }
    @Test
    void shouldExposeFailureState_withoutEchoingCredential() throws Exception {
        var request=new MockHttpServletRequest();doThrow(new IllegalStateException("FAKE-sensitive-cause")).when(credentials).savePassword(anyString());
        try(MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());assertEquals("false",request.getSession().getAttribute("isPassChange"));assertFalse(request.getSession().getAttribute("isPassChange").toString().contains("FAKE"));
        }
    }
    @Test
    void shouldSupplyDecryptedCredential_toActualDelegateBuilder() throws Exception {
        when(credentials.getPassword("FAKE-fallback")).thenReturn("FAKE-decrypted");var props=mock(CarlosProperties.class);when(props.getProperty("mcedt.service.pass")).thenReturn("FAKE-fallback");var observed=new AtomicReference<EdtClientBuilderConfig>();
        try(var properties=mockStatic(CarlosProperties.class);var builders=mockConstruction(EdtClientBuilder.class,(builder,context)->observed.set((EdtClientBuilderConfig)context.arguments().get(0)))) {
            properties.when(CarlosProperties::getInstance).thenReturn(props);Method method=DelegateFactory.class.getDeclaredMethod("newDelegate",String.class);method.setAccessible(true);method.invoke(null,"FAKE-local-service");assertEquals("FAKE-decrypted",observed.get().getUserNameTokenPassword());verify(credentials).getPassword("FAKE-fallback");
        }
    }
    @Test
    void shouldStopDelegateCreation_whenCredentialCannotBeRead() throws Exception {
        when(credentials.getPassword(any())).thenThrow(new IllegalStateException("static failure"));
        try(var properties=mockStatic(CarlosProperties.class);var builders=mockConstruction(EdtClientBuilder.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));Method method=DelegateFactory.class.getDeclaredMethod("newDelegate",String.class);method.setAccessible(true);assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,"FAKE-local-service"));assertTrue(builders.constructed().isEmpty());
        }
    }
}
