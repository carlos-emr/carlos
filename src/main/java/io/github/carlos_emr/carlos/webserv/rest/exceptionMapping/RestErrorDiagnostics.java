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

package io.github.carlos_emr.carlos.webserv.rest.exceptionMapping;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/** Preserves diagnostic types and call sites without recording exception payloads or filenames. */
final class RestErrorDiagnostics {
    private RestErrorDiagnostics() { }

    static void log(Logger logger, Level level, Throwable original) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Exception root = null;
        Exception tail = null;
        Throwable current = original;
        while (current != null && visited.size() < 8 && visited.add(current)) {
            Exception safe = new Exception("REST failure type: " + current.getClass().getName());
            StackTraceElement[] frames = current.getStackTrace();
            StackTraceElement[] redacted = new StackTraceElement[Math.min(frames.length, 64)];
            for (int i = 0; i < redacted.length; i++) {
                StackTraceElement frame = frames[i];
                redacted[i] = new StackTraceElement(frame.getClassName(), frame.getMethodName(), null, frame.getLineNumber());
            }
            safe.setStackTrace(redacted);
            if (root == null) root = safe;
            else tail.initCause(safe);
            tail = safe;
            current = current.getCause();
        }
        logger.log(level, "REST request failed (diagnostic payloads redacted)", root);
    }
}
