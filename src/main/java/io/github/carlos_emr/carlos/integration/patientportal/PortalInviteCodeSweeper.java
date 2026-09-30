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
 * restart, a lost database connection), when the send fails before the attempt learns which email it
 * saved (an archive or permission refusal), or when the removal itself fails. Database backups then keep
 * it too. CARLOS never sends a saved invitation email again, so nothing needs the code once the sending
 * request has finished.
 *
 * <p>Every portal invitation email that has not changed for
 * {@link PortalInviteDeliveryService#RECOVERY_MIN_AGE} is treated as finished with its request, as staff
 * recovery does. Only emails that changed within {@link #WINDOW} are checked: an older code has expired
 * on the portal, and bounding the window keeps each sweep small. A body already cleared is left alone.
 *
 * <p>Runs once when CARLOS starts, because a crash always ends in a restart, and before each new
 * invitation, which catches a removal that failed while CARLOS kept running. It changes no delivery
 * attempt and calls nothing on the portal.
 *
 * @since 2026-09-30
 */
public class PortalInviteCodeSweeper {

    /** Emails older than this hold codes the portal has already expired. */
    static final Duration WINDOW = PortalInviteEmailComposer.CODE_LIFETIME.plusDays(1);

    private static final Logger logger = MiscUtils.getLogger();

    private final EmailLogDao emailLogs;
    private final Clock clock;

    public PortalInviteCodeSweeper(EmailLogDao emailLogs) {
        this(emailLogs, Clock.systemUTC());
    }

    PortalInviteCodeSweeper(EmailLogDao emailLogs, Clock clock) {
        this.emailLogs = Objects.requireNonNull(emailLogs, "emailLogs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Clears the code from every recent portal invitation email that has gone idle.
     *
     * <p>Best effort: an email that cannot be rewritten is logged by its failure's class and skipped, and
     * the next sweep tries it again.
     *
     * @return how many saved emails were changed
     */
    public int forgetLeftoverCodes() {
        Instant now = clock.instant();
        List<Integer> emailLogIds = emailLogs.findIdsByTransactionTypeChangedBetween(TransactionType.PORTAL_INVITE,
                Date.from(now.minus(WINDOW)), Date.from(now.minus(PortalInviteDeliveryService.RECOVERY_MIN_AGE)));
        int cleared = 0;
        int failed = 0;
        for (Integer emailLogId : emailLogIds) {
            try {
                cleared += emailLogs.replaceBody(emailLogId, PortalInviteEmailComposer.CODE_FORGOTTEN);
            } catch (RuntimeException exception) {
                failed++;
                logger.warn("patient portal invitation code could not be cleared from the outbox: {}",
                        exception.getClass().getSimpleName());
            }
        }
        if (cleared > 0 || failed > 0) {
            logger.info("patient portal invitation code sweep: cleared {}, failed {}", cleared, failed);
        }
        return cleared;
    }
}
