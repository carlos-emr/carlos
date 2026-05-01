/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
