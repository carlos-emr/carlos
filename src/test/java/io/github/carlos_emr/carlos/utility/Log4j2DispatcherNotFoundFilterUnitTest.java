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
package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards issue #4570: the shipped {@code log4j2.xml} must drop Struts' "Could not find action or
 * result: URI?query" WARN (the query string can carry PHI) while leaving other Dispatcher output.
 *
 * <p>Loads the real {@code src/main/resources/log4j2.xml} into an isolated configuration, swaps
 * its console appender for a capturing one, and evaluates events against the Dispatcher
 * {@link LoggerConfig}, so the filter is exercised exactly as shipped.</p>
 */
@Tag("unit")
@Tag("fast")
@DisplayName("log4j2.xml Dispatcher not-found filter")
class Log4j2DispatcherNotFoundFilterUnitTest {

    private static final String DISPATCHER = "org.apache.struts2.dispatcher.Dispatcher";

    private static final class Capture extends AbstractAppender {
        final List<String> messages = new ArrayList<>();

        Capture() {
            super("capture", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }

    private List<String> logThroughShippedConfig(Level level, String message) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("log4j2.xml")) {
            // The test classpath's log4j2.xml shadows main's, so read main's explicitly.
            assertThat(in).isNotNull();
        }
        java.io.File shipped = new java.io.File("src/main/resources/log4j2.xml");
        assertThat(shipped).exists();
        Capture capture = new Capture();
        try (InputStream in = new java.io.FileInputStream(shipped);
             LoggerContext ctx = new LoggerContext("dispatcher-filter-test")) {
            Configuration cfg = new XmlConfiguration(ctx, new ConfigurationSource(in));
            cfg.start();
            capture.start();
            LoggerConfig lc = cfg.getLoggerConfig(DISPATCHER);
            assertThat(lc.getName()).isEqualTo(DISPATCHER);
            // The Dispatcher logger inherits Root's appender; replace it with the capture.
            lc.getAppenders().keySet().forEach(lc::removeAppender);
            cfg.getRootLogger().getAppenders().keySet().forEach(cfg.getRootLogger()::removeAppender);
            cfg.getRootLogger().addAppender(capture, null, null);
            cfg.getRootLogger().setLevel(Level.ALL);
            lc.setLevel(Level.ALL);
            lc.log(new org.apache.logging.log4j.core.impl.Log4jLogEvent.Builder()
                    .setLoggerName(DISPATCHER).setLevel(level)
                    .setMessage(new SimpleMessage(message)).build());
            cfg.stop();
        }
        return capture.messages;
    }

    @Test
    @DisplayName("should drop not-found WARN carrying a query string")
    void shouldDropNotFoundWarn_whenQueryStringPresent() throws Exception {
        assertThat(logThroughShippedConfig(Level.WARN,
                "Could not find action or result: /carlos/NoSuchAction?demographic_no=1&hin=9999999999"))
                .isEmpty();
    }

    @Test
    @DisplayName("should drop not-found message even with an embedded line break")
    void shouldDropNotFoundWarn_whenMessageContainsLineBreak() throws Exception {
        assertThat(logThroughShippedConfig(Level.WARN,
                "Could not find action or result: /carlos/x?a=1\nhin=9999999999")).isEmpty();
    }

    @Test
    @DisplayName("should keep other Dispatcher errors")
    void shouldKeepDispatcherOutput_forOtherMessages() throws Exception {
        assertThat(logThroughShippedConfig(Level.ERROR, "Exception occurred during processing request: boom"))
                .containsExactly("Exception occurred during processing request: boom");
    }
}
