package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.logging.log4j.Logger;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CXF interceptor that configures the inbound WSS4J action list from the content of the
 * MCEDT response being received.
 *
 * <p>WSS4J's inbound handler rejects a message whose processed security results do not match
 * the configured {@code ACTION} list one-for-one. An MCEDT download response carries one
 * {@code xenc:EncryptedKey} in the {@code wsse:Security} header for the SOAP body plus one per
 * encrypted attachment, so a download of N files needs N + 1 {@code Encrypt} actions. The
 * previous implementation only ever configured one or two, which broke multi-file EDT, RA and
 * report downloads (issue #3868).</p>
 *
 * <p>The resulting action list is:</p>
 * <ul>
 *   <li>0 {@code EncryptedKey} and no {@code EncryptedData}: {@code Timestamp Signature}</li>
 *   <li>0 {@code EncryptedKey} but {@code EncryptedData} present: one {@code Encrypt}
 *       (legacy fallback, logged as a warning)</li>
 *   <li>N {@code EncryptedKey} (1 &lt;= N &lt;= {@link #MAX_ENCRYPTED_KEYS}):
 *       N {@code Encrypt} actions</li>
 *   <li>more than {@link #MAX_ENCRYPTED_KEYS}, or malformed input: the message is rejected
 *       with a {@link Fault} before WSS4J is configured</li>
 * </ul>
 *
 * <h2>Why a raw-bytes StAX scan in the RECEIVE phase</h2>
 *
 * <p>The WSS4J interceptor has to be configured before it runs in {@code PRE_PROTOCOL}, so this
 * interceptor runs in {@link Phase#RECEIVE}. At that point no SAAJ/DOM view of the envelope
 * exists yet; the only content is the raw HTTP entity stream. For MTOM responses (all MCEDT
 * downloads) that stream is a {@code multipart/related} MIME package, not an XML document; this
 * is the likely reason upstream Open-O's attempt to DOM/XPath-parse the whole buffered stream
 * (fdfdd04dc2) was reverted to substring counting (eddce81dd7): a DOM parse fails on the MIME
 * framing and binary attachment parts that precede and follow the envelope. CARLOS instead locates the
 * XML root part itself (the first MIME part, or the whole entity for plain SOAP) and runs a
 * streaming, namespace-aware StAX scan over just that part, via CXF's hardened
 * {@link StaxUtils} reader (external entities off, CXF depth and size limits), rejecting any
 * DOCTYPE outright. The scan
 * stops at the end of the envelope, so the binary attachment parts are never parsed, and it
 * aborts as soon as the {@code EncryptedKey} bound is exceeded. Unlike substring matching it is
 * not fooled by comments, CDATA, closing tags, or a different namespace prefix.</p>
 *
 * <p>Message content is never logged or placed in fault messages: MCEDT payloads can contain
 * claims and patient data.</p>
 *
 * @since 2025-08-13
 */
public class DynamicWSS4JInInterceptor extends AbstractPhaseInterceptor<Message> {

    /**
     * Upper bound on the number of {@code EncryptedKey} elements accepted in one response.
     *
     * <p>Each key becomes one WSS4J {@code Encrypt} action, and so one RSA key-unwrap during
     * security processing. MCEDT limits a download request to a handful of resources (one key
     * for the body plus one per attachment), so 20 leaves ample headroom while preventing a
     * hostile or corrupted response from forcing unbounded action-list construction and
     * decryption work. Exceeding it rejects the message rather than silently capping, because a
     * capped action list would fail WSS4J's action/result matching anyway.</p>
     */
    static final int MAX_ENCRYPTED_KEYS = 20;

    private static final String WSSE_NS = WSS4JConstants.WSSE_NS;
    private static final String XENC_NS = WSS4JConstants.ENC_NS;
    private static final String SOAP11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";

    private static final byte[] LF = {'\n'};
    /**
     * A MIME boundary is at most 70 characters (RFC 2046 section 5.1.1); allow for the "--"
     * prefix, transport padding and CRLF before declaring the delimiter line malformed.
     */
    private static final int MAX_BOUNDARY_LINE_LENGTH = 100;
    /** RFC 2046 boundary parameter, quoted or token form, at most 70 characters. */
    private static final Pattern BOUNDARY_PARAM = Pattern.compile(
            "(?:^|;)\\s*boundary\\s*=\\s*(?:\"([^\"]{1,70})\"|([^;\\s\"]{1,70}))",
            Pattern.CASE_INSENSITIVE);

    private final EdtClientBuilder clientBuilder;
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * @param clientBuilder supplies the base inbound WSS4J property map for each message
     */
    public DynamicWSS4JInInterceptor(EdtClientBuilder clientBuilder) {
        super(Phase.RECEIVE);
        this.clientBuilder = clientBuilder;
    }

    /**
     * Inspects the incoming message, builds the WSS4J action list and adds a configured
     * {@link WSS4JInInterceptor} to the chain.
     *
     * @param message the incoming CXF message
     * @throws Fault if the content cannot be read, is malformed, or exceeds
     *               {@link #MAX_ENCRYPTED_KEYS}; failing here is deliberate so a
     *               misdetected message never reaches WSS4J with a wrong action list
     */
    @Override
    public void handleMessage(Message message) {
        try {
            // Fail fast: a guessed action list would only surface later as an opaque
            // WSS4J "actions mismatch" or decryption error.
            EncryptionDetectionResult detection = detectEncryption(message);

            Map<String, Object> wssProps = clientBuilder.newWSSInInterceptorConfiguration();
            wssProps.put(WSHandlerConstants.ACTION, buildAction(detection));

            message.getInterceptorChain().add(new WSS4JInInterceptor(wssProps));
        } catch (IOException | XMLStreamException | RuntimeException e) {
            throw e instanceof Fault ? (Fault) e : new Fault(e);
        }
    }

    private static String buildAction(EncryptionDetectionResult detection) {
        StringBuilder action = new StringBuilder()
                .append(WSHandlerConstants.TIMESTAMP).append(' ')
                .append(WSHandlerConstants.SIGNATURE);

        int encryptionCount = detection.encryptedKeyCount;
        if (encryptionCount == 0 && detection.hasEncryptedData) {
            // Preserves the pre-#3868 behaviour of one Encrypt action whenever EncryptedData was
            // present. Not expected from MCEDT, so surface it for diagnosis.
            logger.warn("MCEDT response contains EncryptedData but no EncryptedKey in the "
                    + "Security header; defaulting to a single Encrypt action.");
            encryptionCount = 1;
        }
        for (int i = 0; i < encryptionCount; i++) {
            action.append(' ').append(WSHandlerConstants.ENCRYPTION);
        }
        return action.toString();
    }

    /** Result of scanning the SOAP envelope. */
    static final class EncryptionDetectionResult {
        /** Whether any {@code xenc:EncryptedData} element appears in the envelope. */
        boolean hasEncryptedData;
        /** Number of {@code xenc:EncryptedKey} elements inside {@code wsse:Security} headers. */
        int encryptedKeyCount;
    }

    /**
     * Buffers the message stream, restores it for downstream interceptors, and scans the SOAP
     * envelope.
     *
     * <p>A missing or empty stream is treated as "no encryption" (unchanged legacy behaviour);
     * CXF reports the empty response itself.</p>
     */
    private EncryptionDetectionResult detectEncryption(Message message)
            throws IOException, XMLStreamException {
        InputStream is = message.getContent(InputStream.class);
        if (is == null) {
            logger.warn("No InputStream found in message when detecting encryption.");
            return new EncryptionDetectionResult();
        }

        byte[] content;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            is.transferTo(bos);
            content = bos.toByteArray();
        }
        // Restore the stream before any parsing so CXF can still consume it even if the
        // scan below throws.
        message.setContent(InputStream.class, new ByteArrayInputStream(content));

        int start = skipWhitespace(content, 0);
        if (start == content.length) {
            return new EncryptionDetectionResult();
        }

        EncryptionDetectionResult result = scanEnvelope(
                locateRootPart(content, start, (String) message.get(Message.CONTENT_TYPE)));
        logger.debug("Encryption detection result: hasEncryptedData={}, encryptedKeyCount={}",
                result.hasEncryptedData, result.encryptedKeyCount);
        return result;
    }

    /**
     * Returns the bytes of the XML envelope: the whole entity for plain SOAP, or the first
     * (root) part of an MTOM/SwA {@code multipart/related} package.
     *
     * <p>When the entity starts with a delimiter line the boundary is taken from that line, so
     * this works whether or not CXF's attachment handling has already replaced the stream with
     * the root part at this point in the RECEIVE phase. Otherwise (a MIME preamble precedes the
     * first delimiter) the boundary comes from the message Content-Type. Bare-LF line endings
     * are tolerated alongside the RFC 2046 CRLF.</p>
     *
     * @param content the buffered entity
     * @param start index of the first non-whitespace byte
     * @param contentType the message Content-Type, may be {@code null}
     * @throws IOException if the content is neither XML nor a well-formed MIME package
     */
    static ByteArrayInputStream locateRootPart(byte[] content, int start, String contentType)
            throws IOException {
        if (content[start] == '<' || startsWithUtf8Bom(content, start)) {
            return new ByteArrayInputStream(content, start, content.length - start);
        }

        byte[] dashBoundary;
        int delimiterStart;
        if (start + 1 < content.length && content[start] == '-' && content[start + 1] == '-') {
            int lineEnd = indexOf(content, LF, start, start + MAX_BOUNDARY_LINE_LENGTH);
            if (lineEnd < 0) {
                throw new IOException("MCEDT response has a malformed MIME boundary line");
            }
            // Strip CR and any transport padding (spaces/tabs) permitted after the delimiter.
            int boundaryEnd = lineEnd;
            while (boundaryEnd > start + 2 && (content[boundaryEnd - 1] == '\r'
                    || content[boundaryEnd - 1] == ' ' || content[boundaryEnd - 1] == '\t')) {
                boundaryEnd--;
            }
            if (boundaryEnd == start + 2) {
                throw new IOException("MCEDT response has an empty MIME boundary");
            }
            dashBoundary = Arrays.copyOfRange(content, start, boundaryEnd);
            delimiterStart = start;
        } else {
            String boundary = boundaryFromContentType(contentType);
            if (boundary == null) {
                throw new IOException("MCEDT response is neither XML nor a MIME multipart package");
            }
            dashBoundary = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
            int found = indexOf(content, lfPrefixed(dashBoundary), start, content.length);
            if (found < 0) {
                throw new IOException("MCEDT response has no MIME delimiter for its declared boundary");
            }
            delimiterStart = found + 1;
        }

        // Skip the delimiter line, then the root part's MIME headers up to the first empty line.
        int lineStart = indexOf(content, LF, delimiterStart, content.length);
        int bodyStart = -1;
        while (lineStart >= 0) {
            lineStart++;
            int next = indexOf(content, LF, lineStart, content.length);
            if (next < 0) {
                break;
            }
            int lineEnd = next > lineStart && content[next - 1] == '\r' ? next - 1 : next;
            if (lineEnd == lineStart) {
                bodyStart = next + 1;
                break;
            }
            lineStart = next;
        }
        if (bodyStart < 0) {
            throw new IOException("MCEDT response root MIME part has no header terminator");
        }

        int bodyEnd = indexOf(content, lfPrefixed(dashBoundary), bodyStart, content.length);
        if (bodyEnd < 0) {
            throw new IOException("MCEDT response root MIME part is not terminated by a boundary");
        }
        if (bodyEnd > bodyStart && content[bodyEnd - 1] == '\r') {
            bodyEnd--;
        }
        return new ByteArrayInputStream(content, bodyStart, bodyEnd - bodyStart);
    }

    /** Extracts the {@code boundary} parameter from a multipart Content-Type, or {@code null}. */
    static String boundaryFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher m = BOUNDARY_PARAM.matcher(contentType);
        if (!m.find()) {
            return null;
        }
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private static byte[] lfPrefixed(byte[] dashBoundary) {
        byte[] needle = new byte[dashBoundary.length + 1];
        needle[0] = '\n';
        System.arraycopy(dashBoundary, 0, needle, 1, dashBoundary.length);
        return needle;
    }

    /**
     * Streams the envelope and counts {@code xenc:EncryptedKey} elements at any depth inside a
     * {@code wsse:Security} header block ({@code Envelope/Header/Security}), and notes whether
     * any {@code xenc:EncryptedData} is present. Stops at the end of the envelope.
     *
     * @throws XMLStreamException if the XML is not well-formed or contains a DTD
     * @throws IOException if the document is not a SOAP envelope or exceeds
     *                     {@link #MAX_ENCRYPTED_KEYS}
     */
    static EncryptionDetectionResult scanEnvelope(InputStream xml)
            throws XMLStreamException, IOException {
        EncryptionDetectionResult result = new EncryptionDetectionResult();
        XMLStreamReader reader = StaxUtils.createXMLStreamReader(xml);
        try {
            int depth = 0;
            int securityDepth = -1;
            boolean inHeader = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) {
                    throw new XMLStreamException("DOCTYPE is not allowed in a SOAP message");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    String ns = reader.getNamespaceURI();
                    String local = reader.getLocalName();
                    if (depth == 1) {
                        if (!"Envelope".equals(local) || !isSoapNamespace(ns)) {
                            throw new IOException("MCEDT response root element is not a SOAP Envelope");
                        }
                    } else if (depth == 2) {
                        inHeader = "Header".equals(local) && isSoapNamespace(ns);
                    } else if (depth == 3 && inHeader && "Security".equals(local) && WSSE_NS.equals(ns)) {
                        securityDepth = depth;
                    }

                    if (XENC_NS.equals(ns)) {
                        if ("EncryptedData".equals(local)) {
                            result.hasEncryptedData = true;
                        } else if ("EncryptedKey".equals(local) && securityDepth > 0) {
                            if (++result.encryptedKeyCount > MAX_ENCRYPTED_KEYS) {
                                throw new IOException("MCEDT response exceeds the maximum of "
                                        + MAX_ENCRYPTED_KEYS + " EncryptedKey elements");
                            }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (depth == securityDepth) {
                        securityDepth = -1;
                    }
                    depth--;
                    if (depth == 0) {
                        // End of Envelope: do not read past it (trailing MIME parts are binary).
                        return result;
                    }
                }
            }
            throw new XMLStreamException("MCEDT response SOAP Envelope is not closed");
        } finally {
            StaxUtils.close(reader);
        }
    }

    private static boolean isSoapNamespace(String ns) {
        return SOAP11_NS.equals(ns) || SOAP12_NS.equals(ns);
    }

    private static boolean startsWithUtf8Bom(byte[] content, int start) {
        return start + 2 < content.length && (content[start] & 0xFF) == 0xEF
                && (content[start + 1] & 0xFF) == 0xBB && (content[start + 2] & 0xFF) == 0xBF;
    }

    private static int skipWhitespace(byte[] content, int from) {
        int i = from;
        while (i < content.length && (content[i] == ' ' || content[i] == '\t'
                || content[i] == '\r' || content[i] == '\n')) {
            i++;
        }
        return i;
    }

    /** Naive byte search; needles are at most ~72 bytes. */
    private static int indexOf(byte[] haystack, byte[] needle, int from, int to) {
        int limit = Math.min(to, haystack.length) - needle.length;
        outer:
        for (int i = from; i <= limit; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
