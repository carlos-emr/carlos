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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.Doc2PDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A Messenger PDF download returns a document of the message its attachment list was opened for
 * (issue #4673). It used to read the list from the session, where the list last opened in any
 * window had put its message's PDFs.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("messenger")
@DisplayName("Messenger PDF attachment download")
class MsgViewPDF2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<Doc2PDF> doc2PdfMock;
    private SecurityInfoManager securityInfoManager;
    private MessageTblDao messageTblDao;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final List<String> streamed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        messageTblDao = createAndRegisterMock(MessageTblDao.class);
        createAndRegisterMock(MessageListDao.class);
        createAndRegisterMock(OscarCommLocationsDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("101");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_msg"), eq("r"), isNull())).thenReturn(true);

        request = new MockHttpServletRequest("POST", "/carlos/messenger/ViewPDFFile");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        doc2PdfMock = mockStatic(Doc2PDF.class, Mockito.CALLS_REAL_METHODS);
        doc2PdfMock.when(() -> Doc2PDF.PrintPDFFromBin(any(HttpServletResponse.class), anyString()))
                .thenAnswer(invocation -> streamed.add(invocation.getArgument(1)));

        when(messageTblDao.find(100)).thenReturn(message("FAKEPATIENTAFIRST", "FAKEPATIENTASECOND"));
    }

    @AfterEach
    void tearDown() {
        doc2PdfMock.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    void shouldStreamTheNamedMessagesDocument_whenAnotherViewerWindowOpenedLater() throws Exception {
        // Another viewer window opened another patient's message after this list was shown; the
        // old code read that list from the session.
        request.getSession().setAttribute("PDFAttachment", pdfList("FAKEPATIENTBFIRST"));

        String result = download("100", "1");

        assertThat(streamed).containsExactly("FAKEPATIENTASECOND");
        // The action streamed the bytes itself: no result page may follow them.
        assertThat(result).isEqualTo(ActionSupport.NONE);
    }

    @Test
    void shouldStreamNothing_whenNoMessageIsNamed() throws Exception {
        request.getSession().setAttribute("PDFAttachment", pdfList("FAKEPATIENTBFIRST"));

        String result = download(null, "0");

        assertThat(streamed).isEmpty();
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldStreamNothing_whenTheMessageHasNoPdfs() throws Exception {
        MessageTbl withoutPdfs = new MessageTbl();
        withoutPdfs.setSentByNo("101");
        when(messageTblDao.find(200)).thenReturn(withoutPdfs);

        String result = download("200", "0");

        assertThat(streamed).isEmpty();
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldAnswerNotFound_whenTheIndexIsBeyondTheMessagesPdfs() throws Exception {
        String result = download("100", "2");

        assertThat(streamed).isEmpty();
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldAnswerBadRequest_whenTheIndexIsNotANumber() throws Exception {
        String result = download("100", "first");

        assertThat(streamed).isEmpty();
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(messageTblDao);
    }

    @Test
    void shouldRefuseTheDownload_whenMessengerReadIsMissing() {
        when(securityInfoManager.hasPrivilege(any(), eq("_msg"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> download("100", "0"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_msg)");
        assertThat(streamed).isEmpty();
    }

    private String download(String messageId, String fileId) throws Exception {
        MsgViewPDF2Action action = new MsgViewPDF2Action();
        action.setAttId(messageId);
        action.setFile_id(fileId);
        return action.execute();
    }

    private static MessageTbl message(String... contents) {
        MessageTbl message = new MessageTbl();
        message.setSentByNo("101");
        message.setPdfAttachment(pdfList(contents).getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private static String pdfList(String... contents) {
        MsgSessionBean records = new MsgSessionBean();
        for (String content : contents) {
            records.setAppendPDFAttachment(content, "FAKE title");
            records.setCurrentAttachmentCount(records.getCurrentAttachmentCount() + 1);
        }
        return records.getPDFAttachment();
    }
}
