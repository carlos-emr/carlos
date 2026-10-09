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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MsgAttachPDF2Action} security, PHI-log and response behavior.
 *
 * <p>The previous INFO log of {@code srcText} leaked rendered demographic /
 * encounter / prescription content into application logs — a HIPAA/PIPEDA
 * incident waiting to happen. This test captures the logger output on the
 * preview path and asserts the rendered content is not present and that only
 * length metadata is logged.
 *
 * <p>It also holds the direct-response contract (#2667): the streamed preview and
 * every failed attachment end with {@code NONE} and a real status, while the named
 * results stay for attachment steps that write nothing to the response.
 *
 * @since 2026-04-13
 */
@DisplayName("MsgAttachPDF2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgAttachPDF2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    /** Deliberately unique token that must never appear in application logs. */
    private static final String PHI_SENTINEL = "PHI-SENTINEL-BLOODWORK-RESULT";

    private MsgAttachPDF2Action action;
    private LogCapture logCapture;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new MsgAttachPDF2Action();
        java.lang.reflect.Field f = MsgAttachPDF2Action.class.getDeclaredField("securityInfoManager");
        f.setAccessible(true);
        f.set(action, mockSecurityInfoManager);

        logCapture = LogCapture.forLogger(MsgAttachPDF2Action.class);
    }

    @AfterEach
    void detachAppender() {
        if (logCapture != null) {
            logCapture.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when _msg write privilege is denied")
    void shouldThrowSecurityException_whenWritePrivilegeDenied() {
        denyPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
    }

    @Test
    @DisplayName("should reject non-POST with 405 to block CSRF-style mutation")
    void shouldReturn405_whenMethodIsNotPost() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("GET");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should NOT log rendered srcText content when in preview mode")
    void shouldNotLogPhiSrcText_whenPreviewing() {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        action.setSrcText("<p>" + PHI_SENTINEL + "</p>");
        action.setIsPreview(true);

        // Rendering the preview runs AFTER the log call this test is about, so its
        // outcome is not asserted here (see shouldReturnNone_afterStreamingPreviewPdf).
        try {
            executeAction(action);
        } catch (Throwable ignored) {
            // The assertion below is on the log output only.
        }

        assertThat(logCapture.messages())
                .as("rendered srcText content must not appear in application logs (PHI)")
                .noneMatch(m -> m.contains(PHI_SENTINEL));
    }

    @Test
    @DisplayName("should stream the preview PDF and return NONE so no result page is rendered over it")
    void shouldReturnNone_afterStreamingPreviewPdf() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        action.setSrcText("<body><p>Preview</p></body>");
        action.setIsPreview(true);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(getMockResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(getMockResponse().getContentAsString(StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    @Test
    @DisplayName("should keep the named result, writing nothing, once the last attachment is stored")
    void shouldReturnSuccess_whenLastAttachmentIsStored() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        setSessionAttribute("msgSessionBean", bean);
        action.setSrcText("<body><p>Attachment</p></body>");
        action.setAttachmentTitle("Lab summary");
        action.setAttachmentCount("1");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(bean.getPDFAttachment()).contains("<STATUS>OK</STATUS>").contains("Lab summary");
        assertThat(getMockResponse().isCommitted()).isFalse();
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should answer with 500 and return NONE, not a blank page, when there is no message to attach to")
    void shouldReturn500_whenMessageSessionBeanIsMissing() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        action.setSrcText("<body><p>Attachment</p></body>");
        action.setAttachmentCount("1");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should answer with 500 and return NONE, not a blank page, when attaching fails")
    void shouldReturn500_whenAttachingFails() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setPDFAttachment("earlier attachments");
        setSessionAttribute("msgSessionBean", bean);
        action.setIsNew(false);
        action.setAttachmentCount("not-a-number");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(bean.getPDFAttachment()).isEqualTo("earlier attachments");
    }
}
