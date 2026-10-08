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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the acting user recorded on a new tickler.
 *
 * <p>The tickler form used to post {@code user_no} and the action stored it as
 * the tickler creator, so a caller could name someone else as the author of the
 * tickler they were adding. The creator must come from the logged-in provider,
 * while the selected assignee still comes from {@code task_assigned_to}.</p>
 */
@DisplayName("DbTicklerAdd2Action creator")
@Tag("integration")
@Tag("tickler")
class DbTicklerAdd2ActionTest extends CarlosWebTestBase {

    @Mock
    private TicklerManager ticklerManager;

    @BeforeEach
    void setUpAction() {
        replaceSpringUtilsBean(TicklerManager.class, ticklerManager);
        mockRequest.setContextPath("/carlos");
        mockRequest.setMethod("POST");
    }

    @Test
    @DisplayName("should use the session provider as creator and keep the selected assignee")
    void shouldUseSessionProvider_asCreator() throws Exception {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        // TicklerManagerImpl persists through the DAO, which assigns the id.
        when(ticklerManager.addTickler(any(LoggedInInfo.class), any(Tickler.class))).thenAnswer(invocation -> {
            invocation.<Tickler>getArgument(1).setId(123);
            return true;
        });
        mockRequest.addParameter("demographic_no", "42");
        mockRequest.addParameter("user_no", "spoofed");
        mockRequest.addParameter("task_assigned_to", "999997");
        mockRequest.addParameter("ticklerMessage", "call back about the lab result");

        String result = new DbTicklerAdd2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<Tickler> saved = ArgumentCaptor.forClass(Tickler.class);
        verify(ticklerManager).addTickler(eq(mockLoggedInInfo), saved.capture());
        assertThat(saved.getValue().getCreator()).isEqualTo("999998");
        assertThat(saved.getValue().getTaskAssignedTo()).isEqualTo("999997");
    }
}
