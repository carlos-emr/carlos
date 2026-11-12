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
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.Logger;

/**
 * Clears the saved body of a myCarlos device code email ({@link TransactionType#MYCARLOS_CODE}) that
 * still holds its code.
 *
 * <p>{@code EmailManager} replaces the body as soon as a send ends. If that one write fails (a lost
 * database connection, say), the body keeps the code until this sweep clears it. It uses the same
 * conditional queries as {@link PortalInviteCodeSweeper}, with the device code's much shorter life:
 * <ul>
 *   <li>a SUCCESS or BLOCKED email unchanged for {@link #IDLE} is cleared;</li>
 *   <li>any other email is cleared once its row has been unchanged for the request's life plus
 *   {@link #CODE_AGE_MARGIN}. No portal invitation attempt ever names a code email, so the row's own
 *   timestamp decides. By then the code's request has ended, and CARLOS never sends a saved code email
 *   again.</li>
 * </ul>
 * Each write rechecks the rule, so an email that changed after selection is left alone. A failed row
 * is logged by its failure's class and tried again on a later pass.
 *
 * @since 2026-11-12
 */
public class PortalMyCarlosCodeEmailSweeper implements Runnable {

    static final int BATCH_SIZE = 200;
    public static final Duration INTERVAL = Duration.ofMinutes(5);
    /** How long a settled email must be unchanged: another server may still be finishing its send. */
    static final Duration IDLE = Duration.ofMinutes(5);
    /** A device code request lives 10 minutes ({@code PortalMyCarlosStaffApprovalService}). */
    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    /** How long past that life an email is cleared whatever its state. */
    static final Duration CODE_AGE_MARGIN = Duration.ofMinutes(20);

    private static final Logger logger = MiscUtils.getLogger();

    private final EmailLogDao emailLogs;
    private final Clock clock;
    /** Resume the next bounded batch; reset after reaching the end so failures are retried. */
    private int afterId;

    public PortalMyCarlosCodeEmailSweeper(EmailLogDao emailLogs) {
        this(emailLogs, Clock.systemUTC());
    }

    PortalMyCarlosCodeEmailSweeper(EmailLogDao emailLogs, Clock clock) {
        this.emailLogs = Objects.requireNonNull(emailLogs, "emailLogs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A failed database call must not cancel future scheduled runs. */
    @Override
    public void run() {
        try {
            forgetLeftoverCodes();
        } catch (RuntimeException exception) {
            logger.warn("myCarlos device code email sweep failed: {}", exception.getClass().getSimpleName());
        }
    }

    /**
     * Clears one batch of device code emails that still hold their code.
     *
     * @return how many saved emails were changed
     */
    public int forgetLeftoverCodes() {
        Instant now = clock.instant();
        Date settledBefore = Date.from(now.minus(IDLE));
        // No invitation attempt names a code email, so the stored-expiry branch never applies; the
        // row's own timestamp decides through agedBefore.
        Date expiredBefore = Date.from(now);
        Date agedBefore = Date.from(now.minus(CODE_LIFETIME).minus(CODE_AGE_MARGIN));
        int cleared = 0;
        int failed = 0;
        List<Integer> emailLogIds = emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(
                TransactionType.MYCARLOS_CODE, settledBefore, expiredBefore, agedBefore,
                EmailLog.MYCARLOS_CODE_BODY_FORGOTTEN, afterId, BATCH_SIZE);
        for (Integer emailLogId : emailLogIds) {
            try {
                cleared += emailLogs.replaceBodyIfUnchangedBefore(emailLogId, TransactionType.MYCARLOS_CODE,
                        settledBefore, expiredBefore, agedBefore, EmailLog.MYCARLOS_CODE_BODY_FORGOTTEN);
            } catch (RuntimeException exception) {
                failed++;
                logger.warn("myCarlos device code email sweep: an email could not be cleared: {}",
                        exception.getClass().getSimpleName());
            }
            // Failed rows are retried on a later pass without blocking later batches.
            afterId = emailLogId;
        }
        if (emailLogIds.size() < BATCH_SIZE) {
            afterId = 0;
        }
        if (cleared > 0 || failed > 0) {
            logger.info("myCarlos device code email sweep: cleared {}, failed {}", cleared, failed);
        }
        return cleared;
    }
}
