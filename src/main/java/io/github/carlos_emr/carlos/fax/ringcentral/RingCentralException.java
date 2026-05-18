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
package io.github.carlos_emr.carlos.fax.ringcentral;

import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;

/**
 * Provider-specific exception for RingCentral fax API failures.
 *
 * <p>When the failure is produced from a non-2xx HTTP response, the status code is carried on the
 * exception so the orchestration layer can drive 401-specific token eviction and so 5xx/429 are
 * classified transient alongside the IOException paths.</p>
 *
 * <p>Prefer the static factory {@link #fromHttpStatus(String, int)} when constructing an
 * exception in response to a non-2xx HTTP status — it centralizes the transient classification
 * (5xx and 429 are transient) so the rule lives in exactly one place.</p>
 *
 * @since 2026-05-05
 */
public class RingCentralException extends FaxProviderException {

    private static final long serialVersionUID = 1L;

    private final Integer httpStatus;

    public RingCentralException(String message) {
        super(message);
        this.httpStatus = null;
    }

    public RingCentralException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
    }

    public RingCentralException(String message, Throwable cause, boolean transientError) {
        super(message, cause, transientError);
        this.httpStatus = null;
    }

    public RingCentralException(String message, int httpStatus, boolean transientError) {
        super(message, null, transientError);
        this.httpStatus = httpStatus;
    }

    /**
     * Creates a RingCentralException from a non-2xx HTTP response. Centralizes the
     * status-to-transient classification: 5xx server errors and 429 rate-limited responses are
     * marked transient (eligible for retry); all other 4xx responses are permanent client errors.
     *
     * @param message operator-facing message including any vendor body excerpt
     * @param httpStatus HTTP status from the response (must be in 100..599)
     * @return classified exception ready to throw
     */
    public static RingCentralException fromHttpStatus(String message, int httpStatus) {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status out of range: " + httpStatus);
        }
        boolean transientError = httpStatus >= 500 || httpStatus == 429;
        return new RingCentralException(message, httpStatus, transientError);
    }

    /**
     * @return RingCentral HTTP status that triggered this failure, or {@code null} when the
     *         failure originated outside a response (IOException, JSON parse error, validation).
     */
    public Integer getHttpStatus() {
        return httpStatus;
    }
}
