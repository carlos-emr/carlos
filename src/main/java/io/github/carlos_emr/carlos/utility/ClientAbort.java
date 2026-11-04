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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Recognises a client abort: the browser closed the page, cancelled a download or dropped the connection
 * while CARLOS was still writing the response (#4438).
 *
 * <p>Tomcat reports it as {@code org.apache.catalina.connector.ClientAbortException}, an {@code IOException}
 * that filters often wrap in a {@code ServletException}. Nothing on the server failed and nothing needs
 * action, so filters log it at DEBUG instead of ERROR. The class is matched by name, through the cause chain
 * and each cause's superclasses, so this needs no compile-time dependency on Tomcat internals.
 */
public final class ClientAbort {

    static final String CLIENT_ABORT_EXCEPTION = "org.apache.catalina.connector.ClientAbortException";

    /** Bounds a pathological cause chain; real wrapping is two or three levels deep. */
    private static final int MAX_DEPTH = 16;

    private ClientAbort() {
        // static-utility holder; not instantiable
    }

    /**
     * Whether {@code failure}, or anything in its cause chain, is Tomcat's client-abort exception (or a
     * subclass of it).
     *
     * @param failure the exception that escaped a filter chain; may be {@code null}
     * @return {@code true} if the failure is a client abort
     */
    public static boolean isClientAbort(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable t = failure;
        for (int depth = 0; t != null && depth < MAX_DEPTH && seen.add(t); depth++) {
            for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                if (CLIENT_ABORT_EXCEPTION.equals(c.getName())) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}
