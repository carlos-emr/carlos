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
import ca.ontario.health.edt.EDTDelegate;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated("Static MCEDT delegate cache")
@Execution(ExecutionMode.SAME_THREAD)
class McedtCredentialConsumerUnitTest extends CarlosUnitTestBase {
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

    @Test
    void shouldConfirmPasswordChange_afterCredentialCommit() throws Exception {
        var request=new MockHttpServletRequest();
        try(MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());verify(credentials).savePassword(eq("FAKE-input"), isNull());assertEquals("true",request.getSession().getAttribute("isPassChange"));
        }
    }
    @Test
    void shouldExposeFailureState_withoutEchoingCredential() throws Exception {
        var request=new MockHttpServletRequest();doThrow(new IllegalStateException("FAKE-sensitive-cause")).when(credentials).savePassword(anyString(), any());
        try (var servlet = mockStatic(ServletActionContext.class); var logs = LogCapture.forLogger(User2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = new User2Action(); action.setPassword("FAKE-input");
            assertEquals("success", action.changePassword());
            assertEquals("false", request.getSession().getAttribute("isPassChange"));
            assertEquals(List.of("MCEDT password change failed; credential storage was not updated"), logs.messages());
            assertTrue(logs.messages().stream().noneMatch(message -> message.contains("FAKE-input") || message.contains("FAKE-sensitive-cause")));
            assertTrue(logs.events().stream().allMatch(event -> event.getThrown() == null));
        }
    }
    @Test
    void shouldForwardTheAuthenticatedActor_withTheReplacementCredential() throws Exception {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", "FAKE-provider");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = new User2Action(); action.setPassword("FAKE-input");
            assertEquals("success", action.changePassword());
            verify(credentials).savePassword("FAKE-input", "FAKE-provider");
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetryAnOlderCredentialObservation_afterANewerClientWasPublished(boolean refresh) throws Exception {
        var oldRead = new CountDownLatch(1);
        var resumeOld = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var builtPasswords = new ConcurrentLinkedQueue<String>();
        var props = mock(CarlosProperties.class);
        var serviceId = "FAKE-publication-service";
        when(credentials.getPassword(any())).thenAnswer(call -> {
            if (reads.getAndIncrement() == 0) {
                oldRead.countDown();
                assertTrue(resumeOld.await(10, TimeUnit.SECONDS));
                return "FAKE-obsolete";
            }
            return "FAKE-current";
        });
        try (var executor = Executors.newSingleThreadExecutor();
                var properties = mockStatic(CarlosProperties.class);
                var builders = mockConstruction(EdtClientBuilder.class, (builder, context) -> {
                    builtPasswords.add(((EdtClientBuilderConfig) context.arguments().get(0)).getUserNameTokenPassword());
                    when(builder.build(EDTDelegate.class)).thenReturn(mock(EDTDelegate.class));
                })) {
            properties.when(CarlosProperties::getInstance).thenReturn(props);
            var delayed = executor.submit(() -> {
                try (var spring = mockStatic(SpringUtils.class);
                        var workerProperties = mockStatic(CarlosProperties.class);
                        var actions = mockStatic(ActionUtils.class);
                        var workerBuilders = mockConstruction(EdtClientBuilder.class, (builder, context) -> {
                            builtPasswords.add(((EdtClientBuilderConfig) context.arguments().get(0)).getUserNameTokenPassword());
                            when(builder.build(EDTDelegate.class)).thenReturn(mock(EDTDelegate.class));
                        })) {
                    spring.when(() -> SpringUtils.getBean(McedtCredentialService.class)).thenReturn(credentials);
                    workerProperties.when(CarlosProperties::getInstance).thenReturn(props);
                    actions.when(ActionUtils::getServiceIds).thenReturn(List.of(serviceId));
                    if (refresh) {
                        DelegateFactory.refreshDelegates();
                        return null;
                    }
                    return DelegateFactory.getEDTDelegateInstance(serviceId);
                }
            });
            try {
                assertTrue(oldRead.await(5, TimeUnit.SECONDS));
                EDTDelegate newer = DelegateFactory.getEDTDelegateInstance(serviceId);
                assertNotNull(newer);
                resumeOld.countDown();
                EDTDelegate result = delayed.get(10, TimeUnit.SECONDS);
                assertFalse(builtPasswords.contains("FAKE-obsolete"), "An older observation replaced the current delegate");
                if (!refresh) {
                    assertSame(newer, result);
                }
                assertTrue(reads.get() >= 3, "The stale observation must trigger another credential read");
            } finally {
                resumeOld.countDown();
            }
        }
    }

    @Test
    void shouldNotReportStorageFailure_whenSessionFeedbackFailsAfterCommit() throws Exception {
        var request = mock(HttpServletRequest.class);
        var session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        doThrow(new IllegalStateException("FAKE-invalidated-session")).when(session).setAttribute("isPassChange", "true");
        try (var servlet = mockStatic(ServletActionContext.class); var logs = LogCapture.forLogger(User2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = new User2Action();
            action.setPassword("FAKE-input");
            assertThrows(IllegalStateException.class, action::changePassword);
            verify(credentials).savePassword(eq("FAKE-input"), isNull());
            verify(session, never()).setAttribute("isPassChange", "false");
            assertTrue(logs.messages().stream().noneMatch(message -> message.contains("storage was not updated")));
        }
    }

}
