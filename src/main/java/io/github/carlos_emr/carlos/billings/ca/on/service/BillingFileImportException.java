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
package io.github.carlos_emr.carlos.billings.ca.on.service;

/**
 * Thrown when a fixed-width MOH report file cannot be fully parsed/imported.
 * Wraps the underlying {@link java.io.IOException} or
 * {@link StringIndexOutOfBoundsException} so the surrounding
 * {@code @Transactional} boundary rolls back any rows persisted earlier in
 * the file rather than leaving partial commits behind.
 *
 * @since 2026-04-30
 */
public class BillingFileImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BillingFileImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
