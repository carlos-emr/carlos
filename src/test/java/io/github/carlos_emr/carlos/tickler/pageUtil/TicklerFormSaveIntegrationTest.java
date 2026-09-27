/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerTextSuggestDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.util.Date;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regression coverage for rejected tickler saves and validation before entity mutation. */
@Tag("integration")
@Tag("tickler")
class TicklerFormSaveIntegrationTest extends CarlosWebTestBase {
    @Mock
    private TicklerManager manager;
    @Mock
    private TicklerLinkDao linkDao;
    @Mock
    private TicklerTextSuggestDao suggestDao;
    private Tickler tickler;

    @BeforeEach
    void setUpForm() {
        replaceSpringUtilsBean(TicklerManager.class, manager);
        replaceSpringUtilsBean(io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService.class,
                mock(io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService.class));
        replaceSpringUtilsBean(TicklerLinkDao.class, linkDao);
        replaceSpringUtilsBean(TicklerTextSuggestDao.class, suggestDao);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("demographic_no", "123");
        mockRequest.setParameter("user_no", "999998");
        mockRequest.setParameter("task_assigned_to", "999998");
        mockRequest.setParameter("xml_appointment_date", "2026-03-04");
        mockRequest.setParameter("ticklerNo", "456");
        mockRequest.setParameter("status", "A");
        mockRequest.setParameter("priority", "Normal");
        mockRequest.setParameter("assignedToProviders", "999998");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        tickler = new Tickler();
        tickler.setId(456);
        tickler.setDemographicNo(123);
        tickler.setCreator("999998");
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(TicklerFormDate.parse("2026-03-04"));
        tickler.setUpdateDate(new Date(1000));
        when(manager.getTickler(mockLoggedInInfo, 456)).thenReturn(tickler);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-02-29", "2026-13-01", "2026-03-04junk", "2026-3-4", "0000-01-01"})
    void shouldRejectInvalidDate_withoutAddingTickler(String date) throws Exception {
        setDate(date);
        assertThat(new DbTicklerAdd2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(manager, never()).addTickler(any(), any());
        verifyNoInteractions(linkDao);
        assertThat(mockRequest.getAttribute("rowsAffected")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-02-29", "2026-13-01", "2026-03-04junk", "2026-3-4", "0000-01-01"})
    void shouldRejectInvalidDate_withoutChangingExistingTickler(String date) {
        setDate(date);
        mockRequest.setParameter("newMessage", "must not persist");
        mockRequest.setParameter("status", "C");
        assertThat(editAction().editTickler()).isEqualTo("failure");
        assertUnchanged();
    }

    @ParameterizedTest
    @CsvSource(value = {"status|", "status|Unknown", "priority|", "priority|urgent", "assignedToProviders|"}, delimiter = '|', nullValues = "NULL")
    void shouldRejectInvalidEditFields_withoutChangingHistory(String field, String value) {
        mockRequest.setParameter(field, value == null ? "" : value);
        mockRequest.setParameter("newMessage", "must not persist");
        assertThat(editAction().editTickler()).isEqualTo("failure");
        assertUnchanged();
    }

    @ParameterizedTest
    @CsvSource(value = {"demographic_no|0", "demographic_no|-1", "task_assigned_to|"}, delimiter = '|')
    void shouldRejectInvalidAddFields_withoutCallingManager(String field, String value) throws Exception {
        mockRequest.setParameter(field, value == null ? "" : value);
        assertThat(new DbTicklerAdd2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(manager, never()).addTickler(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "post", "Post", "PO\u017fT"})
    void shouldRejectNonPostEdit_withoutReadingOrChangingTicklers(String method) {
        mockRequest.setMethod(method);
        assertThat(editAction().editTickler()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(manager);
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "post", "Post", "PO\u017fT"})
    void shouldRejectNonPostSuggestedText_withoutChangingSuggestions(String method) {
        mockRequest.setMethod(method);
        EditTickler2Action action = editAction();
        action.setActiveText(new String[] { "new suggestion" });
        action.setInactiveText(new String[0]);
        assertThat(action.updateTextSuggest()).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(suggestDao);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldReportRejectedAdd_withoutUnboxingMissingIdOrLinking() throws Exception {
        mockRequest.setParameter("docType", "document");
        mockRequest.setParameter("docId", "12");
        when(manager.addTickler(any(), any())).thenReturn(false);
        assertThat(new DbTicklerAdd2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verifyNoInteractions(linkDao);
        assertThat(mockRequest.getAttribute("rowsAffected")).isNull();
    }

    @Test
    void shouldReportMissingSavedId_asServerFailure() throws Exception {
        when(manager.addTickler(any(), any())).thenReturn(true);
        assertThat(new DbTicklerAdd2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        assertThat(mockRequest.getAttribute("rowsAffected")).isNull();
    }

    @Test
    void shouldRejectInvalidStoredTickler_beforeMutatingCommentsOrHistory() {
        tickler.setCreator(null);
        mockRequest.setParameter("newMessage", "must not persist");
        assertThat(editAction().editTickler()).isEqualTo("failure");
        assertUnchanged();
    }

    @Test
    void shouldReportRejectedUpdate_withoutSuccessView() {
        mockRequest.setParameter("xml_appointment_date", "2026-03-05");
        when(manager.updateTickler(any(), any())).thenReturn(false);
        EditTickler2Action action = editAction();
        assertThat(action.editTickler()).isEqualTo("error");
        assertThat(action.getActionErrors()).isNotEmpty();
    }

    @Test
    void shouldRecordChangedStatusPriorityAndAssignee_whenUpdateSucceeds() {
        mockRequest.setParameter("status", "C");
        mockRequest.setParameter("priority", "High");
        mockRequest.setParameter("assignedToProviders", "999999");
        when(manager.updateTickler(any(), any())).thenReturn(true);
        assertThat(editAction().editTickler()).isEqualTo("close");
        assertThat(tickler.getStatus()).isEqualTo(Tickler.STATUS.C);
        assertThat(tickler.getPriority()).isEqualTo(Tickler.PRIORITY.High);
        assertThat(tickler.getTaskAssignedTo()).isEqualTo("999999");
        assertThat(tickler.getUpdates()).hasSize(2);
        assertThat(tickler.getUpdates()).anySatisfy(update -> {
            assertThat(update.getStatus()).isEqualTo(Tickler.STATUS.C);
            assertThat(update.getPriority()).isEqualTo("High");
            assertThat(update.getAssignedTo()).isEqualTo("999999");
        });
        verify(manager).updateTickler(mockLoggedInInfo, tickler);
    }

    @Test
    void shouldPreserveOriginalTimestamp_whenAddingComment() {
        mockRequest.setParameter("newMessage", "new comment");
        when(manager.updateTickler(any(), any())).thenReturn(true);
        assertThat(editAction().editTickler()).isEqualTo("close");
        assertThat(tickler.getComments()).hasSize(1);
        assertThat(tickler.getUpdates()).hasSize(1);
        assertThat(tickler.getUpdates().iterator().next().getUpdateDate()).isEqualTo(new Date(1000));
        verify(manager).updateTickler(mockLoggedInInfo, tickler);
    }

    @Test
    void shouldAvoidInventingUpdates_whenFormIsUnchanged() {
        assertThat(editAction().editTickler()).isEqualTo("close");
        assertUnchanged();
    }

    @Test
    void shouldSaveLeapDay_andReturnSuccessSentinel() throws Exception {
        mockRequest.setParameter("xml_appointment_date", "2028-02-29");
        when(manager.addTickler(any(), any())).thenAnswer(call -> {
            Tickler saved = call.getArgument(1);
            assertThat(saved.getServiceDate()).isEqualTo(TicklerFormDate.parse("2028-02-29"));
            saved.setId(789);
            return true;
        });
        assertThat(new DbTicklerAdd2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockRequest.getAttribute("rowsAffected")).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "post", "Post", "PO\u017fT"})
    void shouldRejectNonPostAdd_withoutChangingTicklers(String method) throws Exception {
        mockRequest.setMethod(method);
        assertThat(new DbTicklerAdd2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(manager, linkDao);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistNewSuggestionsPrivately_whenTextIsNotAnIdentifier(boolean active) {
        String text = "private-fixture-clinical-suggestion";
        EditTickler2Action action = editAction();
        action.setActiveText(active ? new String[] {text} : new String[0]);
        action.setInactiveText(active ? new String[0] : new String[] {text});
        try (LogCapture logs = LogCapture.forLogger(EditTickler2Action.class)) {
            org.apache.logging.log4j.LogManager.getLogger(EditTickler2Action.class).error("privacy capture control");
            assertThat(action.updateTextSuggest()).isEqualTo("close");
            ArgumentCaptor<TicklerTextSuggest> saved = ArgumentCaptor.forClass(TicklerTextSuggest.class);
            verify(suggestDao).persist(saved.capture());
            assertThat(saved.getValue().getSuggestedText()).isEqualTo(text);
            assertThat(saved.getValue().getActive()).isEqualTo(active);
            assertThat(saved.getValue().getCreator()).isEqualTo("999998");
            assertThat(logs.messages()).containsExactly("privacy capture control");
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }
    }

    private EditTickler2Action editAction() {
        EditTickler2Action action = spy(new EditTickler2Action());
        // CarlosWebTestBase supplies servlet/Spring state; it does not inject Struts' text provider.
        doReturn("tickler.ticklerEdit.arg.error").when(action).getText("tickler.ticklerEdit.arg.error");
        return action;
    }

    private void setDate(String date) {
        if (date == null) {
            mockRequest.removeParameter("xml_appointment_date");
        } else {
            mockRequest.setParameter("xml_appointment_date", date);
        }
    }

    private void assertUnchanged() {
        assertThat(tickler.getComments()).isEmpty();
        assertThat(tickler.getUpdates()).isEmpty();
        assertThat(tickler.getStatus()).isEqualTo(Tickler.STATUS.A);
        assertThat(tickler.getServiceDate()).isEqualTo(TicklerFormDate.parse("2026-03-04"));
        assertThat(tickler.getUpdateDate()).isEqualTo(new Date(1000));
        verify(manager, never()).updateTickler(any(), any());
    }
}
