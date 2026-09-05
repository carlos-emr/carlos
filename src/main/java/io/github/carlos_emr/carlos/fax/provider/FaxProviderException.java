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
package io.github.carlos_emr.carlos.fax.provider;

/**
 * Domain exception for provider transport failures.
 *
 * <p>This exception keeps provider-specific failures from leaking low-level transport details
 * directly into the fax orchestration flow.</p>
 *
 * @since 2026-02-11
 */
public class FaxProviderException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean transientError;
    /** HTTP status of the provider response that caused this failure, or 0 when not HTTP-related. */
    private final int httpStatus;

    /**
     * Creates a provider exception with message only (non-transient by default).
     */
    public FaxProviderException(String message) {
        super(message);
        this.transientError = false;
        this.httpStatus = 0;
    }

    /**
     * Creates a non-transient provider exception for an HTTP response with a non-success status,
     * carrying the status so callers can branch on it instead of parsing the message.
     *
     * @param message String the error message
     * @param httpStatus int the HTTP status code returned by the provider (for example 401 or 403)
     */
    public FaxProviderException(String message, int httpStatus) {
        super(message);
        this.transientError = false;
        this.httpStatus = httpStatus;
    }

    /**
     * Creates a provider exception with message and original cause (non-transient by default).
     */
    public FaxProviderException(String message, Throwable cause) {
        super(message, cause);
        this.transientError = false;
        this.httpStatus = 0;
    }

    /**
     * Creates a provider exception with message, cause, and transient flag.
     *
     * @param message String the error message
     * @param cause Throwable the underlying cause
     * @param transientError boolean true if this is a transient network error that may succeed on retry
     */
    public FaxProviderException(String message, Throwable cause, boolean transientError) {
        super(message, cause);
        this.transientError = transientError;
        this.httpStatus = 0;
    }

    /**
     * Creates a non-transient provider exception that re-describes an HTTP failure for the caller
     * while keeping both the original cause and its HTTP status, so a wrapper never erases the
     * status callers branch on.
     *
     * @param message String the error message
     * @param cause Throwable the underlying HTTP failure
     * @param httpStatus int the HTTP status code of that failure (for example 401 or 403)
     */
    public FaxProviderException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.transientError = false;
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the HTTP status of the provider response behind this failure.
     *
     * @return int the status code (for example 401, 403, 500), or 0 when the failure was not an
     *         HTTP non-success response (network error, malformed body, provider "Failed" status)
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns whether this error is transient (may succeed on retry).
     * Transient errors include network timeouts, connection refused, and temporary service unavailability.
     *
     * @return boolean true if the error is transient and the operation may be retried
     */
    public boolean isTransient() {
        return transientError;
    }

    /**
     * Checks whether any cause in the given throwable's chain is a transient network error.
     *
     * <p>Provider clients should call this when wrapping {@link java.io.IOException} to determine
     * whether the {@code transientError} flag should be set. This centralizes the network-error
     * classification logic so all providers use the same criteria.</p>
     *
     * @param cause Throwable the root cause to inspect
     * @return boolean true if any cause is a transient network exception
     */
    public static boolean isTransientNetworkCause(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.NoRouteToHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
