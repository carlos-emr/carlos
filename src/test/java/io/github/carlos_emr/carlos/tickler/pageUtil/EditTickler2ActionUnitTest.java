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
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.commn.dao.TicklerTextSuggestDao;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
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
        ticklerManager = createAndRegisterMock(TicklerManager.class);
        org.mockito.Mockito.lenient().when(ticklerManager.updateTickler(any(), any())).thenReturn(true);
        ticklerAttachmentService = createAndRegisterMock(TicklerAttachmentService.class);
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), (String) any()))
                .thenReturn(true);

        var transactions = createAndRegisterMock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call ->
                new org.springframework.transaction.support.SimpleTransactionStatus());
        tickler = new Tickler();
        tickler.setId(42);
        tickler.setDemographicNo(1001);
        tickler.setStatus(Tickler.STATUS.A);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(TicklerFormDate.parse("2026-09-27"));
        tickler.setCreator("999998");
        when(ticklerManager.getTickler(loggedInInfo, 42)).thenReturn(tickler);
    }

    @AfterEach
    void tearDown() {
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
        verify(ticklerAttachmentService).syncAttachments(eq(loggedInInfo), eq(tickler), captor.capture(), isNull());
        assertThat(captor.getValue().get(DocumentType.DOC)).containsExactly("11", "12");
        assertThat(captor.getValue().get(DocumentType.LAB)).containsExactly("77");
        assertThat(captor.getValue().get(DocumentType.EFORM)).isEmpty();
    }

    @Test
    @DisplayName("should pass the rows the form rendered to the sync when the rendered marker is present")
    @SuppressWarnings("unchecked")
    void shouldPassRenderedAttachments_whenRenderedMarkerPresent() {
        unchangedEditParameters();
        request.setParameter("attachmentsSubmitted", "1");
        request.setParameter("attachmentsRendered", "1");
        request.addParameter("docNo", "11");
        request.addParameter("renderedDocNo", "11", "12");
        request.addParameter("renderedLabNo", "HL7:77");

        assertThat(new TestableEditTickler2Action().execute()).isEqualTo("close");

        ArgumentCaptor<Map<DocumentType, Set<String>>> submitted = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<DocumentType, Set<String>>> rendered = ArgumentCaptor.forClass(Map.class);
        verify(ticklerAttachmentService).syncAttachments(eq(loggedInInfo), eq(tickler), submitted.capture(), rendered.capture());
        assertThat(submitted.getValue().get(DocumentType.DOC)).containsExactly("11");
        assertThat(rendered.getValue().get(DocumentType.DOC)).containsExactly("11", "12");
        assertThat(rendered.getValue().get(DocumentType.LAB)).containsExactly("HL7:77");
        assertThat(rendered.getValue().get(DocumentType.EFORM)).isEmpty();
    }

    @Test
    @DisplayName("should leave attachments untouched when the picker was never opened")
    void shouldNotSyncAttachments_whenMarkerMissing() {
        unchangedEditParameters();
        request.addParameter("docNo", "11");

        String result = new TestableEditTickler2Action().execute();

        assertThat(result).isEqualTo("close");
        verify(ticklerAttachmentService, never()).syncAttachments(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should report an error when an attachment is refused")
    void shouldReturnError_whenAttachmentRefused() {
        unchangedEditParameters();
        request.setParameter("attachmentsSubmitted", "1");
        request.addParameter("docNo", "11");
        doThrow(new SecurityException("doc attachment does not belong to the patient"))
                .when(ticklerAttachmentService).syncAttachments(any(), any(), any(), any());

        String result = new TestableEditTickler2Action().execute();

        assertThat(result).isEqualTo("error");
    }

    @Test
    @DisplayName("should not update the tickler at all when a document attachment is refused")
    void shouldNotUpdateTickler_whenAttachmentAuthorizationDenied() {
        unchangedEditParameters();
        request.setParameter("status", "C");
        request.setParameter("newMessage", "must not be saved when the attachment is refused");
        request.setParameter("attachmentsSubmitted", "1");
        request.setParameter("docNo", "11");
        doThrow(new SecurityException("Document is not available"))
                .when(ticklerAttachmentService).syncAttachments(any(), any(), any(), any());

        assertThat(new TestableEditTickler2Action().execute()).isEqualTo("error");

        verify(ticklerManager, never()).updateTickler(any(), any());
        assertThat(tickler.getStatus()).isEqualTo(Tickler.STATUS.A);
        assertThat(tickler.getComments()).isEmpty();
        assertThat(tickler.getUpdates()).isEmpty();
    }

    @Test
    @DisplayName("should authorise and sync attachments before writing the tickler's fields")
    void shouldSyncAttachmentsBeforeUpdatingTickler_whenBothChange() {
        unchangedEditParameters();
        request.setParameter("status", "C");
        request.setParameter("attachmentsSubmitted", "1");
        request.setParameter("docNo", "11");

        assertThat(new TestableEditTickler2Action().execute()).isEqualTo("close");

        var order = org.mockito.Mockito.inOrder(ticklerAttachmentService, ticklerManager);
        order.verify(ticklerAttachmentService).syncAttachments(eq(loggedInInfo), eq(tickler), any(), any());
        order.verify(ticklerManager).updateTickler(loggedInInfo, tickler);
    }

    @Test
    @DisplayName("should roll back synced attachments when the tickler update then fails")
    void shouldRollbackAttachments_whenTicklerUpdateFails() {
        var dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tickler-edit-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE attach_probe (id INT PRIMARY KEY, attached INT)");
        jdbc.update("INSERT INTO attach_probe VALUES (42, 0)");
        registerMock(org.springframework.transaction.PlatformTransactionManager.class,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        unchangedEditParameters();
        request.setParameter("newMessage", "comment whose save fails");
        request.setParameter("attachmentsSubmitted", "1");
        request.setParameter("docNo", "11");
        doAnswer(call -> {
            jdbc.update("UPDATE attach_probe SET attached=attached+1 WHERE id=42");
            return null;
        }).when(ticklerAttachmentService).syncAttachments(any(), any(), any(), any());
        when(ticklerManager.updateTickler(any(), any())).thenReturn(false);

        assertThat(new TestableEditTickler2Action().execute()).isEqualTo("error");

        assertThat(jdbc.queryForObject("SELECT attached FROM attach_probe WHERE id=42", Integer.class)).isZero();
        jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void shouldRollbackFieldsAndComment_whenAttachmentSaveFails() {
        var dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tickler-edit-" + java.util.UUID.randomUUID());
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        dataSource.setURL(dataSource.getURL() + ";DB_CLOSE_DELAY=-1");
        jdbc.execute("CREATE TABLE edit_probe (id INT PRIMARY KEY, comment_count INT)");
        jdbc.update("INSERT INTO edit_probe VALUES (42, 0)");
        registerMock(org.springframework.transaction.PlatformTransactionManager.class,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        unchangedEditParameters();
        request.setParameter("newMessage", "retry must not duplicate this comment");
        request.setParameter("attachmentsSubmitted", "1");
        request.setParameter("docNo", "11");
        when(ticklerManager.updateTickler(any(), any())).thenAnswer(call -> {
            jdbc.update("UPDATE edit_probe SET comment_count=comment_count+1 WHERE id=42");
            return true;
        });
        doThrow(new IllegalArgumentException("Attachment does not belong to this patient"))
                .when(ticklerAttachmentService).syncAttachments(any(), any(), any(), any());
        assertThat(new TestableEditTickler2Action().execute()).isEqualTo("error");
        assertThat(jdbc.queryForObject("SELECT comment_count FROM edit_probe WHERE id=42", Integer.class)).isZero();
        jdbc.execute("DROP ALL OBJECTS");
    }


    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "0"})
    void shouldIgnoreEmptyListPlaceholders_withoutPersistingSuggestions(String placeholder) {
        TicklerTextSuggestDao dao = createAndRegisterMock(TicklerTextSuggestDao.class);
        var action = new TestableEditTickler2Action();
        action.setActiveText(new String[]{placeholder});
        action.setInactiveText(new String[]{placeholder});

        assertThat(action.execute()).isEqualTo("close");
        verifyNoInteractions(dao);
    }

    @Test
    void shouldPreserveExactNewText_andSkipPlaceholdersInBothLists() {
        TicklerTextSuggestDao dao = createAndRegisterMock(TicklerTextSuggestDao.class);
        var action = new TestableEditTickler2Action();
        action.setActiveText(new String[]{"", "0", "  Active suggestion <follow-up>  "});
        action.setInactiveText(new String[]{" ", "0", "Inactive suggestion"});

        assertThat(action.execute()).isEqualTo("close");
        ArgumentCaptor<TicklerTextSuggest> saved = ArgumentCaptor.forClass(TicklerTextSuggest.class);
        verify(dao, org.mockito.Mockito.times(2)).persist(saved.capture());
        assertThat(saved.getAllValues()).extracting(TicklerTextSuggest::getSuggestedText)
                .containsExactly("  Active suggestion <follow-up>  ", "Inactive suggestion");
        assertThat(saved.getAllValues()).extracting(TicklerTextSuggest::getActive).containsExactly(true, false);
        assertThat(saved.getAllValues()).allSatisfy(value -> {
            assertThat(value.getCreator()).isEqualTo("999998");
            assertThat(value.getCreateDate()).isNotNull();
        });
        verify(dao, never()).merge(any());
    }

    @Test
    void shouldMoveExistingSuggestions_withoutRewritingTheirTextOrOwnership() {
        TicklerTextSuggestDao dao = createAndRegisterMock(TicklerTextSuggestDao.class);
        TicklerTextSuggest active = new TicklerTextSuggest();
        active.setId(11);
        active.setActive(false);
        active.setSuggestedText("Existing active");
        active.setCreator("other-provider");
        active.setCreateDate(new Date(1234));
        TicklerTextSuggest inactive = new TicklerTextSuggest();
        inactive.setId(12);
        inactive.setActive(true);
        inactive.setSuggestedText("Existing inactive");
        inactive.setCreator("other-provider");
        inactive.setCreateDate(new Date(5678));
        when(dao.find(Integer.valueOf(11))).thenReturn(active);
        when(dao.find(Integer.valueOf(12))).thenReturn(inactive);
        var action = new TestableEditTickler2Action();
        action.setActiveText(new String[]{"11", ""});
        action.setInactiveText(new String[]{"0", "12"});

        assertThat(action.execute()).isEqualTo("close");
        verify(dao).merge(active);
        verify(dao).merge(inactive);
        verify(dao, never()).persist(any());
        assertThat(active.getActive()).isTrue();
        assertThat(inactive.getActive()).isFalse();
        assertThat(active.getSuggestedText()).isEqualTo("Existing active");
        assertThat(inactive.getSuggestedText()).isEqualTo("Existing inactive");
        assertThat(active.getCreator()).isEqualTo("other-provider");
        assertThat(inactive.getCreator()).isEqualTo("other-provider");
        assertThat(active.getCreateDate()).isEqualTo(new Date(1234));
        assertThat(inactive.getCreateDate()).isEqualTo(new Date(5678));
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "text:literal", "  Exact text  "})
    void shouldPersistPrefixedLiteralText_withoutTreatingNumericTextAsAnId(String text) {
        TicklerTextSuggestDao dao = createAndRegisterMock(TicklerTextSuggestDao.class);
        request.setParameter("suggestionValueFormat", "prefixed");
        var action = new TestableEditTickler2Action();
        action.setActiveText(new String[]{"", "text:" + text});
        action.setInactiveText(new String[]{"text:" + text, "text: ", "text:"});

        assertThat(action.execute()).isEqualTo("close");
        ArgumentCaptor<TicklerTextSuggest> saved = ArgumentCaptor.forClass(TicklerTextSuggest.class);
        verify(dao, org.mockito.Mockito.times(2)).persist(saved.capture());
        assertThat(saved.getAllValues()).extracting(TicklerTextSuggest::getSuggestedText)
                .containsExactly(text, text);
        assertThat(saved.getAllValues()).extracting(TicklerTextSuggest::getActive).containsExactly(true, false);
        org.mockito.Mockito.verifyNoMoreInteractions(dao);
        verify(dao, never()).merge(any());
    }

    @Test
    void shouldPreserveLegacyTextPrefixes_whenNoNewFormatIsDeclared() {
        TicklerTextSuggestDao dao = createAndRegisterMock(TicklerTextSuggestDao.class);
        var action = new TestableEditTickler2Action();
        action.setActiveText(new String[]{"text:literal"});
        action.setInactiveText(new String[]{""});

        assertThat(action.execute()).isEqualTo("close");
        ArgumentCaptor<TicklerTextSuggest> saved = ArgumentCaptor.forClass(TicklerTextSuggest.class);
        verify(dao).persist(saved.capture());
        assertThat(saved.getValue().getSuggestedText()).isEqualTo("text:literal");
    }

}
