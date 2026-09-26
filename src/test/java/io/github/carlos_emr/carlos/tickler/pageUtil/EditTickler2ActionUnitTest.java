/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EditTickler2Action}: the verb gate that precedes every side effect, and
 * the attachment sync that runs only when the picker marker is present (#3984).
 *
 * @since 2026-09-26
 */
@DisplayName("EditTickler2Action Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("tickler")
@Tag("security")
class EditTickler2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<DateUtils> dateUtilsMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private TicklerManager ticklerManager;
    private TicklerAttachmentService ticklerAttachmentService;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private Tickler tickler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/tickler/EditTickler");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        // The action's service-date comparison always re-parses the submitted date; the
        // configured DATE_FORMAT is not what this test is about.
        dateUtilsMock = mockStatic(DateUtils.class);
        dateUtilsMock.when(() -> DateUtils.parseDate(anyString(), any())).thenReturn(new Date());

        ticklerManager = createAndRegisterMock(TicklerManager.class);
        ticklerAttachmentService = createAndRegisterMock(TicklerAttachmentService.class);
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), (String) any()))
                .thenReturn(true);

        tickler = new Tickler();
        tickler.setId(42);
        tickler.setDemographicNo(1001);
        tickler.setStatus(Tickler.STATUS.A);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(new Date());
        tickler.setCreator("999998");
        when(ticklerManager.getTickler(loggedInInfo, 42)).thenReturn(tickler);
    }

    @AfterEach
    void tearDown() {
        dateUtilsMock.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    /** ActionSupport.getText needs a Struts container; the message text is irrelevant here. */
    private static final class TestableEditTickler2Action extends EditTickler2Action {
        @Override
        public String getText(String key) {
            return key;
        }
    }

    private void unchangedEditParameters() {
        request.setParameter("method", "editTickler");
        request.setParameter("ticklerNo", "42");
        request.setParameter("status", "A");
        request.setParameter("priority", "Normal");
        request.setParameter("assignedToProviders", "999998");
        request.setParameter("xml_appointment_date", new java.text.SimpleDateFormat("yyyy-MM-dd").format(tickler.getServiceDate()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should reject every non-POST verb before any privilege check or side effect")
    void shouldReject_whenVerbIsNotPost(String verb) {
        request.setMethod(verb);
        request.setParameter("method", "editTickler");
        request.setParameter("ticklerNo", "42");
        request.setParameter("attachmentsSubmitted", "1");
        request.setParameter("docNo", "11");

        String result = new TestableEditTickler2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, ticklerManager, ticklerAttachmentService);
    }

    @Test
    @DisplayName("should synchronise attachments only when the picker marker is present")
    @SuppressWarnings("unchecked")
    void shouldSyncAttachments_whenMarkerPresent() {
        unchangedEditParameters();
        request.setParameter("attachmentsSubmitted", "1");
        request.addParameter("docNo", "11", "12");
        request.addParameter("labNo", "77");

        String result = new TestableEditTickler2Action().execute();

        assertThat(result).isEqualTo("close");
        ArgumentCaptor<Map<DocumentType, Set<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ticklerAttachmentService).syncAttachments(eq(loggedInInfo), eq(tickler), captor.capture());
        assertThat(captor.getValue().get(DocumentType.DOC)).containsExactly("11", "12");
        assertThat(captor.getValue().get(DocumentType.LAB)).containsExactly("77");
        assertThat(captor.getValue().get(DocumentType.EFORM)).isEmpty();
    }

    @Test
    @DisplayName("should leave attachments untouched when the picker was never opened")
    void shouldNotSyncAttachments_whenMarkerMissing() {
        unchangedEditParameters();
        request.addParameter("docNo", "11");

        String result = new TestableEditTickler2Action().execute();

        assertThat(result).isEqualTo("close");
        verify(ticklerAttachmentService, never()).syncAttachments(any(), any(), any());
    }

    @Test
    @DisplayName("should report an error when an attachment is refused")
    void shouldReturnError_whenAttachmentRefused() {
        unchangedEditParameters();
        request.setParameter("attachmentsSubmitted", "1");
        request.addParameter("docNo", "11");
        doThrow(new SecurityException("doc attachment does not belong to the patient"))
                .when(ticklerAttachmentService).syncAttachments(any(), any(), any());

        String result = new TestableEditTickler2Action().execute();

        assertThat(result).isEqualTo("error");
    }
}
