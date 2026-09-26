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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the attachment handling in {@link DbTicklerAdd2Action} (#3984): picker
 * selections and the legacy forward-from-document pair are folded into one per-type sync,
 * the creator is the session provider, and a refused attachment never fails the save.
 *
 * @since 2026-09-26
 */
@DisplayName("DbTicklerAdd2Action attachment Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("tickler")
class DbTicklerAdd2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private TicklerManager ticklerManager;
    private TicklerAttachmentService ticklerAttachmentService;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/tickler/DbTicklerAdd");
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        ticklerManager = createAndRegisterMock(TicklerManager.class);
        ticklerAttachmentService = createAndRegisterMock(TicklerAttachmentService.class);
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), (String) any()))
                .thenReturn(true);
        doAnswer(invocation -> {
            Tickler saved = invocation.getArgument(1);
            saved.setId(77);
            return true;
        }).when(ticklerManager).addTickler(eq(loggedInInfo), any(Tickler.class));

        request.setParameter("demographic_no", "1001");
        request.setParameter("user_no", "111111");
        request.setParameter("ticklerMessage", "call about results");
        request.setParameter("task_assigned_to", "999998");
        request.setParameter("priority", "Normal");
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should fold the legacy forwarded document into the picker selection and sync once per type")
    @SuppressWarnings("unchecked")
    void shouldSyncUnionOfPickerAndForwardedDocument_whenBothPresent() throws Exception {
        request.setParameter("attachmentsSubmitted", "1");
        request.addParameter("docNo", "11");
        request.addParameter("labNo", "77");
        request.setParameter("docType", "DOC");
        request.setParameter("docId", "12");

        String result = new DbTicklerAdd2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<Tickler> ticklerCaptor = ArgumentCaptor.forClass(Tickler.class);
        verify(ticklerManager).addTickler(eq(loggedInInfo), ticklerCaptor.capture());
        // The hidden user_no field is client-controlled; the creator is the session provider.
        assertThat(ticklerCaptor.getValue().getCreator()).isEqualTo("999998");

        ArgumentCaptor<Map<DocumentType, ? extends Collection<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ticklerAttachmentService).syncAttachments(eq(loggedInInfo), eq(ticklerCaptor.getValue()), captor.capture());
        assertThat(captor.getValue().get(DocumentType.DOC)).containsExactly("11", "12");
        assertThat(captor.getValue().get(DocumentType.LAB)).containsExactly("77");
        assertThat(request.getAttribute("ticklerLinkFailed")).isEqualTo(false);
    }

    @Test
    @DisplayName("should attach a forwarded lab under its named source when the picker was never opened")
    @SuppressWarnings("unchecked")
    void shouldSyncOnlyForwardedType_whenPickerNotSubmitted() throws Exception {
        request.setParameter("docType", "mds");
        request.setParameter("docId", "5");

        new DbTicklerAdd2Action().execute();

        ArgumentCaptor<Map<DocumentType, ? extends Collection<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ticklerAttachmentService).syncAttachments(eq(loggedInInfo), any(Tickler.class), captor.capture());
        assertThat(captor.getValue()).containsOnlyKeys(DocumentType.LAB);
        assertThat(captor.getValue().get(DocumentType.LAB)).containsExactly("MDS:5");
    }

    @Test
    @DisplayName("should not touch attachments when nothing was submitted")
    void shouldNotSync_whenNoAttachmentsSubmitted() throws Exception {
        String result = new DbTicklerAdd2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(ticklerAttachmentService, never()).syncAttachments(any(), any(), any());
    }

    @Test
    @DisplayName("should keep the saved tickler and flag the link failure when an attachment is refused")
    void shouldFlagLinkFailure_whenAttachmentRefused() throws Exception {
        request.setParameter("attachmentsSubmitted", "1");
        request.addParameter("docNo", "11");
        doThrow(new SecurityException("doc attachment does not belong to the patient"))
                .when(ticklerAttachmentService).syncAttachments(any(), any(), any());

        String result = new DbTicklerAdd2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("rowsAffected")).isEqualTo(true);
        assertThat(request.getAttribute("ticklerLinkFailed")).isEqualTo(true);
    }

    @Test
    @DisplayName("should flag and ignore an unknown forwarded document type rather than guess")
    void shouldFlagLinkFailure_whenForwardedTypeUnknown() throws Exception {
        request.setParameter("docType", "XYZ");
        request.setParameter("docId", "5");

        new DbTicklerAdd2Action().execute();

        verify(ticklerAttachmentService, never()).syncAttachments(any(), any(), any());
        assertThat(request.getAttribute("ticklerLinkFailed")).isEqualTo(true);
    }
}
