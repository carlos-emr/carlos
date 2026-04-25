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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

/**
 * Thrown when a validated billing-form input fails its precondition for an
 * audit-trail-sensitive write (e.g. {@code addToPatientDx} requested with a
 * non-numeric {@code demographic_no}). Distinguishable from
 * {@link IllegalArgumentException} so Struts {@code <exception-mapping>} can
 * route the failure to a specific user-facing message rather than falling
 * back to the generic CARLOS error page.
 *
 * <p>Use this for client-supplied data that the gate could not have caught
 * (form tampering, route mismatch, dev regression). For programmer-error
 * contract violations (caller passed a bad arg to an internal API), use
 * {@link IllegalArgumentException}.</p>
 *
 * @since 2026-04-25
 */
public class BillingValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BillingValidationException(String message) {
        super(message);
    }

    public BillingValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
