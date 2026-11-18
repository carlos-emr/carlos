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
package io.github.carlos_emr.carlos.billing.CA.ON.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisplayInvoiceLogo2Action}: the Ontario third-party invoice's letterhead image is served
 * with its bytes, only as an image, under its own file name, and a missing or unusable letterhead answers 404.
 */
@DisplayName("DisplayInvoiceLogo2Action")
@Tag("unit")
@Tag("billing")
class DisplayInvoiceLogo2ActionUnitTest extends CarlosUnitTestBase {

    /** The smallest valid PNG: one opaque pixel. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final String DOCTYPE = "invoice letterhead";

    @TempDir
    Path documentDir;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private DocumentDao documentDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private String previousDocumentDir;
    private String previousDoctype;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DocumentDao.class, documentDao);

        request = new MockHttpServletRequest("GET", "/carlos/billing/ca/on/DisplayInvoiceLogo");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull())).thenReturn(true);

        CarlosProperties properties = CarlosProperties.getInstance();
        previousDocumentDir = properties.getProperty("DOCUMENT_DIR");
        previousDoctype = properties.getProperty("invoice_head_logo_doctype");
        properties.setProperty("DOCUMENT_DIR", documentDir.toString());
        properties.setProperty("invoice_head_logo_doctype", DOCTYPE);
    }

    @AfterEach
    void tearDown() {
        restore("DOCUMENT_DIR", previousDocumentDir);
        restore("invoice_head_logo_doctype", previousDoctype);
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, previous);
        }
    }

    /** Files one active letterhead document with the given file name, writing the bytes when given. */
    private void letterhead(String fileName, byte[] bytes) throws Exception {
        if (bytes != null) {
            Files.write(documentDir.resolve(fileName), bytes);
        }
        Document document = new Document();
        document.setDocfilename(fileName);
        when(documentDao.findByDoctype(DOCTYPE)).thenReturn(List.of(document));
    }

    @Test
    @DisplayName("should serve the letterhead's bytes as an image under its own file name")
    void shouldServeLetterheadBytes_whenLetterheadIsPng() throws Exception {
        letterhead("FAKE-letterhead.png", PNG);

        assertThat(new DisplayInvoiceLogo2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).startsWith("image/png");
        assertThat(response.getContentAsByteArray()).isEqualTo(PNG);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline; filename=\"FAKE-letterhead.png\"");
    }

    @ParameterizedTest
    @CsvSource({"FAKE-LOGO.JPG, image/jpeg", "FAKE-logo.jpeg, image/jpeg", "FAKE-logo.gif, image/gif",
            "FAKE-logo.bmp, image/bmp"})
    @DisplayName("should serve each image type the invoice can show, whatever the extension's case")
    void shouldServeImage_forEachImageType(String fileName, String contentType) throws Exception {
        letterhead(fileName, PNG);

        new DisplayInvoiceLogo2Action().execute();

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).startsWith(contentType);
        assertThat(response.getContentAsByteArray()).isEqualTo(PNG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAKE-letterhead.html", "FAKE-letterhead.svg", "FAKE-letterhead.js", "FAKE-letterhead.pdf",
            "FAKE-letterhead"})
    @DisplayName("should answer 404 without content for a letterhead that is not an image the invoice can show")
    void shouldAnswerNotFound_whenLetterheadIsNotAnImage(String fileName) throws Exception {
        letterhead(fileName, "<p>FAKE</p>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(new DisplayInvoiceLogo2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    @DisplayName("should answer 404 when the clinic has no letterhead")
    void shouldAnswerNotFound_whenNoLetterheadExists() throws Exception {
        when(documentDao.findByDoctype(DOCTYPE)).thenReturn(List.of());

        assertThat(new DisplayInvoiceLogo2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should answer 404 when the letterhead's file is missing")
    void shouldAnswerNotFound_whenLetterheadFileIsMissing() throws Exception {
        letterhead("FAKE-missing.png", null);

        assertThat(new DisplayInvoiceLogo2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should answer 404 when the document directory does not exist")
    void shouldAnswerNotFound_whenDocumentDirectoryIsMissing() throws Exception {
        letterhead("FAKE-letterhead.png", PNG);
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.resolve("absent").toString());

        assertThat(new DisplayInvoiceLogo2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should tell the invoice to show a letterhead that is a present image")
    void shouldReportServableLogo_whenLetterheadIsPresentImage() throws Exception {
        letterhead("FAKE-letterhead.png", PNG);

        assertThat(DisplayInvoiceLogo2Action.hasServableLogo()).isTrue();
    }

    @Test
    @DisplayName("should tell the invoice to keep the clinic's details without a letterhead it can show")
    void shouldReportNoServableLogo_whenLetterheadCannotBeShown() throws Exception {
        when(documentDao.findByDoctype(DOCTYPE)).thenReturn(List.of());
        assertThat(DisplayInvoiceLogo2Action.hasServableLogo()).as("no letterhead").isFalse();

        letterhead("FAKE-missing.png", null);
        assertThat(DisplayInvoiceLogo2Action.hasServableLogo()).as("its file is missing").isFalse();

        letterhead("FAKE-letterhead.pdf", PNG);
        assertThat(DisplayInvoiceLogo2Action.hasServableLogo()).as("not an image type").isFalse();
    }

    @Test
    @DisplayName("should refuse a user without billing read before looking for the letterhead")
    void shouldThrowSecurityException_whenUserCannotReadBilling() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> new DisplayInvoiceLogo2Action().execute()).isInstanceOf(SecurityException.class);

        verifyNoInteractions(documentDao);
    }
}
