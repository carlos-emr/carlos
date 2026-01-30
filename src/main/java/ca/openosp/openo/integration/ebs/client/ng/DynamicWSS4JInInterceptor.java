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
                             ", hasAttachmentEncryption=" + detection.hasAttachmentEncryption +
                             ", encryptedKeyCount=" + detection.encryptedKeyCount);

            Map<String, Object> wssProps = clientBuilder.newWSSInInterceptorConfiguration();

            String action;
            if (!detection.hasEncryption) {
                // No encryption → only timestamp and signature verification
                action = WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.SIGNATURE;
                wssProps.put(WSHandlerConstants.ACTION, action);
                System.out.println("No encryption detected, using actions: " + action);
            } else {
                // Build action string with correct number of Encryption actions based on EncryptedKey count
                StringBuilder actionBuilder = new StringBuilder();
                actionBuilder.append(WSHandlerConstants.TIMESTAMP).append(" ")
                            .append(WSHandlerConstants.SIGNATURE);

                // Add one Encryption action for each EncryptedKey element
                int encryptionCount = detection.encryptedKeyCount > 0 ? detection.encryptedKeyCount : 1;
                for (int i = 0; i < encryptionCount; i++) {
                    actionBuilder.append(" ").append(WSHandlerConstants.ENCRYPTION);
                }

                action = actionBuilder.toString();
                wssProps.put(WSHandlerConstants.ACTION, action);
                System.out.println("Encryption detected with " + encryptionCount + " EncryptedKey elements, using actions: " + action);
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
        int encryptedKeyCount;
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

            // Count EncryptedKey elements to determine how many Encryption actions are needed
            result.encryptedKeyCount = countOccurrences(xml, "<xenc:EncryptedKey");

            // Extract and log KeyId information for debugging certificate mismatch issues
            if (result.hasEncryption) {
                extractAndLogKeyIdInfo(xml);
            }

            logger.debug("Encryption detection result: hasEncryption={}, hasAttachmentEncryption={}, encryptedKeyCount={}",
                    result.hasEncryption, result.hasAttachmentEncryption, result.encryptedKeyCount);

        } catch (Exception e) {
            logger.error("Error reading message content for encryption detection", e);
        }
        return result;
    }

    /**
     * Counts the number of occurrences of a substring in a string.
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Extracts and logs KeyId information from the encrypted SOAP message.
     * This helps diagnose certificate mismatch issues by showing what certificate
     * the MCEDT server used to encrypt the response.
     */
    private void extractAndLogKeyIdInfo(String xml) {
        try {
            System.out.println("=== MCEDT DEBUG: Analyzing KeyId in Encrypted Message ===");
            System.out.println(xml);

            System.out.println("=== MCEDT DEBUG: End KeyId Analysis ===");
            System.out.println("");

        } catch (Exception e) {
            System.out.println("ERROR extracting KeyId info: " + e.getMessage());
            logger.error("Error extracting KeyId information", e);
        }
    }

    /**
     * Extracts the EncryptedKey section from the SOAP message.
     */
    private String extractEncryptedKeySection(String xml) {
        try {
            // Try different variations of EncryptedKey tag
            String[] startTags = {
                "<xenc:EncryptedKey",
                "<EncryptedKey",
                ":EncryptedKey"
            };

            String[] endTags = {
                "</xenc:EncryptedKey>",
                "</EncryptedKey>",
                ":EncryptedKey>"
            };

            for (String startTag : startTags) {
                int startIdx = xml.indexOf(startTag);
                if (startIdx != -1) {
                    // Find matching end tag
                    for (String endTag : endTags) {
                        int endIdx = xml.indexOf(endTag, startIdx);
                        if (endIdx != -1) {
                            endIdx += endTag.length();
                            return xml.substring(startIdx, endIdx);
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Simple helper to extract content from an XML element.
     * Handles elements with or without namespace prefixes.
     */
    private String extractXmlElement(String xml, String elementName) {
        try {
            // Try with ds: prefix first
            String withPrefix = extractElementContent(xml, "ds:" + elementName);
            if (withPrefix != null) {
                return withPrefix;
            }

            // Try without prefix
            return extractElementContent(xml, elementName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts content between opening and closing tags.
     */
    private String extractElementContent(String xml, String tagName) {
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";

        int startIdx = xml.indexOf(startTag);
        if (startIdx == -1) {
            // Try with attributes
            startTag = "<" + tagName + " ";
            startIdx = xml.indexOf(startTag);
            if (startIdx != -1) {
                // Find the end of the opening tag
                int tagEndIdx = xml.indexOf(">", startIdx);
                if (tagEndIdx != -1) {
                    startIdx = tagEndIdx + 1;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            startIdx += startTag.length();
        }

        int endIdx = xml.indexOf(endTag, startIdx);
        if (endIdx == -1) {
            return null;
        }

        String content = xml.substring(startIdx, endIdx).trim();
        return content.isEmpty() ? null : content;
    }
}
