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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import ca.ontario.health.edt.EDTDelegate;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import io.github.carlos_emr.carlos.utility.SpringUtils;
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
    private SecurityInfoManager security;
    private Map<String, EDTDelegate> delegates;
    private Map<String, byte[]> fingerprints;
    private Map<String, EDTDelegate> originalDelegates;
    private Map<String, byte[]> originalFingerprints;
    @SuppressWarnings("unchecked")
    private static <T> T cache(String name) throws Exception {
        Field field=DelegateFactory.class.getDeclaredField(name);field.setAccessible(true);return (T)field.get(null);
    }
    @Test void savePasswordRaisesOnlyAnIllegalStateExceptionWithoutACause() throws Exception {
        // The service's contract that User2Action.isConfirmedNotSaved relies on: whatever fails inside
        // the transactional body, the caller sees exactly IllegalStateException and no cause.
        McedtCredentialService real=new McedtCredentialService();
        jakarta.persistence.EntityManager failing=mock(jakarta.persistence.EntityManager.class);
        when(failing.createNativeQuery(anyString(),any(Class.class))).thenThrow(new RuntimeException("FAKE database failure"));
        when(failing.createNativeQuery(anyString())).thenThrow(new RuntimeException("FAKE database failure"));
        Field entityManager=McedtCredentialService.class.getDeclaredField("entityManager");
        entityManager.setAccessible(true);entityManager.set(real,failing);
        for (String password : new String[]{"FAKE-password", "x".repeat(4001)}) {
            IllegalStateException refused=assertThrows(IllegalStateException.class,()->real.savePassword(password,"611"));
            assertEquals(IllegalStateException.class,refused.getClass());
            assertNull(refused.getCause());
            assertFalse(refused.getMessage().contains(password));
        }
    }
    @Test void currentRowStatementsFollowTheDatabaseProduct() throws Exception {
        // The MariaDB form of each current-row statement is chosen when the product name says MariaDB, the standard one
        // otherwise. The H2 lane never takes the MariaDB branch (the physical MariaDB lane does), so both are driven here.
        for (boolean mariaDb : new boolean[]{true, false}) {
            McedtCredentialService real=new McedtCredentialService();
            jakarta.persistence.EntityManager manager=mock(jakarta.persistence.EntityManager.class);
            org.hibernate.Session session=mock(org.hibernate.Session.class);
            when(manager.unwrap(org.hibernate.Session.class)).thenReturn(session);
            when(session.doReturningWork(any(org.hibernate.jdbc.ReturningWork.class))).thenReturn(mariaDb);
            AtomicReference<String> sql=new AtomicReference<>();
            when(manager.createNativeQuery(anyString(),any(Class.class))).thenAnswer(call -> {
                sql.set(call.getArgument(0));
                throw new RuntimeException("FAKE stop after the statement is chosen");
            });
            Field entityManager=McedtCredentialService.class.getDeclaredField("entityManager");
            entityManager.setAccessible(true);entityManager.set(real,manager);
            assertThrows(IllegalStateException.class,()->real.getPassword("fallback"));
            assertTrue(sql.get().contains("encrypted_password FROM mcedt_credential WHERE id = 1 FOR UPDATE"));
            assertEquals(mariaDb,sql.get().contains("SET STATEMENT innodb_snapshot_isolation=OFF FOR"));
        }
    }
    @BeforeEach void prepare() throws Exception {
        credentials=mock(McedtCredentialService.class);
        registerMock(McedtCredentialService.class,credentials);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq(McedtSecurity.PRIVILEGE), eq("w"), isNull()))
                .thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
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
        var request = new MockHttpServletRequest("POST", "/mcedt");
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
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());verify(credentials).savePassword("FAKE-input", AUDIT_ACTOR);assertEquals(Boolean.TRUE,request.getAttribute("isPassChange"));
        }
    }
    @Test
    void shouldExposeFailureState_withoutEchoingCredential() throws Exception {
        var request=authenticatedRequest();
        doThrow(new IllegalStateException("FAKE-sensitive-cause")).when(credentials).savePassword(anyString(), eq(AUDIT_ACTOR));
        try(MockedStatic<ServletActionContext> servlet=mockStatic(ServletActionContext.class);
                var log=LogCapture.forLogger(User2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action=new User2Action();action.setPassword("FAKE-input");assertEquals("success",action.changePassword());assertEquals(Boolean.FALSE,request.getAttribute("isPassChange"));
            assertEquals(List.of("MCEDT password change failed; credential storage was not updated"), log.messages());
            assertNull(log.events().getFirst().getThrown());
        }
    }

    @Test
    void shouldSayTheOutcomeIsUnknown_whenTheCommitAcknowledgementIsLost() throws Exception {
        // The credential service is transactional. An exception that does not come from its own body (here a
        // transaction exception raised at commit) can mean the database already holds the new password, so
        // the action must not report "not updated" nor offer the confirmed-failure state.
        var request = authenticatedRequest();
        doThrow(new org.springframework.transaction.TransactionSystemException("FAKE commit acknowledgement lost"))
                .when(credentials).savePassword(anyString(), eq(AUDIT_ACTOR));
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                var log = LogCapture.forLogger(User2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = new User2Action();
            action.setPassword("FAKE-input");
            assertEquals("success", action.changePassword());
            assertNull(request.getAttribute("isPassChange"));
            assertEquals(Boolean.TRUE, request.getAttribute("isPassChangeUnconfirmed"));
            assertEquals(List.of("MCEDT password change could not be confirmed; credential storage may have been updated"),
                    log.messages());
            assertNull(log.events().getFirst().getThrown());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"service", "rolledBack"})
    void shouldKeepTheConfirmedFailureState_forARefusalOrAConfirmedRollback(String kind) throws Exception {
        var request = authenticatedRequest();
        RuntimeException refusal = kind.equals("service")
                ? new IllegalStateException("FAKE service refusal")
                : new org.springframework.transaction.UnexpectedRollbackException("FAKE rolled back");
        doThrow(refusal).when(credentials).savePassword(anyString(), eq(AUDIT_ACTOR));
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            var action = new User2Action();
            action.setPassword("FAKE-input");
            assertEquals("success", action.changePassword());
            assertEquals(Boolean.FALSE, request.getAttribute("isPassChange"));
            assertNull(request.getAttribute("isPassChangeUnconfirmed"));
        }
    }

    @Test
    void shouldGiveAnUnconfirmedSaveItsOwnMessage_andNeverTellTheAdministratorToResubmit() throws Exception {
        var page = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/webapp/WEB-INF/jsp/mcedt/mailbox/changepass.jsp"));
        var unconfirmed = page.substring(page.indexOf("if (isUnconfirmed)"), page.indexOf("} else if (isSucess != null"));
        assertTrue(unconfirmed.contains("Unable to confirm the EDT password change"));
        assertTrue(unconfirmed.contains("Do not assume the previous password is still in effect"));
        assertFalse(unconfirmed.contains("try again"));
        assertFalse(unconfirmed.contains("Unable to change the EDT password"));
        // The confirmed failure keeps its own wording and the success path is unchanged.
        assertTrue(page.contains("Unable to change the EDT password. Please try again or contact your administrator."));
        assertTrue(page.contains("EDT Password has been changed successfully."));
        assertTrue(page.contains("request.getAttribute(\"isPassChangeUnconfirmed\")"));
    }

    @Test
    void shouldRetainTheSuccessOutcome_whenLogoutInvalidatesFeedbackAfterCommit() throws Exception {
        var request = authenticatedRequest();
        var session = request.getSession(false);
        doAnswer(call -> {
            session.invalidate();
            return null;
        }).when(credentials).savePassword("FAKE-input", AUDIT_ACTOR);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            var action = new User2Action();
            action.setPassword("FAKE-input");
            assertEquals("success", action.changePassword());
            verify(credentials).savePassword("FAKE-input", AUDIT_ACTOR);
            assertEquals(Boolean.TRUE, request.getAttribute("isPassChange"));
        }
    }

    @Test
    void shouldKeepDistinctStorageOutcomes_whenRequestsShareOneSession() throws Exception {
        var failed = authenticatedRequest();
        var saved = new MockHttpServletRequest("POST", "/mcedt");
        saved.setSession(failed.getSession());
        doThrow(new IllegalStateException("synthetic storage refusal"))
                .when(credentials).savePassword("FAKE-refused", AUDIT_ACTOR);

        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(failed);
            var first = new User2Action();
            first.setPassword("FAKE-refused");
            assertEquals("success", first.changePassword());

            servlet.when(ServletActionContext::getRequest).thenReturn(saved);
            var second = new User2Action();
            second.setPassword("FAKE-accepted");
            assertEquals("success", second.changePassword());

            assertEquals(Boolean.FALSE, failed.getAttribute("isPassChange"));
            assertEquals(Boolean.TRUE, saved.getAttribute("isPassChange"));
            assertNull(saved.getSession().getAttribute("isPassChange"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shouldRefuseCredentialChanges_whenTheSessionHasNoAuditActor(boolean hasLogin) {
        var request = new MockHttpServletRequest("POST", "/mcedt");
        if (hasLogin) LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            assertThrows(SecurityException.class, new User2Action()::changePassword);
            verifyNoInteractions(credentials);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRefuseDirectCredentialChanges_withoutWritePrivilegeOrPost(boolean post) {
        var request = authenticatedRequest();
        if (post) {
            when(security.hasPrivilege(any(LoggedInInfo.class), eq(McedtSecurity.PRIVILEGE), eq("w"), isNull()))
                    .thenReturn(false);
        } else {
            request.setMethod("GET");
        }
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            assertThrows(SecurityException.class, new User2Action()::changePassword);
            verifyNoInteractions(credentials);
        }
    }

    @Test
    void shouldRefuseCredentialChange_whenLogoutRemovesTheActorAfterAuthorization() {
        var request = authenticatedRequest();
        when(security.hasPrivilege(any(LoggedInInfo.class), eq(McedtSecurity.PRIVILEGE), eq("w"), isNull()))
                .thenAnswer(invocation -> {
                    request.getSession(false).invalidate();
                    return true;
                });
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


}
