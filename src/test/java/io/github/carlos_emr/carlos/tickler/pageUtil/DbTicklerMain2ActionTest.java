/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for redirects issued after Tickler Manager bulk actions.
 *
 * @since 2026-08-05
 */
@DisplayName("DbTicklerMain2Action redirects")
@Tag("integration")
@Tag("tickler")
class DbTicklerMain2ActionTest extends CarlosWebTestBase {

    @Mock
    private TicklerManager ticklerManager;

    @BeforeEach
    void setUpAction() {
        replaceSpringUtilsBean(TicklerManager.class, ticklerManager);
        mockRequest.setContextPath("/carlos");
        mockRequest.setMethod("POST");
    }

    @Test
    @DisplayName("should preserve schedule navigation after completing selected ticklers")
    void shouldPreserveScheduleNavigation_afterCompletingSelectedTicklers() throws Exception {
        Tickler tickler = mock(Tickler.class);
        when(tickler.getId()).thenReturn(123);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), anyInt())).thenReturn(tickler);
        mockRequest.addParameter("checkbox", "123");
        mockRequest.addParameter("submit_form", "Complete");
        mockRequest.addParameter("scheduleNav", "1");

        String result = new DbTicklerMain2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl())
                .isEqualTo("/carlos/tickler/ViewTicklerMain?scheduleNav=1");
    }

    @Test
    @DisplayName("should retain schedule navigation with a bulk-update failure count")
    void shouldRetainScheduleNavigation_withBulkUpdateFailureCount() throws Exception {
        mockRequest.addParameter("checkbox", "not-a-tickler-id");
        mockRequest.addParameter("submit_form", "Complete");
        mockRequest.addParameter("scheduleNav", "1");

        String result = new DbTicklerMain2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl())
                .isEqualTo("/carlos/tickler/ViewTicklerMain?scheduleNav=1&failCount=1");
    }

    @Test
    @DisplayName("should preserve schedule navigation and sorting when no ticklers are selected")
    void shouldPreserveScheduleNavigationAndSorting_whenNoTicklersAreSelected() throws Exception {
        mockRequest.addParameter("sort_column", "service_date");
        mockRequest.addParameter("sort_order", "ASC");
        mockRequest.addParameter("scheduleNav", "1");

        String result = new DbTicklerMain2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo(
                "/carlos/tickler/ViewTicklerMain?sort_column=service_date&sort_order=ASC&scheduleNav=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Delete", "delete", "Erase Completely", "erase completely", "E", "e"})
    @DisplayName("should only mark tasks deleted for Delete and legacy erase submissions")
    void shouldMarkDeleted_whenDeleteOrLegacyEraseSubmitted(String submitForm) throws Exception {
        Tickler tickler = mock(Tickler.class);
        when(tickler.getId()).thenReturn(123);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), anyInt())).thenReturn(tickler);
        mockRequest.addParameter("checkbox", "123");
        mockRequest.addParameter("submit_form", submitForm);

        String result = new DbTicklerMain2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(ticklerManager).getTickler(any(LoggedInInfo.class), eq(123));
        verify(ticklerManager).updateStatusIfCurrent(any(LoggedInInfo.class), eq(123), any(), isNull(), eq(Tickler.STATUS.D));
        verifyNoMoreInteractions(ticklerManager);
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/carlos/tickler/ViewTicklerMain");
    }

    @Test
    @DisplayName("should leave a task another user changed alone, apply the others and remember which was refused")
    void shouldApplyOthersAndRememberRefused_whenOneTickedTaskIsStale() throws Exception {
        Tickler deletedElsewhere = mock(Tickler.class);
        when(deletedElsewhere.getId()).thenReturn(123);
        Tickler untouched = mock(Tickler.class);
        when(untouched.getId()).thenReturn(124);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), eq(123))).thenReturn(deletedElsewhere);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), eq(124))).thenReturn(untouched);
        // The list showed both Active; 123 has been deleted by someone else since.
        when(ticklerManager.updateStatusIfCurrent(any(LoggedInInfo.class), eq(123), any(), eq(Tickler.STATUS.A), eq(Tickler.STATUS.C)))
                .thenReturn(TicklerManager.StatusChange.STALE);
        when(ticklerManager.updateStatusIfCurrent(any(LoggedInInfo.class), eq(124), any(), eq(Tickler.STATUS.A), eq(Tickler.STATUS.C)))
                .thenReturn(TicklerManager.StatusChange.APPLIED);
        mockRequest.addParameter("checkbox", "123", "124");
        mockRequest.addParameter("submit_form", "Complete");
        mockRequest.addParameter(TicklerRenderedStatus.PARAMETER, "123:A,124:A");

        String result = new DbTicklerMain2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(ticklerManager).updateStatusIfCurrent(any(LoggedInInfo.class), eq(123), any(), eq(Tickler.STATUS.A), eq(Tickler.STATUS.C));
        verify(ticklerManager).updateStatusIfCurrent(any(LoggedInInfo.class), eq(124), any(), eq(Tickler.STATUS.A), eq(Tickler.STATUS.C));
        // A refused task is not a failed update: the redirect carries no failCount.
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/carlos/tickler/ViewTicklerMain");
        assertThat(TicklerRenderedStatus.takeRefused(mockRequest.getSession())).containsExactly(123);
        // The notice is shown once.
        assertThat(TicklerRenderedStatus.takeRefused(mockRequest.getSession())).isEmpty();
    }
}
