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
package io.github.carlos_emr.carlos.consultation;

import java.util.Objects;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.utility.LogSafe;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

/**
 * Resolves the authoritative patient demographic for a consultation request.
 *
 * <p>Consult print and attachment flows must use the demographic stored on the
 * {@link ConsultationRequest}, not a submitted request parameter or request attribute. This helper
 * centralizes that lookup and the shared mismatch warning so all callers handle spoofed or stale
 * submitted demographic values consistently.</p>
 */
public final class ConsultationDemographicResolver {

    private ConsultationDemographicResolver() {
        // utility class
    }

    /**
     * Resolves the persisted consultation demographic and records safe diagnostic warnings.
     *
     * @param consultationRequestDao DAO used to load the consultation request
     * @param requestId consultation request identifier; may be a String or another scalar request attribute type
     * @param submittedDemographicId demographic submitted by the caller; ignored when it differs from the persisted value
     * @param context short log context such as {@code preview}, {@code PDF}, or {@code print}
     * @param logger logger for safe operational diagnostics
     * @return Resolution containing the persisted demographic id, or a failure reason when it cannot be resolved
     */
    public static Resolution resolve(ConsultationRequestDao consultationRequestDao, Object requestId,
            Object submittedDemographicId, String context, Logger logger) {
        String normalizedRequestId = Objects.toString(requestId, null);
        if (StringUtils.isBlank(normalizedRequestId)) {
            return Resolution.unresolved(FailureReason.MISSING_REQUEST_ID, null);
        }

        int parsedRequestId;
        try {
            parsedRequestId = Integer.parseInt(normalizedRequestId);
        } catch (NumberFormatException e) {
            warn(logger, "Invalid consultation {} request id while resolving demographic requestId={}",
                    context, normalizedRequestId, null, null);
            return Resolution.unresolved(FailureReason.INVALID_REQUEST_ID, e);
        }

        ConsultationRequest consultationRequest = consultationRequestDao.find(parsedRequestId);
        if (consultationRequest == null || consultationRequest.getDemographicId() == null) {
            warn(logger, "Unable to resolve consultation {} demographic for requestId={}",
                    context, String.valueOf(parsedRequestId), null, null);
            return Resolution.unresolved(FailureReason.MISSING_CONSULTATION_REQUEST, null);
        }

        String consultationDemographicId = String.valueOf(consultationRequest.getDemographicId());
        String submittedDemographic = Objects.toString(submittedDemographicId, null);
        if (StringUtils.isNotBlank(submittedDemographic) && !consultationDemographicId.equals(submittedDemographic)) {
            warn(logger, "Ignoring mismatched consultation {} demographic requestId={} submittedDemographic={} consultationDemographic={}",
                    context, String.valueOf(parsedRequestId), submittedDemographic, consultationDemographicId);
        }

        return Resolution.resolved(consultationDemographicId);
    }

    private static void warn(Logger logger, String message, String context, String requestId,
            String submittedDemographic, String consultationDemographic) {
        if (logger == null || !logger.isWarnEnabled()) {
            return;
        }
        if (submittedDemographic == null && consultationDemographic == null) {
            logger.warn(message, LogSafe.sanitize(context), LogSafe.sanitize(requestId));
            return;
        }
        logger.warn(message, LogSafe.sanitize(context), LogSafe.sanitize(requestId),
                LogSafe.sanitize(submittedDemographic), LogSafe.sanitize(consultationDemographic));
    }

    /**
     * Why the persisted consultation demographic could not be resolved.
     */
    public enum FailureReason {
        MISSING_REQUEST_ID,
        INVALID_REQUEST_ID,
        MISSING_CONSULTATION_REQUEST
    }

    /**
     * Result of resolving the persisted consultation demographic.
     *
     * @param demographicId persisted consultation demographic id when resolved
     * @param failureReason reason resolution failed, or {@code null} when resolved
     * @param cause parsing exception for invalid request ids, or {@code null}
     */
    public record Resolution(String demographicId, FailureReason failureReason, Exception cause) {
        static Resolution resolved(String demographicId) {
            return new Resolution(demographicId, null, null);
        }

        static Resolution unresolved(FailureReason failureReason, Exception cause) {
            return new Resolution(null, failureReason, cause);
        }

        /**
         * Returns whether the persisted demographic was resolved.
         *
         * @return true when {@link #demographicId()} is available
         */
        public boolean isResolved() {
            return demographicId != null;
        }
    }
}
