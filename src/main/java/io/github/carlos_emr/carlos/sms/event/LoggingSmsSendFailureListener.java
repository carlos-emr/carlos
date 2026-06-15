package io.github.carlos_emr.carlos.sms.event;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Default surface for terminal outbound-SMS failures: a structured warning logged after the failing
 * transaction commits. This is the seam other surfaces hang off of (e.g. a tickler to the requesting
 * provider, or a "Failed SMS" admin worklist) once the clinical send paths and consent model land;
 * keeping it here means the worker/recorder never need to change to add those.
 */
@Component
public class LoggingSmsSendFailureListener {
    private static final Logger LOGGER = MiscUtils.getLogger();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSmsSendFailed(SmsSendFailedEvent event) {
        LOGGER.warn(
                "Outbound SMS transaction {} failed terminally (errorCode={}); demographic={}, "
                        + "requestedByProvider={}, appointment={}. Needs staff follow-up.",
                event.transactionId(),
                event.errorCode(),
                event.demographicNo(),
                event.requestedByHealthcareProviderNo(),
                event.appointmentNo()
        );
    }
}
