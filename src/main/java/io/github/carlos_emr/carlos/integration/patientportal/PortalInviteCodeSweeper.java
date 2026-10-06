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
 * Removes invitation codes left behind in saved invitation emails.
 *
 * <p>The email carrying a code is saved before the portal activates the code, and the code is removed
 * from it once the send resolves. The code stays when CARLOS stops between those two steps (a crash, a
 * restart, a lost database connection), when the send fails after the email is saved but before the
 * attempt learns which email it saved (a permission refusal, or another failure in preparing the
 * message), or when the removal itself fails. Database backups then keep it too. CARLOS never sends a
 * saved invitation email again, so nothing needs the code once the sending request has finished.
 *
 * <p>Cleanup runs at startup and periodically, independently of invitation traffic. All callers use
 * the same idle cutoff, including startup: another server sharing the database may still be sending.
 * SUCCESS and BLOCKED emails qualify, and so do FAILED ones whose invitation attempt recorded a definite
 * "not sent" after its code went live (terminal SEND_FAILED, outcome SEND_REFUSED, written once the send
 * returned). That covers all definite not-sent outcomes after the code went live, as approved by Ben
 * (decision D22 A, 2026-10-05): the mail server refusing the message, a refused connection or failed mail
 * login, and, rarely, the commit gate failing after the attempt was recorded as committed. In every one
 * nothing was sent and the sender has finished. A RESOLVED email counts too when staff confirmed it never
 * arrived once the portal showed its code dead (attempt NOT_ARRIVED). Until then, other failed sends (staff
 * abandonment, which can be written while the original send is still running, a permission refusal, or an
 * error before the commit) and unfinished or manually resolved ones are left alone.
 *
 * <p>Whatever its state, an email is also cleared once its code is past its life plus
 * {@link #CODE_AGE_MARGIN} (#4083, option B, approved by Ben on 2026-10-06): when the attempt recorded the
 * portal's expiry, a day after that expiry; when it did not, as when the code never went live or no attempt
 * names the email, once the email row has been unchanged for the code's seven-day life plus a day. The send
 * asks the portal to activate a code seconds after it saves the email, and CARLOS never sends a saved
 * invitation email again, so with the day's margin such a code has expired on the portal: even an email
 * still being sent loses nothing it needs.
 * Bounded pages keep bodies out of application memory, and each write rechecks the rule in case the email
 * changed after selection.
 *
 * @since 2026-09-30
 */
public class PortalInviteCodeSweeper implements Runnable {

    static final int BATCH_SIZE = 200;
    public static final Duration INTERVAL = Duration.ofMinutes(15);
    /**
     * How long past the end of its code's life a saved invitation email is cleared whatever its state:
     * a margin over the portal's fixed seven-day life ({@link PortalInviteEmailComposer#CODE_LIFETIME}),
     * from #4083's "no saved invitation email older than seven days holds a code".
     */
    static final Duration CODE_AGE_MARGIN = Duration.ofDays(1);

    private static final Logger logger = MiscUtils.getLogger();

    private final EmailLogDao emailLogs;
    private final Clock clock;
    /** Resume the next bounded batch; reset after reaching the end so failures are retried. */
    private int afterId;

    public PortalInviteCodeSweeper(EmailLogDao emailLogs) {
        this(emailLogs, Clock.systemUTC());
    }

    PortalInviteCodeSweeper(EmailLogDao emailLogs, Clock clock) {
        this.emailLogs = Objects.requireNonNull(emailLogs, "emailLogs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A failed database call must not cancel future scheduled retries. */
    @Override
    public void run() {
        try {
            forgetLeftoverCodes(PortalInviteDeliveryService.RECOVERY_MIN_AGE);
        } catch (RuntimeException exception) {
            logger.warn("patient portal invitation code sweep failed: {}", exception.getClass().getSimpleName());
        }
    }

    /**
     * Clears one batch of settled invitation emails unchanged for at least {@code minIdle}.
     *
     * <p>Best effort: an email that cannot be rewritten is logged by its failure's class and skipped, and
     * a later sweep tries it again after reaching the end of the current pass.
     *
     * @param minIdle how long an email must be unchanged
     * @return how many saved emails were changed
     */
    public int forgetLeftoverCodes(Duration minIdle) {
        Instant now = clock.instant();
        Date cutoff = Date.from(now.minus(minIdle));
        // Aged out, whatever the state: a stored portal expiry more than the margin ago, or, with none
        // recorded, an email row unchanged for the code's whole life plus the margin.
        Date expiredBefore = Date.from(now.minus(CODE_AGE_MARGIN));
        Date agedBefore = Date.from(now.minus(PortalInviteEmailComposer.CODE_LIFETIME).minus(CODE_AGE_MARGIN));
        int cleared = 0;
        int failed = 0;
        List<Integer> emailLogIds = emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(
                TransactionType.PORTAL_INVITE, cutoff, expiredBefore, agedBefore,
                PortalInviteEmailComposer.CODE_FORGOTTEN, afterId, BATCH_SIZE);
        for (Integer emailLogId : emailLogIds) {
            try {
                cleared += emailLogs.replaceBodyIfUnchangedBefore(emailLogId, TransactionType.PORTAL_INVITE,
                        cutoff, expiredBefore, agedBefore, PortalInviteEmailComposer.CODE_FORGOTTEN);
            } catch (RuntimeException exception) {
                failed++;
                logger.warn("patient portal invitation code sweep: an email could not be cleared: {}",
                        exception.getClass().getSimpleName());
            }
            // Failed rows are retried next pass without blocking later batches.
            afterId = emailLogId;
        }
        if (emailLogIds.size() < BATCH_SIZE) {
            afterId = 0;
        }
        if (cleared > 0 || failed > 0) {
            logger.info("patient portal invitation code sweep: cleared {}, failed {}", cleared, failed);
        }
        return cleared;
    }
}
