package ca.openosp.openo.integration.ebs.client.ng;

import java.util.Collection;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * Caches attachments for the current message. Please remember to add {@link AttachmentCleanupInterceptor}
 * to the interceptor chain in order to remove cached attachments after processing.
 *
 * <h3>Implementation notes</h3>
 * 
 * This interceptor is invoked at PRE_PROTOCOL phase and expects attachments
 * to be already parsed and configured on the {@link Message} instance before it is invoked.
 * It then sets the attachments in the thread local scope for further processing.
 * 
 * <h3>ThreadLocal usage</h3>
 * 
 * The interceptor uses {@link ThreadLocal} to store the attachments in a thread-local context.
 * This ensures that the attachments are available to the current thread during message processing 
 * and can be retrieved via {@link #getAttachments()}. After processing, {@link #clear()} should 
 * be called to avoid memory leaks by removing the cached attachments from the thread-local storage.
 */
public class AttachmentCachingInterceptor extends AbstractPhaseInterceptor<Message> {

	private static ThreadLocal<Collection<Attachment>> localInstance = new ThreadLocal<Collection<Attachment>>();

	/**
	 * Constructs an instance of the AttachmentCachingInterceptor, 
	 * initializing it to the {@link Phase#PRE_PROTOCOL} phase.
	 */
	public AttachmentCachingInterceptor() {
		super(Phase.PRE_PROTOCOL);
	}

	/**
	 * Handles the incoming message by caching its attachments.
	 * 
	 * <p/>
	 * 
	 * This method is invoked during the message processing phase and 
	 * retrieves the attachments from the given message, storing them in 
	 * the thread-local context for later access.
	 *
	 * @param message the message containing the attachments to be cached
	 * @throws Fault if there is a problem handling the message
	 */
	@Override
	public void handleMessage(Message message) throws Fault {
		localInstance.set(message.getAttachments());
	}

	/**
	 * Retrieves the cached attachments from the thread-local storage.
	 * 
	 * @return a collection of cached attachments, or {@code null} if no attachments 
	 *         are cached for the current thread
	 */
	public static Collection<Attachment> getAttachments() {
		Collection<Attachment> result = localInstance.get();
		return result;
	}

	/**
	 * Clears the cached attachments from the thread-local storage.
	 * 
	 * <p/>
	 * 
	 * This method should be called after message processing to avoid memory leaks 
	 * by removing the attachments cached in the current thread's context.
	 */
	public static void clear() {
		localInstance.remove();
	}

}
