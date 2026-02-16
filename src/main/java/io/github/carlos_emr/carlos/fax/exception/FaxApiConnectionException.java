/**
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * This software was written for
 * CloudPractice Inc.
 * Victoria, British Columbia
 * Canada
 *
 * Ported to CARLOS EMR from JunoEMR (2026).
 */
package io.github.carlos_emr.carlos.fax.exception;

/**
 * Thrown when a connection to an external fax API service cannot be established.
 * <p>
 * Typical causes include network timeouts, DNS failures, or the remote fax
 * service being unavailable. Callers should treat this as a transient error
 * and may retry the operation after a delay.
 *
 * @since 2026-02-09 (ported from JunoEMR CloudPractice fax module)
 */
public class FaxApiConnectionException extends FaxException {

    /**
     * @param message String the technical detail message for logging
     */
    public FaxApiConnectionException(String message) {
        super(message);
    }

    /**
     * @param e Exception the underlying cause (e.g. IOException)
     */
    public FaxApiConnectionException(Exception e) {
        super(e);
    }

    /**
     * @param e Exception the underlying cause
     * @param userMessageResourceKey String the resource bundle key for the user-facing message
     */
    public FaxApiConnectionException(Exception e, String userMessageResourceKey) {
        super(e, userMessageResourceKey);
    }
}
