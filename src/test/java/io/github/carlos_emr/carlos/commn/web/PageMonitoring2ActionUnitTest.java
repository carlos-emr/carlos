/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.PageMonitorDao;
import io.github.carlos_emr.carlos.commn.model.PageMonitor;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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

        executeAction(new PageMonitoring2Action());

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
    void shouldReleaseLock_whenBookerHoldsAppointmentWriteButNotAdmin() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), anyString(), isNull())).thenReturn(false);

        executeAction(new PageMonitoring2Action());

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
}
