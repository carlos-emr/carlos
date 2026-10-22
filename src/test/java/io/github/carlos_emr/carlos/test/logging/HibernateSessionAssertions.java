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
package io.github.carlos_emr.carlos.test.logging;

import java.util.List;

import org.apache.logging.log4j.core.LogEvent;

/**
 * Finds Hibernate's HHH000099 session assertion in captured log events.
 *
 * <p>Hibernate logs HHH000099 at ERROR when a session is used after a failed flush or insert (#4436).
 * In Hibernate 7 it goes through the core message logger, {@code CoreMessageLogger.NAME}, not a logger
 * named after {@code AssertionFailure}, the class the console pattern prints. A capture on the wrong
 * name records nothing and lets an "assertion absent" check pass vacuously, so
 * {@code MessageUploaderCleanIntegrationTest} also asserts that a query in a failed session is recorded
 * here, and fails if Hibernate moves the message to another logger.</p>
 *
 * @since 2026-10-08
 */
public final class HibernateSessionAssertions {
    /** The logger Hibernate's core messages, HHH000099 among them, are logged under. */
    public static final String LOGGER = "org.hibernate.orm.core";

    private HibernateSessionAssertions() {
    }

    /**
     * Starts capturing the logger HHH000099 is logged under.
     *
     * @return a capture to pass to {@link #in(LogCapture)} and close
     */
    public static LogCapture capture() {
        return LogCapture.forLogger(LOGGER);
    }

    /**
     * Returns the HHH000099 events a capture recorded.
     *
     * @param hibernateCore a capture from {@link #capture()}
     * @return the events carrying HHH000099 or an {@code AssertionFailure}; empty if there were none
     */
    public static List<LogEvent> in(LogCapture hibernateCore) {
        return hibernateCore.events().stream()
                .filter(event -> event.getMessage().getFormattedMessage().contains("HHH000099")
                        || event.getThrown() instanceof org.hibernate.AssertionFailure)
                .toList();
    }
}
