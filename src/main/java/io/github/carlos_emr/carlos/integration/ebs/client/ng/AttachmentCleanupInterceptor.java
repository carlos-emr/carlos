package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.util.Collection;
import java.util.List;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptor;

/**
 * Clears the attachment ThreadLocal and closes the application's response replay.
 * 
 * <h3>Purpose</h3>
 * 
 * This interceptor operates in the {@link Phase#POST_INVOKE} phase and is responsible for 
 * clearing attachments cached in the thread-local storage by the {@link AttachmentCachingInterceptor}. 
 * By doing so, it prevents potential memory leaks and ensures that the cached attachments 
 * are removed after message processing.
 *
 * <h3>Usage</h3>
 * 
 * This interceptor should be added to the interceptor chain following the {@link AttachmentCachingInterceptor} 
 * to ensure that attachments are cleaned up after they are no longer needed.
 */
public class AttachmentCleanupInterceptor extends
		AbstractPhaseInterceptor<Message> {

	/**
	 * Constructs an instance of the AttachmentCleanupInterceptor, 
	 * initializing it to the {@link Phase#POST_INVOKE} phase.
	 */
	public AttachmentCleanupInterceptor() {
		super(Phase.POST_INVOKE);
	}

    /** Arms cleanup before processing, including when the inbound-fault chain itself fails. */
    @Override
    public Collection<PhaseInterceptor<? extends Message>> getAdditionalInterceptors() {
        return List.of(new FaultCleanupGuard());
    }

    private static final class FaultCleanupGuard extends AbstractPhaseInterceptor<Message> {
        private FaultCleanupGuard() {
            super(Phase.RECEIVE);
        }

        @Override
        public void handleMessage(Message message) {
            // Keep the replay open until normal completion or a later processing failure.
        }

        @Override
        public void handleFault(Message message) {
            cleanup(message);
        }
    }

	/**
	 * Handles the incoming message by clearing cached attachments.
	 * 
	 * <p/>
	 * 
	 * This method is invoked during the message processing phase and 
	 * calls the {@link AttachmentCachingInterceptor#clear()} method 
	 * to remove cached attachments from the thread-local storage.
	 *
	 * @param message the message for which cached attachments should be cleared
	 * @throws Fault if there is a problem handling the message
	 */
	@Override
	public void handleMessage(Message message) throws Fault {
		cleanup(message);
	}

    static void cleanup(Message message) {
        try {
            DynamicWSS4JInInterceptor.closeReplay(message);
        } finally {
            AttachmentCachingInterceptor.clear();
        }
    }
	
}
