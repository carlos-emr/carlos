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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.ErrorPageMessage;

/**
 * Tests for {@link MsgViewPDF2Action}'s direct-response contract (#2667).
 *
 * <p>The action streams one PDF out of the attachment XML held in the session.
 * Every path owns the response and ends with {@code NONE}, so Struts never renders
 * a page over the PDF or over an error status, and a request with nothing to show
 * gets a 4xx instead of a blank page.
 *
 * @since 2026-10-05
 */
@DisplayName("MsgViewPDF2Action")
@Tag("integration")
@Tag("messenger")
class MsgViewPDF2ActionTest extends CarlosWebTestBase {

    private static final byte[] FIRST_PDF = "%PDF-1.4 first attachment".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SECOND_PDF = "%PDF-1.4 second attachment".getBytes(StandardCharsets.US_ASCII);

    private MsgViewPDF2Action action;

    @BeforeEach
    void setUp() {
        // The base class has already bound the action context and the security mock
        action = new MsgViewPDF2Action();
    }

    /** Holds the two PDFs in the session as attachment XML written by {@link MsgSessionBean}. */
    private void holdTwoAttachments() {
        MsgSessionBean bean = new MsgSessionBean();
        bean.setAppendPDFAttachment(encode(FIRST_PDF), "first");
        bean.setCurrentAttachmentCount(1);
        bean.setAppendPDFAttachment(encode(SECOND_PDF), "second");
        setSessionAttribute("PDFAttachment", bean.getPDFAttachment());
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("should throw SecurityException when _msg read privilege is denied")
    void shouldThrowSecurityException_whenReadPrivilegeDenied() {
        denyPrivilege("_msg", "r");
        holdTwoAttachments();
        action.setFile_id("0");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should stream the chosen PDF and return NONE")
    void shouldStreamChosenPdf_andReturnNone() throws Exception {
        allowPrivilege("_msg", "r");
        holdTwoAttachments();
        action.setFile_id("1");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(getMockResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(getMockResponse().getContentAsByteArray()).isEqualTo(SECOND_PDF);
    }

    @ParameterizedTest(name = "file_id={0}")
    @ValueSource(strings = {"-1", "2", "2147483647"})
    @DisplayName("should answer an out-of-range file_id with 400 and return NONE")
    void shouldReturn400_whenFileIdIsOutOfRange(String fileId) throws Exception {
        allowPrivilege("_msg", "r");
        holdTwoAttachments();
        action.setFile_id(fileId);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.invalidFileId");
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest(name = "file_id={0}")
    @NullSource
    @ValueSource(strings = {"", "one", "1.0", "99999999999"})
    @DisplayName("should answer a missing or non-numeric file_id with 400 and return NONE")
    void shouldReturn400_whenFileIdIsNotANumber(String fileId) throws Exception {
        allowPrivilege("_msg", "r");
        holdTwoAttachments();
        action.setFile_id(fileId);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.invalidFileId");
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should answer attachment XML it cannot read with 500 and return NONE")
    void shouldReturn500_whenAttachmentXmlIsMalformed() throws Exception {
        allowPrivilege("_msg", "r");
        setSessionAttribute("PDFAttachment", "<PDF><CONTENT>" + encode(FIRST_PDF));
        action.setFile_id("0");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.unreadable");
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should answer an attachment that failed to render with 500, not junk bytes as a PDF")
    void shouldReturn500_whenAttachmentIsNotAPdf() throws Exception {
        allowPrivilege("_msg", "r");
        MsgSessionBean bean = new MsgSessionBean();
        // A render failure is stored with status BAD and the content "null"
        bean.setAppendPDFAttachment(null, "failed");
        setSessionAttribute("PDFAttachment", bean.getPDFAttachment());
        action.setFile_id("0");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.unreadable");
        assertThat(getMockResponse().getContentType()).isNull();
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should check file_id before reading the session, answering 400 even when the attachment XML is unreadable")
    void shouldReturn400BeforeReadingAttachmentXml_whenFileIdIsNotANumber() throws Exception {
        allowPrivilege("_msg", "r");
        setSessionAttribute("PDFAttachment", "<PDF><CONTENT>" + encode(FIRST_PDF));
        action.setFile_id("abc");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.invalidFileId");
    }

    @ParameterizedTest(name = "PDFAttachment={0}, file_id={1}")
    @CsvSource(value = {"NULL, 0", "NULL, 99", "'', 0", "'', 99"}, nullValues = "NULL")
    @DisplayName("should answer a session with no attachment with 404 and return NONE, not a blank page")
    void shouldReturn404_whenSessionHoldsNoAttachment(String pdfAttachment, String fileId) throws Exception {
        allowPrivilege("_msg", "r");
        setSessionAttribute("PDFAttachment", pdfAttachment);
        action.setFile_id(fileId);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.noAttachment");
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest(name = "file_id={0}")
    @NullSource
    @ValueSource(strings = {"", "abc"})
    @DisplayName("should still answer a missing or non-numeric file_id with 400 when the session holds no attachment")
    void shouldReturn400_whenFileIdIsNotANumberAndSessionHoldsNoAttachment(String fileId) throws Exception {
        allowPrivilege("_msg", "r");
        action.setFile_id(fileId);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(getMockRequest().getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.invalidFileId");
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {MsgViewPDF2Action.INVALID_FILE_ID, MsgViewPDF2Action.NO_ATTACHMENT, MsgViewPDF2Action.UNREADABLE})
    @DisplayName("should show error messages that exist in the bundle, not raw keys")
    void shouldUseBundleKeys_forErrorMessages(String key) {
        assertThat(ResourceBundle.getBundle("oscarResources", Locale.ENGLISH).getString(key)).isNotBlank();
    }
}
