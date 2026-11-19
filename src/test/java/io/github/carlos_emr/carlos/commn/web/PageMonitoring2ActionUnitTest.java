/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.PageMonitorDao;
import io.github.carlos_emr.carlos.commn.model.PageMonitor;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.struts2.ActionContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the privilege gate on {@code /PageMonitoringService}, the slot-lock endpoint that
 * {@code addappointment.jsp} calls for every booker (issue #4917). The gate must follow the
 * Add Appointment page's own {@code _appointment w} requirement rather than {@code _admin},
 * which only the administrator role holds.
 */
@Tag("unit")
class PageMonitoring2ActionUnitTest extends CarlosWebTestBase {

    private static final String PAGE_ID = "999998|2026-10-19|10:00|10:15";
    private static final String LOGGED_IN_INFO_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";

    private PageMonitorDao dao;

    @BeforeEach
    void prepare() {
        dao = mock(PageMonitorDao.class);
        replaceSpringUtilsBean(PageMonitorDao.class, dao);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        setSessionAttribute(LOGGED_IN_INFO_KEY, mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Test");
        provider.setLastName("Booker");
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(provider);
        mockRequest.setMethod("POST");
        addRequestParameter("page", "addappointment");
        addRequestParameter("pageId", PAGE_ID);
    }

    @Test
    void shouldAcquireLock_whenBookerHoldsAppointmentWriteButNotAdmin() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), anyString(), isNull())).thenReturn(false);
        when(dao.findByPageName("addappointment")).thenReturn(List.of());
        addRequestParameter("method", "update");
        addRequestParameter("lock", "true");

        // The JSON is written straight to the response, so no Struts result may follow it.
        assertThat(executeAction(new PageMonitoring2Action())).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(200);
        ArgumentCaptor<PageMonitor> persisted = ArgumentCaptor.forClass(PageMonitor.class);
        verify(dao).persist(persisted.capture());
        assertThat(persisted.getValue().isLocked()).isTrue();
        assertThat(persisted.getValue().getProviderNo()).isEqualTo("999998");
    }

    @Test
    void shouldReportOtherHolder_whenBookerOpensSlotAlreadyLocked() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), anyString(), isNull())).thenReturn(false);
        PageMonitor holder = new PageMonitor();
        holder.setPageName("addappointment");
        holder.setPageId("999998|2026-10-19|10:05|10:20");
        holder.setSession("another-session");
        holder.setRemoteAddr("10.0.0.9");
        holder.setProviderNo("999997");
        holder.setProviderName("Holder, Pat");
        holder.setLocked(true);
        when(dao.findByPageName("addappointment")).thenReturn(List.of(holder));
        addRequestParameter("method", "update");
        addRequestParameter("lock", "true");

        executeAction(new PageMonitoring2Action());

        // The overlapping holder is reported so the page can show "Viewers: N (locked)" and hide Add.
        String body = mockResponse.getContentAsString();
        assertThat(body).contains("\"locked\":true").contains("\"providerName\":\"Holder, Pat\"").contains("\"self\":false");
        // The response goes to every booker: another user's session id (a bearer credential) and
        // address must never be echoed back, only what the page renders.
        assertThat(body).doesNotContain("another-session").doesNotContain("\"session\"")
                .doesNotContain("10.0.0.9").doesNotContain("remoteAddr").doesNotContain("999997");
        ArgumentCaptor<PageMonitor> persisted = ArgumentCaptor.forClass(PageMonitor.class);
        verify(dao).persist(persisted.capture());
        assertThat(persisted.getValue().isLocked()).isFalse();
    }

    @Test
    void shouldGrantOnlyOneLock_whenTwoActionInstancesBookTheSameSlotConcurrently() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        addRequestParameter("method", "update");
        addRequestParameter("lock", "true");
        PageMonitoring2Action firstAction = new PageMonitoring2Action();

        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/PageMonitoringService");
        secondRequest.setSession(new MockHttpSession());
        secondRequest.getSession().setAttribute(LOGGED_IN_INFO_KEY, mockLoggedInInfo);
        secondRequest.setParameter("page", "addappointment");
        secondRequest.setParameter("pageId", PAGE_ID);
        secondRequest.setParameter("method", "update");
        secondRequest.setParameter("lock", "true");
        ActionContext.of().withServletRequest(secondRequest)
                .withServletResponse(new MockHttpServletResponse()).bind();
        PageMonitoring2Action secondAction = new PageMonitoring2Action();

        List<PageMonitor> persisted = new CopyOnWriteArrayList<>();
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch releaseFirstRead = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        when(dao.findByPageName("addappointment")).thenAnswer(invocation -> {
            List<PageMonitor> snapshot = List.copyOf(persisted);
            if (first.compareAndSet(true, false)) {
                firstRead.countDown();
                if (!releaseFirstRead.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("The first slot lookup was never released");
                }
            }
            return snapshot;
        });
        doAnswer(invocation -> {
            persisted.add(invocation.getArgument(0));
            return null;
        }).when(dao).persist(any(PageMonitor.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(firstAction::execute);
            try {
                assertThat(firstRead.await(5, TimeUnit.SECONDS)).isTrue();
                var secondResult = executor.submit(secondAction::execute);
                try {
                    // An unprotected second request completes while the first holds an empty snapshot.
                    // A protected request waits, then observes the first request's persisted lock.
                    secondResult.get(300, TimeUnit.MILLISECONDS);
                } catch (TimeoutException expected) {
                    // The shared critical section is still held by the first request.
                } finally {
                    releaseFirstRead.countDown();
                }
                firstResult.get(5, TimeUnit.SECONDS);
                secondResult.get(5, TimeUnit.SECONDS);
            } finally {
                releaseFirstRead.countDown();
            }
        }

        assertThat(persisted).hasSize(2);
        assertThat(persisted.stream().filter(PageMonitor::isLocked)).hasSize(1);
    }

    @Test
    void shouldReleaseLock_whenBookerHoldsAppointmentWriteButNotAdmin() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), anyString(), isNull())).thenReturn(false);

        assertThat(executeAction(new PageMonitoring2Action())).isEqualTo(ActionSupport.NONE);

        verify(dao).cancelPageIdForProvider("addappointment", PAGE_ID, "999998");
    }

    @Test
    void shouldDenyLockRefresh_whenAppointmentWriteMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(false);
        // Holding _admin alone must not substitute for the booking right the page itself needs.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), anyString(), isNull())).thenReturn(true);
        addRequestParameter("method", "update");
        addRequestParameter("lock", "true");

        assertThatThrownBy(() -> executeAction(new PageMonitoring2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
        verifyNoInteractions(dao);
    }

    @Test
    void shouldNotPersistLock_whenAppointmentWriteMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> executeAction(new PageMonitoring2Action()))
                .isInstanceOf(SecurityException.class);
        verify(dao, never()).cancelPageIdForProvider(anyString(), anyString(), anyString());
        verify(dao, never()).persist(any(PageMonitor.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "PATCH", "DELETE"})
    void shouldRejectRequest_whenMethodIsNotPost(String httpMethod) throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        mockRequest.setMethod(httpMethod);

        String result = executeAction(new PageMonitoring2Action());

        // No method parameter would otherwise fall through to cancel() and delete the caller's monitor rows.
        assertThat(result).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(dao);
    }

    @Test
    void shouldRejectUpdate_whenMethodIsGet() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        mockRequest.setMethod("GET");
        addRequestParameter("method", "update");
        addRequestParameter("lock", "true");

        executeAction(new PageMonitoring2Action());

        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verifyNoInteractions(dao);
    }
}
