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
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the patient tickler list's bulk actions, including Erase Completely (#4993).
 *
 * @since 2026-10-18
 */
@DisplayName("DbTicklerDemoMain2Action bulk actions")
@Tag("integration")
@Tag("tickler")
class DbTicklerDemoMain2ActionTest extends CarlosWebTestBase {

    @Mock
    private TicklerManager ticklerManager;

    @BeforeEach
    void setUpAction() {
        replaceSpringUtilsBean(TicklerManager.class, ticklerManager);
        mockRequest.setContextPath("/carlos");
        mockRequest.setMethod("POST");
    }

    @Test
    @DisplayName("should erase rather than re-delete ticked tasks on Erase Completely")
    void shouldEraseTicklers_whenEraseCompletelySubmitted() throws Exception {
        Tickler tickler = mock(Tickler.class);
        when(tickler.getId()).thenReturn(123);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), anyInt())).thenReturn(tickler);
        when(ticklerManager.eraseTickler(any(LoggedInInfo.class), eq(123))).thenReturn(true);
        mockRequest.addParameter("checkbox", "123");
        mockRequest.addParameter("submit_form", "Erase Completely");

        String result = new DbTicklerDemoMain2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(ticklerManager).eraseTickler(any(LoggedInInfo.class), eq(123));
        verify(ticklerManager, never()).updateStatus(any(), any(), any(), any());
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/carlos/tickler/ViewTicklerMain?demoview=all&parentAjaxId=&updateParent=false");
    }

    @Test
    @DisplayName("should report a failure when an erase is refused")
    void shouldReportFailure_whenEraseRefused() throws Exception {
        Tickler tickler = mock(Tickler.class);
        when(tickler.getId()).thenReturn(124);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), anyInt())).thenReturn(tickler);
        when(ticklerManager.eraseTickler(any(LoggedInInfo.class), eq(124))).thenReturn(false);
        mockRequest.addParameter("checkbox", "124");
        mockRequest.addParameter("submit_form", "Erase Completely");

        new DbTicklerDemoMain2Action().execute();

        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/carlos/tickler/ViewTicklerMain?demoview=all&parentAjaxId=&updateParent=false&failCount=1");
    }

    @Test
    @DisplayName("should still mark tasks deleted on Delete")
    void shouldMarkDeleted_whenDeleteSubmitted() throws Exception {
        Tickler tickler = mock(Tickler.class);
        when(tickler.getId()).thenReturn(125);
        when(ticklerManager.getTickler(any(LoggedInInfo.class), anyInt())).thenReturn(tickler);
        mockRequest.addParameter("checkbox", "125");
        mockRequest.addParameter("submit_form", "Delete");

        new DbTicklerDemoMain2Action().execute();

        verify(ticklerManager).updateStatus(any(LoggedInInfo.class), eq(125), any(), eq(Tickler.STATUS.D));
        verify(ticklerManager, never()).eraseTickler(any(), any());
    }
}
