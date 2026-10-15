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

package io.github.carlos_emr.carlos.email.archive;

/**
 * Startup refused because the outbound email archive keyring cannot be used safely (#3448).
 *
 * <p>Thrown while the Spring context starts, which stops CARLOS from starting. The message is the
 * same sanitized, operator-facing text already logged once at ERROR: a file path, key ids, counts
 * and property names, never key material.</p>
 *
 * @since 2026-09-30
 */
public class OutboundEmailArchiveKeyringException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public OutboundEmailArchiveKeyringException(String message) {
        super(message);
    }
}
