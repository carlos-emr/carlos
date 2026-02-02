//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.app;

import org.owasp.csrfguard.log.JavaLogger;
import org.owasp.csrfguard.log.LogLevel;
import io.github.carlos_emr.OscarProperties;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Oscar CsrfGuardLogger
 * Extends the standard CsrfGuard JavaLogger to take into account the oscar property "csrf_log_all_messages"
 * If the property is enabled, only
 */
public class CsrfGuardLogger extends JavaLogger {

    private static final OscarProperties oscarProperties = OscarProperties.getInstance();
    private static final List<LogLevel> errorLogLevels = Arrays.asList(LogLevel.Warning, LogLevel.Error, LogLevel.Fatal);

    // Create logger that adds to a log file in the oscar document folder just for csrf log lines
    private static Logger LOGGER = Logger.getLogger("Owasp.CsrfGuard");

    static {
        try {
            String documentsFolder = oscarProperties.getProperty("BASE_DOCUMENT_DIR") != null ? oscarProperties.getProperty("BASE_DOCUMENT_DIR") : System.getProperty("java.io.tmpdir");
            if (!documentsFolder.endsWith("/")) {
                documentsFolder += "/";
            }
            String logDirectory = documentsFolder + "logs";
            File logFile = new File(logDirectory + "/csrf.log");
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            FileHandler logFileHandler = new FileHandler(logFile.getPath(), true);
            logFileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(logFileHandler);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "CSRFGuard Logger exception", e);
        }
    }

    @Override
    public void log(String msg) {
        if (isLoggable()) {
            super.log(msg);
        }
    }

    @Override
    public void log(LogLevel level, String msg) {
        if (isLoggable(level)) {
            super.log(level, msg);
        }
    }

    @Override
    public void log(Exception exception) {
        if (isLoggable()) {
            super.log(exception);
        }
    }

    @Override
    public void log(LogLevel level, Exception exception) {
        if (isLoggable(level)) {
            super.log(exception);
        }
    }

    private boolean isLoggable() {
        return oscarProperties.isPropertyActive("csrf_log_all_messages");
    }

    private boolean isLoggable(LogLevel level) {
        return oscarProperties.isPropertyActive("csrf_log_all_messages") || errorLogLevels.contains(level);
    }
}
