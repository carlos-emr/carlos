package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.cxf.attachment.LazyAttachmentCollection;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * A CXF interceptor that processes incoming attachments and message properties after a message has been invoked.
 * 
 * <h3>Purpose</h3>
 * 
 * The {@code DownloadInInterceptor} is designed to handle attachments after the main invocation phase 
 * of a message, typically used in scenarios where the client downloads and processes attachments 
 * from a response message.
 * 
 * <h3>Usage</h3>
 * 
 * This interceptor operates in the {@link Phase#POST_INVOKE} phase, meaning it processes the 
 * message after it has been fully invoked. It retrieves and logs all attachments from the message 
 * and prints out all key-value pairs from the message's internal map.
 * 
 * <h3>Implementation notes</h3>
 * 
 * - The attachments are accessed via the {@link LazyAttachmentCollection}, which allows the 
 *   attachments to be loaded lazily (i.e., only when needed).
 * - It prints the attachment ID and content to the standard output.
 * - If an {@link IOException} occurs when reading the attachment data, the exception is swallowed 
 *   and logged as part of the message processing.
 */
public class DownloadInInterceptor extends AbstractPhaseInterceptor<Message> {

	/**
	 * Constructs a new {@code DownloadInInterceptor} and sets it to operate in the POST_INVOKE phase.
	 */
	public DownloadInInterceptor() {
		super(Phase.POST_INVOKE);
	}

	/**
	 * Handles the incoming message by processing its attachments and logging message properties.
	 * 
	 * @param message The incoming {@link Message} to be processed.
	 * @throws Fault If there is a problem during message processing.
	 */
	@Override
	public void handleMessage(Message message) throws Fault {
		// ---- Handle attachments safely ----
        Object attObj = message.get(Message.ATTACHMENTS);
        if (attObj instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Attachment> attachments = (Collection<Attachment>) attObj;
            for (Attachment a : attachments) {
                System.out.println("Attachment ID: " + a.getId());
                try (InputStream attIs = a.getDataHandler().getInputStream()) {
                    System.out.println(IOUtils.readStringFromStream(attIs));
                } catch (IOException e) {
                    // swallow or log
                }
            }
        }

        // ---- Copy the message input stream so we can read it multiple times ----
        try {
            InputStream is = message.getContent(InputStream.class);
            if (is != null) {
                CachedOutputStream cos = new CachedOutputStream();
                IOUtils.copy(is, cos);
                cos.flush();

                // Put the stream back so CXF and later interceptors can still use it
                message.setContent(InputStream.class, cos.getInputStream());

                // Get message as String (once)
                String soapText = IOUtils.toString(cos.getInputStream(), StandardCharsets.UTF_8.name());
                System.out.println("Decrypted SOAP Response Message:\n" + soapText);
            } else {
                System.out.println("No InputStream found in the message content.");
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
	}

}
