package ca.openosp.openo.integration.ebs.client.ng;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.logging.log4j.Logger;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import ca.openosp.openo.utility.MiscUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Custom interceptor that dynamically configures WSS4J based on message content.
 * Detects encryption in the SOAP body and/or attachments, and configures actions accordingly.
 */
public class DynamicWSS4JInInterceptor extends AbstractPhaseInterceptor<Message> {

    private final EdtClientBuilder clientBuilder;
    private static final Logger logger = MiscUtils.getLogger();

    public DynamicWSS4JInInterceptor(EdtClientBuilder clientBuilder) {
        super(Phase.RECEIVE);
        this.clientBuilder = clientBuilder;
    }

    @Override
    public void handleMessage(Message message) {
        try {
            System.out.println("=== MCEDT DEBUG: DynamicWSS4JInInterceptor handling message ===");
            EncryptionDetectionResult detection = detectEncryption(message);

            System.out.println("Encryption detection: hasEncryption=" + detection.hasEncryption +
                             ", hasAttachmentEncryption=" + detection.hasAttachmentEncryption);

            Map<String, Object> wssProps = clientBuilder.newWSSInInterceptorConfiguration();

            String action;
            if (!detection.hasEncryption) {
                // No encryption → only timestamp and signature verification
                action = WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.SIGNATURE;
                wssProps.put(WSHandlerConstants.ACTION, action);
                System.out.println("No encryption detected, using actions: " + action);
            } else if (detection.hasAttachmentEncryption) {
                // Both SOAP body and attachment encryption → encryption action twice
                action = WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.SIGNATURE + " "
                                + WSHandlerConstants.ENCRYPTION + " " + WSHandlerConstants.ENCRYPTION;
                wssProps.put(WSHandlerConstants.ACTION, action);
                System.out.println("Both body and attachment encryption detected, using actions: " + action);
            } else {
                // Only one encryption block
                action = WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.SIGNATURE + " " + WSHandlerConstants.ENCRYPTION;
                wssProps.put(WSHandlerConstants.ACTION, action);
                System.out.println("Body encryption detected, using actions: " + action);
            }

            // Add WSS4J interceptor to chain with appropriate configuration
            WSS4JInInterceptor wssInterceptor = new WSS4JInInterceptor(wssProps);
            message.getInterceptorChain().add(wssInterceptor);

            System.out.println("=== MCEDT DEBUG: WSS4J interceptor added to chain ===");

        } catch (Exception e) {
            System.out.println("=== MCEDT DEBUG: ERROR in DynamicWSS4JInInterceptor: " + e.getMessage() + " ===");
            logger.error("Error in DynamicWSS4JInInterceptor", e);
            throw new Fault(e);
        }
    }

    private static class EncryptionDetectionResult {
        boolean hasEncryption;
        boolean hasAttachmentEncryption;
    }

    /**
     * Detects if the incoming message has encryption in the SOAP body or encrypted attachments.
     */
    private EncryptionDetectionResult detectEncryption(Message message) {
        EncryptionDetectionResult result = new EncryptionDetectionResult();
        try {
            InputStream is = message.getContent(InputStream.class);
            if (is == null) {
                logger.warn("No InputStream found in message when detecting encryption.");
                return result;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }

            String xml = bos.toString("UTF-8");

            // Reset stream so CXF can still process it downstream
            message.setContent(InputStream.class,
                    new java.io.ByteArrayInputStream(bos.toByteArray()));

            // Detect body encryption markers
            if (xml.contains("<wsse:EncryptedData") || xml.contains("<xenc:EncryptedData")) {
                result.hasEncryption = true;
            }

            // Detect attachment encryption markers
            if (xml.contains("Attachment-Content-Only")) {
                result.hasAttachmentEncryption = true;
            }

            logger.debug("Encryption detection result: hasEncryption={}, hasAttachmentEncryption={}",
                    result.hasEncryption, result.hasAttachmentEncryption);

        } catch (Exception e) {
            logger.error("Error reading message content for encryption detection", e);
        }
        return result;
    }
}
