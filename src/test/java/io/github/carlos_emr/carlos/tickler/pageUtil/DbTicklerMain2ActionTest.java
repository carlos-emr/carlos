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
import static org.mockito.Mockito.mock;
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
}
