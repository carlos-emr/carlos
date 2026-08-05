/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

/**
 * Indicates that an application-owned temporary file could not be safely promoted.
 */
public class FilePromotionException extends Exception {

    private static final long serialVersionUID = 1L;

    public FilePromotionException(String message) {
        super(message);
    }

    public FilePromotionException(String message, Throwable cause) {
        super(message, cause);
    }
}
