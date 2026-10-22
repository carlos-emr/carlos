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
package io.github.carlos_emr.carlos.report.data;

import java.sql.SQLException;

/**
 * Query by Example cannot run because its read-only reporting database account is missing,
 * misconfigured, refused by MariaDB, or holds more than {@code SELECT} on reportable tables.
 *
 * <p>Raised before any submitted SQL reaches the database. Query by Example never falls back
 * to the application account, so this is the fail-closed outcome while the account is not
 * provisioned (see {@code docs/query-by-example-least-privilege.md}). The message is meant for
 * the server log; it never carries credentials or grant text.</p>
 *
 * @since 2026-10-22
 */
public class ReportingAccountUnavailableException extends SQLException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with an operator-facing explanation.
     *
     * @param message why the reporting account cannot be used
     */
    public ReportingAccountUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates the exception while keeping the database or driver failure behind it.
     *
     * @param message why the reporting account cannot be used
     * @param cause the login, driver, or grant-audit failure
     */
    public ReportingAccountUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
