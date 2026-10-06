/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.test.logging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Scoped Log4j2 capture helper for unit tests that assert application logging.
 *
 * <p>Tests should attach capture appenders to the exact logger under test instead of mutating the
 * root logger. Surefire runs classes in parallel, so root-level add/remove operations make unrelated
 * log assertions race each other. This helper creates a dedicated logger config only when the target
 * logger does not already have one, removes only the appender/config it owns, and always stores
 * immutable events so assertions are independent of Log4j's event reuse.</p>
 */
public final class LogCapture implements AutoCloseable {

    private static final Map<String, Integer> ownedLoggerConfigCounts = new HashMap<>();

    private final LoggerContext context;
    private final String loggerName;
    private final LoggerConfig loggerConfig;
    private final CapturingAppender appender;
    private final boolean ownsHelperLoggerConfig;
    private boolean closed;

    private LogCapture(String loggerName) {
        this.loggerName = Objects.requireNonNull(loggerName, "loggerName");
        context = (LoggerContext) LogManager.getContext(false);
        appender = new CapturingAppender("LogCapture-" + UUID.randomUUID());

        synchronized (LogCapture.class) {
            Configuration configuration = context.getConfiguration();
            LoggerConfig candidate = configuration.getLoggerConfig(loggerName);
            boolean ownsConfig = false;
            if (!loggerName.equals(candidate.getName())) {
                candidate = new LoggerConfig(loggerName, Level.ALL, false);
                configuration.addLogger(loggerName, candidate);
                ownedLoggerConfigCounts.put(loggerName, 1);
                ownsConfig = true;
            } else if (ownedLoggerConfigCounts.containsKey(loggerName)) {
                ownedLoggerConfigCounts.merge(loggerName, 1, Integer::sum);
                ownsConfig = true;
            }

            loggerConfig = candidate;
            ownsHelperLoggerConfig = ownsConfig;
            appender.start();
            loggerConfig.addAppender(appender, Level.ALL, null);
            context.updateLoggers();
        }
    }

    /**
     * Captures events emitted by the logger named after {@code loggerClass}.
     *
     * @param loggerClass class whose fully qualified name is the Log4j2 logger name
     * @return closeable capture handle
     */
    public static LogCapture forLogger(Class<?> loggerClass) {
        Objects.requireNonNull(loggerClass, "loggerClass");
        return forLogger(loggerClass.getName());
    }

    /**
     * Captures events emitted by the exact Log4j2 logger name.
     *
     * <p>Use this overload for classes whose logger is private or when production code intentionally
     * logs through a non-class category.</p>
     *
     * @param loggerName exact Log4j2 logger name to capture
     * @return closeable capture handle
     */
    public static LogCapture forLogger(String loggerName) {
        return new LogCapture(loggerName);
    }

    /**
     * Returns immutable Log4j2 events in append order.
     *
     * @return snapshot of captured events
     */
    public List<LogEvent> events() {
        return List.copyOf(appender.events());
    }

    /**
     * Returns formatted log messages in append order.
     *
     * @return snapshot of captured formatted messages
     */
    public List<String> messages() {
        return events().stream()
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();
    }

    /**
     * Detaches the capture appender and removes only the logger config created by this helper.
     */
    @Override
    public void close() {
        synchronized (LogCapture.class) {
            if (closed) {
                return;
            }
            loggerConfig.removeAppender(appender.getName());
            if (ownsHelperLoggerConfig) {
                Integer count = ownedLoggerConfigCounts.get(loggerName);
                if (count == null || count <= 1) {
                    ownedLoggerConfigCounts.remove(loggerName);
                    context.getConfiguration().removeLogger(loggerName);
                } else {
                    ownedLoggerConfigCounts.put(loggerName, count - 1);
                }
            }
            appender.stop();
            context.updateLoggers();
            closed = true;
        }
    }

    private static final class CapturingAppender extends AbstractAppender {
        private final CopyOnWriteArrayList<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender(String name) {
            super(name, null, null, false, null);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        List<LogEvent> events() {
            return events;
        }
    }
}
