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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import ca.ontario.health.edt.EDTDelegate;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated("Static MCEDT delegate cache")
@Execution(ExecutionMode.SAME_THREAD)
class McedtCredentialConsumerUnitTest extends CarlosUnitTestBase {
    private static final String AUDIT_ACTOR = "611";
    private McedtCredentialService credentials;
    private Map<String, EDTDelegate> delegates;
    private Map<String, byte[]> fingerprints;
    private Map<String, EDTDelegate> originalDelegates;
    private Map<String, byte[]> originalFingerprints;
    @SuppressWarnings("unchecked")
    private static <T> T cache(String name) throws Exception {
        Field field=DelegateFactory.class.getDeclaredField(name);field.setAccessible(true);return (T)field.get(null);
    }
    @BeforeEach void prepare() throws Exception {
        credentials=mock(McedtCredentialService.class);
        registerMock(McedtCredentialService.class,credentials);
        registerMock(UserPropertyDAO.class,mock(UserPropertyDAO.class));
        delegates=cache("edtDelegates");fingerprints=cache("credentialFingerprints");
        synchronized(delegates) {
            originalDelegates=new HashMap<>(delegates);originalFingerprints=new HashMap<>(fingerprints);
            delegates.clear();fingerprints.clear();
        }
    }
    @AfterEach void restoreCache() {
        if(delegates!=null)synchronized(delegates) {
            delegates.clear();delegates.putAll(originalDelegates);
            fingerprints.clear();fingerprints.putAll(originalFingerprints);
        }
    }

    private static MockHttpServletRequest authenticatedRequest() {
        var request = new MockHttpServletRequest();
        var actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn(AUDIT_ACTOR);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), actor);
        return request;
    }

    @Test
    void shouldConfirmPasswordChange_afterCredentialCommit() throws Exception {
        var request=authenticatedRequest();
        try(MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());verify(credentials).savePassword("FAKE-input", AUDIT_ACTOR);assertEquals("true",request.getSession().getAttribute("isPassChange"));
        }
    }
    @Test
    void shouldExposeFailureState_withoutEchoingCredential() throws Exception {
        var request=authenticatedRequest();
        doThrow(new IllegalStateException("FAKE-sensitive-cause")).when(credentials).savePassword(anyString(), eq(AUDIT_ACTOR));
        try(MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class);
                var log=LogCapture.forLogger(User2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());assertEquals("false",request.getSession().getAttribute("isPassChange"));
            assertEquals(List.of("MCEDT password change failed; credential storage was not updated"), log.messages());
            assertNull(log.events().getFirst().getThrown());
        }
    }

    @Test
    void shouldRetainTheSuccessOutcome_whenLogoutInvalidatesFeedbackAfterCommit() throws Exception {
        var request = authenticatedRequest();
        var session = request.getSession(false);
        doAnswer(call -> {
            session.invalidate();
            return null;
        }).when(credentials).savePassword("FAKE-input", AUDIT_ACTOR);
        try (var servlet = mockStatic(ServletActionContext.class);
                var log = LogCapture.forLogger(User2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            var action = new User2Action();
            action.setPassword("FAKE-input");
            assertEquals("success", action.changePassword());
            verify(credentials).savePassword("FAKE-input", AUDIT_ACTOR);
            assertTrue(log.events().isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRefuseCredentialChanges_whenTheSessionHasNoAuditActor(boolean hasLogin) {
        var request = new MockHttpServletRequest();
        if (hasLogin) LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            assertThrows(SecurityException.class, new User2Action()::changePassword);
            verifyNoInteractions(credentials);
        }
    }
    @Test
    void shouldSupplyDecryptedCredential_toActualDelegateBuilder() throws Exception {
        when(credentials.getPassword("FAKE-fallback")).thenReturn("FAKE-decrypted");var props=mock(CarlosProperties.class);when(props.getProperty("mcedt.service.pass")).thenReturn("FAKE-fallback");var observed=new AtomicReference<EdtClientBuilderConfig>();
        try(var properties=mockStatic(CarlosProperties.class);var builders=mockConstruction(EdtClientBuilder.class,(builder,context)->observed.set((EdtClientBuilderConfig)context.arguments().get(0)))) {
            properties.when(CarlosProperties::getInstance).thenReturn(props);DelegateFactory.getEDTDelegateInstance("FAKE-local-service");assertEquals("FAKE-decrypted",observed.get().getUserNameTokenPassword());verify(credentials).getPassword("FAKE-fallback");
        }
    }
    @Test
    void shouldStopDelegateCreation_whenCredentialCannotBeRead() throws Exception {
        when(credentials.getPassword(any())).thenThrow(new IllegalStateException("static failure"));
        try(var properties=mockStatic(CarlosProperties.class);var builders=mockConstruction(EdtClientBuilder.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));assertThrows(IllegalStateException.class,()->DelegateFactory.getEDTDelegateInstance("FAKE-local-service"));assertTrue(builders.constructed().isEmpty());
        }
    }
    @Test
    void shouldReuseClientsOnlyWhileCredentialStateIsUnchanged() {
        when(credentials.getPassword(any())).thenReturn(null,null,"","","FAKE-rotated");
        try(var properties=mockStatic(CarlosProperties.class);
                var builders=mockConstruction(EdtClientBuilder.class,(builder,context)->
                        when(builder.build(EDTDelegate.class)).thenReturn(mock(EDTDelegate.class)))) {
            properties.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
            var absent=DelegateFactory.getEDTDelegateInstance("FAKE-local-service");
            assertSame(absent,DelegateFactory.getEDTDelegateInstance("FAKE-local-service"));
            var blank=DelegateFactory.getEDTDelegateInstance("FAKE-local-service");
            assertNotSame(absent,blank);
            assertSame(blank,DelegateFactory.getEDTDelegateInstance("FAKE-local-service"));
            assertNotSame(blank,DelegateFactory.getEDTDelegateInstance("FAKE-local-service"));
            assertEquals(3,builders.constructed().size());
        }
    }
    @Test
    void shouldRebuildClientsOnExplicitRefresh_evenWhenPasswordIsUnchanged() {
        when(credentials.getPassword(any())).thenReturn("FAKE-current");
        try(var properties=mockStatic(CarlosProperties.class);var actions=mockStatic(ActionUtils.class);
                var builders=mockConstruction(EdtClientBuilder.class,(builder,context)->
                        when(builder.build(EDTDelegate.class)).thenReturn(mock(EDTDelegate.class)))) {
            var props=mock(CarlosProperties.class);when(props.getProperty("mcedt.service.id")).thenReturn("FAKE-local-service");
            properties.when(CarlosProperties::getInstance).thenReturn(props);
            actions.when(ActionUtils::getServiceIds).thenReturn(List.of("FAKE-local-service"));
            var first=DelegateFactory.getEDTDelegateInstance();DelegateFactory.refreshDelegates();
            assertNotSame(first,DelegateFactory.getEDTDelegateInstance());
            assertEquals(2,builders.constructed().size());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRejectAnOlderPasswordObservation_whenANewerDelegateWasPublished(boolean refresh) {
        String service = "FAKE-local-service";
        var reads = new AtomicInteger();
        var newer = new AtomicReference<EDTDelegate>();
        var builtPasswords = new ArrayList<String>();
        when(credentials.getPassword(any())).thenAnswer(call -> {
            if (reads.getAndIncrement() == 0) {
                newer.set(DelegateFactory.getEDTDelegateInstance(service));
                return "FAKE-older";
            }
            return "FAKE-newer";
        });
        try (var properties = mockStatic(CarlosProperties.class);
                var actions = mockStatic(ActionUtils.class);
                var builders = mockConstruction(EdtClientBuilder.class, (builder, construction) -> {
                    builtPasswords.add(((EdtClientBuilderConfig) construction.arguments().getFirst()).getUserNameTokenPassword());
                    when(builder.build(EDTDelegate.class)).thenReturn(mock(EDTDelegate.class));
                })) {
            properties.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
            actions.when(ActionUtils::getServiceIds).thenReturn(List.of(service));
            if (refresh) {
                DelegateFactory.refreshDelegates();
                assertNotSame(newer.get(), DelegateFactory.getEDTDelegateInstance(service));
                assertEquals(List.of("FAKE-newer", "FAKE-newer"), builtPasswords);
            } else {
                EDTDelegate observed = DelegateFactory.getEDTDelegateInstance(service);
                assertSame(newer.get(), observed);
                assertEquals(List.of("FAKE-newer"), builtPasswords);
            }
            assertEquals(refresh ? 4 : 3, reads.get());
        }
    }

}
