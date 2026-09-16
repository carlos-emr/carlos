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

/**
 * Signals that durable capture of an outbound email artifact failed.
 *
 * <p>Exists so callers can tell an archive-storage failure from a transport
 * failure by <em>type</em>. The previous approach compared the exception message
 * against a constant, which was wrong in both directions: an SMTP server whose
 * error text happened to contain that constant was misreported as an archive
 * failure, and the text itself is provider-controlled, so classification depended
 * on a remote party's wording.</p>
 *
 * <p>Scope is deliberately narrow. This marks failure to <em>store</em> the
 * artifact, not failure to build it: SMTP configuration validation happens while
 * preparing the message, and a bad host or password is a send-configuration
 * problem that should not be reported as an archive fault.</p>
 *
 * @since 2026-08-17
 */
public class OutboundEmailArchiveException extends EmailSendingException {

    public OutboundEmailArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
