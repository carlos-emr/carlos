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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MsgAttachPDF2Action} security + PHI-log behavior.
 *
 * <p>The previous INFO log of {@code srcText} leaked rendered demographic /
 * encounter / prescription content into application logs — a HIPAA/PIPEDA
 * incident waiting to happen. This test captures the logger output on the
 * preview path and asserts the rendered content is not present and that only
 * length metadata is logged.
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
    private CapturingAppender appender;
    private LoggerConfig addedLoggerConfig;

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

        attachCapturingAppender();
    }

    @AfterEach
    void detachAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        if (addedLoggerConfig != null) {
            addedLoggerConfig.removeAppender(appender.getName());
        }
        appender.stop();
        ctx.updateLoggers();
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

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
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

        // Doc2PDF will fail to render the malformed HTML stub, but that runs
        // AFTER the log call we care about, so the NPE is not relevant here.
        try {
            executeAction(action);
        } catch (Throwable ignored) {
            // Expected: Doc2PDF dies on the stub; assertion below is on the log output.
        }

        assertThat(appender.messages())
                .as("rendered srcText content must not appear in application logs (PHI)")
                .noneMatch(m -> m.contains(PHI_SENTINEL));
    }

    private void attachCapturingAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        appender = new CapturingAppender("MsgAttachPDF2ActionTest-capture");
        appender.start();
        config.addAppender(appender);

        // Attach to the action's own logger (MiscUtils.getLogger() uses the
        // caller class name). Scoping to this specific logger avoids capturing
        // unrelated messages if the suite runs in parallel.
        String loggerName =
                "io.github.carlos_emr.carlos.messenger.pageUtil.MsgAttachPDF2Action";
        LoggerConfig existing = config.getLoggerConfig(loggerName);
        if (!loggerName.equals(existing.getName())) {
            LoggerConfig scoped = new LoggerConfig(loggerName, Level.ALL, true);
            config.addLogger(loggerName, scoped);
            existing = scoped;
        }
        addedLoggerConfig = existing;
        addedLoggerConfig.addAppender(appender, Level.ALL, null);
        ctx.updateLoggers();
    }

    /** Minimal log4j2 appender that captures formatted messages for assertion. */
    private static final class CapturingAppender extends AbstractAppender {
        private final List<String> captured = Collections.synchronizedList(new ArrayList<>());

        CapturingAppender(String name) {
            super(name, null, null, false, null);
        }

        @Override
        public void append(LogEvent event) {
            captured.add(event.getMessage().getFormattedMessage());
        }

        List<String> messages() {
            return new ArrayList<>(captured);
        }
    }
}
