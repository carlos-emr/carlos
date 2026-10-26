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
package io.github.carlos_emr.carlos.documentManager.actions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #4790: Inbox ▸ Doc Upload sends "Send to provider" as {@code provider}, and a document uploaded to
 * pending docs must be routed to that provider's inbox ({@code "0"}, None, is the unassigned inbox). The
 * action had read the field under another name, so no upload was routed at all.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("document")
@DisplayName("Document upload routes to the provider chosen on the uploader (#4790)")
class DocumentUploadSendToProviderUnitTest extends CarlosUnitTestBase {

    private static final String UPLOADER_JSP = "src/main/webapp/WEB-INF/jsp/documentManager/documentUploader.jsp";
    private static final String ACTION_SOURCE =
            "src/main/java/io/github/carlos_emr/carlos/documentManager/actions/DocumentUpload2Action.java";
    private static final int DOCUMENT_NO = 77;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EDocUtil> eDocUtilMock;
    private MockedStatic<WebApplicationContextUtils> webContextMock;
    private MockHttpServletRequest request;
    private ProviderInboxRoutingDao routingDao;
    private QueueDocumentLinkDao queueLinkDao;
    private Path documentDir;
    private File upload;
    private String originalDocumentDir;
    private boolean originalDocumentDirPresent;

    @BeforeEach
    void setUp() throws IOException {
        originalDocumentDirPresent = CarlosProperties.getInstance().containsKey("DOCUMENT_DIR");
        originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        documentDir = Files.createTempDirectory("document-store");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
        upload = File.createTempFile("document-upload", ".pdf");
        Files.write(upload.toPath(), ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
                .getBytes(StandardCharsets.ISO_8859_1));

        request = new MockHttpServletRequest("POST", "/documentManager/documentUpload");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", "999998");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
        NioFileManager nioFileManager = createAndRegisterMock(NioFileManager.class);
        when(nioFileManager.deleteTempFile(nullable(String.class))).thenAnswer(invocation -> {
            String filePath = invocation.getArgument(0);
            return filePath != null && Files.deleteIfExists(Path.of(filePath));
        });
        createAndRegisterMock(ProgramManager2.class);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
        // LogAction is already mocked statically by CarlosUnitTestBase.
        eDocUtilMock = mockStatic(EDocUtil.class, CALLS_REAL_METHODS);
        eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn(String.valueOf(DOCUMENT_NO));

        routingDao = mock(ProviderInboxRoutingDao.class);
        queueLinkDao = mock(QueueDocumentLinkDao.class);
        WebApplicationContext webContext = mock(WebApplicationContext.class);
        when(webContext.getBean(ProviderInboxRoutingDao.class)).thenReturn(routingDao);
        when(webContext.getBean(QueueDocumentLinkDao.class)).thenReturn(queueLinkDao);
        webContextMock = mockStatic(WebApplicationContextUtils.class);
        webContextMock.when(() -> WebApplicationContextUtils.getRequiredWebApplicationContext(any(ServletContext.class)))
                .thenReturn(webContext);
    }

    @AfterEach
    void tearDown() throws IOException {
        for (MockedStatic<?> mock : new MockedStatic<?>[] {webContextMock, eDocUtilMock, loggedInInfoMock, servletActionContextMock}) {
            if (mock != null) {
                mock.close();
            }
        }
        if (originalDocumentDirPresent) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
        } else {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        }
        Files.deleteIfExists(upload.toPath());
        try (Stream<Path> files = Files.walk(documentDir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** One upload to pending docs, posted the way the uploader page posts it. */
    private void uploadToPendingDocs(String provider) throws Exception {
        request.setParameter("destination", "pendingDocs");
        request.setParameter("destFolder", "");
        request.setParameter("provider", provider);
        request.setParameter("queue", "1");
        DocumentUpload2Action action = new DocumentUpload2Action();
        action.setFiledata(upload);
        action.setFiledataFileName("scan.pdf");
        assertThat(action.executeUpload()).isNull();
    }

    @Test
    @DisplayName("should route the upload to the provider chosen in Send to provider")
    void shouldRouteToChosenProvider_whenSendToProviderIsSet() throws Exception {
        uploadToPendingDocs("999998");

        verify(routingDao).addToProviderInbox("999998", DOCUMENT_NO, "DOC");
        verify(queueLinkDao).addActiveQueueDocumentLink(1, DOCUMENT_NO);
    }

    @Test
    @DisplayName("should route the upload to the unassigned inbox when None is chosen")
    void shouldRouteToUnassigned_whenNoneIsChosen() throws Exception {
        uploadToPendingDocs("0");

        verify(routingDao).addToProviderInbox("0", DOCUMENT_NO, "DOC");
    }

    @Test
    @DisplayName("should read every upload field under the name the uploader page sends it")
    void shouldReadUploadFields_byTheUploaderPagesNames() throws IOException {
        // The page posts FormData built field by field; a name the action reads that the page never sends is
        // always null (a 2025 rename did that to "provider").
        String action = Files.readString(Path.of(ACTION_SOURCE), StandardCharsets.UTF_8);
        String upload = action.substring(action.indexOf("public String executeUpload()"),
                action.indexOf("public String setUploadDestination()"));
        Set<String> read = matches(upload, "getParameter\\(\"([A-Za-z0-9]+)\"\\)");
        Set<String> sent = matches(Files.readString(Path.of(UPLOADER_JSP), StandardCharsets.UTF_8),
                "formData\\.append\\('([A-Za-z0-9]+)'");

        assertThat(read).contains("destination", "provider", "queue");
        assertThat(sent).containsAll(read);
    }

    private static Set<String> matches(String text, String regex) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
