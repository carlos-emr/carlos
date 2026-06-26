/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.test.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Scoped Log4j2 level override for unit tests that need to cover disabled log branches.
 */
public final class LoggerLevelOverride implements AutoCloseable {

    private final LoggerContext context;
    private final LoggerConfig loggerConfig;
    private final Level previousLevel;

    private LoggerLevelOverride(Class<?> loggerClass, Level level) {
        context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        loggerConfig = configuration.getLoggerConfig(loggerClass.getName());
        previousLevel = loggerConfig.getLevel();
        loggerConfig.setLevel(level);
        context.updateLoggers();
    }

    public static LoggerLevelOverride disableDebug(Class<?> loggerClass) {
        return new LoggerLevelOverride(loggerClass, Level.INFO);
    }

    @Override
    public void close() {
        loggerConfig.setLevel(previousLevel);
        context.updateLoggers();
    }
}
