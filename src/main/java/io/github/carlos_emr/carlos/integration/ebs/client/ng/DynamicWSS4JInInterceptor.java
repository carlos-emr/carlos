package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import org.apache.cxf.attachment.AttachmentDeserializer;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.logging.log4j.Logger;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.WSS4JConstants;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
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
 *   <li>more than {@link #MAX_ENCRYPTED_KEYS}, or a malformed envelope or root part: the
 *       message is rejected with a {@link Fault} before WSS4J is configured (attachment parts
 *       after the root are left to CXF and WSS4J to validate)</li>
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
 * framing and binary attachment parts that precede and follow the envelope. CARLOS instead
 * locates the SOAP envelope itself (the whole entity for plain SOAP, or the first body part of a
 * multipart package, which is the part CXF treats as the root, delimited by complete RFC 2046
 * delimiter lines) and runs a streaming, namespace-aware StAX scan over just
 * those bytes, via CXF's hardened {@link StaxUtils} reader (external entities off, CXF depth and
 * size limits), rejecting any DOCTYPE outright. Binary attachment parts are never parsed, the
 * scan aborts as soon as the {@code EncryptedKey} bound is exceeded, and anything other than the
 * XML epilog after the envelope is rejected. Unlike substring matching it is
 * not fooled by comments, CDATA, closing tags, or a different namespace prefix.</p>
 *
 * <h2>Bounded scan, disk-backed replay</h2>
 *
 * <p>Because this runs before CXF's attachment handling, the stream it sees is the whole HTTP
 * entity, attachments included, and a multi-file download can be large. The entity is therefore
 * copied into a CXF {@link CachedOutputStream}, which keeps small messages in memory and spills
 * larger ones to a temporary file, and downstream interceptors receive a replay stream over that
 * cache. The cache honours the same spill settings CXF's attachment handling uses for this
 * message: {@code attachment-directory} and {@code attachment-memory-threshold}, looked up as
 * contextual properties (message, exchange, endpoint, then bus); when they are not set, CXF's
 * {@code CachedOutputStream} threshold and temp-directory defaults apply. The per-attachment
 * {@code attachment-max-size} limit is deliberately not applied: this cache holds the whole
 * entity (every attachment together), so applying it here would cap the total size of a
 * multi-file download. CXF's {@code AttachmentDeserializer} still enforces it per attachment
 * downstream. Only a bounded prefix of at
 * most {@link #DEFAULT_MAX_SCAN_BYTES} bytes is read into memory for the scan: it must contain
 * the whole plain SOAP envelope, or the MIME preamble, the root part and the delimiter that ends
 * it. Otherwise the message is rejected. Attachment bytes beyond the prefix are never held in
 * the heap by this class. The replay is handed to the message only after the WSS4J interceptor
 * has been configured and added to the chain; if anything before that fails (the copy, the scan,
 * loading the WSS4J configuration, or adding the interceptor), the replay is closed at once, so
 * any spilled temporary file is deleted rather than left for CXF's
 * {@code CachedOutputStreamCleaner}. After hand-off, closing the replay deletes the file.</p>
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

    /**
     * Default upper bound, in bytes, on the prefix of the entity that is read into memory and
     * scanned: the whole plain SOAP envelope, or the MIME preamble plus the complete root part
     * and its closing delimiter. MCEDT envelopes are a few kilobytes (the payload travels in
     * attachments), so 16 MiB is generous while keeping the per-message heap cost bounded.
     */
    static final int DEFAULT_MAX_SCAN_BYTES = 16 * 1024 * 1024;

    /** Fault text for an envelope that does not fit the scan limit; carries no message content. */
    private static final String SCAN_LIMIT_MESSAGE =
            "MCEDT response SOAP envelope is not complete within the scan size limit";

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
    /** RFC 2046 section 5.1.1: a boundary is 1 to 70 characters. */
    private static final int MAX_BOUNDARY_LENGTH = 70;
    /** An (unfolded) {@code Content-ID} header line; the header name is case-insensitive. */
    private static final Pattern CONTENT_ID_HEADER = Pattern.compile(
            "content-id[ \\t]*:(.*)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final EdtClientBuilder clientBuilder;
    private final int maxScanBytes;
    /**
     * Test override for the replay cache's in-memory threshold; {@code <= 0} means the message's
     * {@code attachment-memory-threshold}, or CXF's configured default when that is not set.
     */
    private final long cacheThreshold;
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * @param clientBuilder supplies the base inbound WSS4J property map for each message
     */
    public DynamicWSS4JInInterceptor(EdtClientBuilder clientBuilder) {
        this(clientBuilder, DEFAULT_MAX_SCAN_BYTES, 0);
    }

    /**
     * Test seam for the scan limit and the replay cache's in-memory threshold, so the bounds
     * can be exercised without multi-megabyte fixtures.
     *
     * @param clientBuilder supplies the base inbound WSS4J property map for each message
     * @param maxScanBytes maximum prefix, in bytes, read into memory and scanned; must be positive
     * @param cacheThreshold bytes kept in memory before the replay cache spills to a temporary
     *                       file, overriding {@code attachment-memory-threshold}; {@code <= 0}
     *                       uses that property, or CXF's configured default when it is not set
     */
    DynamicWSS4JInInterceptor(EdtClientBuilder clientBuilder, int maxScanBytes, long cacheThreshold) {
        super(Phase.RECEIVE);
        if (maxScanBytes <= 0) {
            throw new IllegalArgumentException("maxScanBytes must be positive");
        }
        this.clientBuilder = clientBuilder;
        this.maxScanBytes = maxScanBytes;
        this.cacheThreshold = cacheThreshold;
    }

    /**
     * Inspects the incoming message, builds the WSS4J action list and adds a configured
     * {@link WSS4JInInterceptor} to the chain.
     *
     * @param message the incoming CXF message
     * @throws Fault if the content cannot be read, is malformed, exceeds
     *               {@link #MAX_ENCRYPTED_KEYS}, or its envelope does not fit the scan limit
     *               ({@link #DEFAULT_MAX_SCAN_BYTES}); failing here is deliberate so a
     *               misdetected message never reaches WSS4J with a wrong action list
     */
    @Override
    public void handleMessage(Message message) {
        Detection detection = null;
        try {
            // Fail fast: a guessed action list would only surface later as an opaque
            // WSS4J "actions mismatch" or decryption error.
            detection = detectEncryption(message);

            Map<String, Object> wssProps = clientBuilder.newWSSInInterceptorConfiguration();
            wssProps.put(ConfigurationConstants.ACTION, buildAction(detection.result()));

            message.getInterceptorChain().add(new WSS4JInInterceptor(wssProps));
            // Hand-off is last: until here this method owns the replay. Once it is the
            // message's content, CXF reads and closes it (deleting any spilled temp file).
            if (detection.replay() != null) {
                message.setContent(InputStream.class, detection.replay());
            }
        } catch (IOException | XMLStreamException | RuntimeException e) {
            // The exchange faults, so nothing downstream will read the replay. Close it now
            // (for a spilled cache this deletes the temp file) instead of leaving it to the
            // delayed cleaner; this covers keystore/configuration and chain failures too.
            if (detection != null) {
                closeQuietly(detection.replay(), e);
            }
            throw e instanceof Fault fault ? fault : new Fault(e);
        }
    }

    /**
     * Scan result plus the replay of the entity that is still owned by this interceptor.
     *
     * @param result the encryption detection result
     * @param replay stream over the cached entity, or {@code null} when the message had no stream
     */
    private record Detection(EncryptionDetectionResult result, InputStream replay) {
    }

    private static String buildAction(EncryptionDetectionResult detection) {
        StringBuilder action = new StringBuilder()
                .append(ConfigurationConstants.TIMESTAMP).append(' ')
                .append(ConfigurationConstants.SIGNATURE);

        int encryptionCount = detection.encryptedKeyCount;
        if (encryptionCount == 0 && detection.hasEncryptedData) {
            // Preserves the pre-#3868 behaviour of one Encrypt action whenever EncryptedData was
            // present. Not expected from MCEDT, so surface it for diagnosis.
            logger.warn("MCEDT response contains EncryptedData but no EncryptedKey in the "
                    + "Security header; defaulting to a single Encrypt action.");
            encryptionCount = 1;
        }
        for (int i = 0; i < encryptionCount; i++) {
            action.append(' ').append(ConfigurationConstants.ENCRYPTION);
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
     * Caches the message stream and scans a bounded prefix for the SOAP envelope. On success the
     * replay of the whole entity is returned to the caller, which owns it until it is handed to
     * the message; on any failure here the replay (and any spilled temp file) is released before
     * the exception propagates.
     *
     * <p>A missing or empty stream is treated as "no encryption" (unchanged legacy behaviour);
     * CXF reports the empty response itself.</p>
     */
    private Detection detectEncryption(Message message)
            throws IOException, XMLStreamException {
        InputStream is = message.getContent(InputStream.class);
        if (is == null) {
            logger.warn("No InputStream found in message when detecting encryption.");
            return new Detection(new EncryptionDetectionResult(), null);
        }

        CachedOutputStream cache = new CachedOutputStream();
        byte[] prefix;
        boolean truncated;
        InputStream replay = null;
        try {
            applyCacheSettings(message, cache);
            is.transferTo(cache);
            cache.flush();
            long size = cache.size();
            truncated = size > maxScanBytes;
            // A second, independent reader over the cache: the replay handed downstream must
            // start at byte 0 regardless of how far the scan reads.
            try (InputStream scanCopy = cache.getInputStream()) {
                prefix = scanCopy.readNBytes((int) Math.min(size, maxScanBytes));
            }
            replay = cache.getInputStream();
            // Closing the cache ends writing; a spilled temp file survives until the replay
            // stream (still registered with the cache) is closed, and is then deleted.
            cache.close();
        } catch (IOException | RuntimeException e) {
            // Nothing has been handed downstream yet, so release the cache (and delete any
            // temp file) here rather than leaving it to the delayed cleaner.
            closeQuietly(replay, e);
            closeQuietly(cache, e);
            throw e;
        }
        EncryptionDetectionResult result;
        try {
            result = detectInPrefix(prefix, truncated, (String) message.get(Message.CONTENT_TYPE));
        } catch (IOException | XMLStreamException | RuntimeException e) {
            // A detection failure faults the exchange, so nothing downstream will read the
            // replay. Close it now: for a spilled cache this is what deletes the temp file.
            closeQuietly(replay, e);
            throw e;
        }
        return new Detection(result, replay);
    }

    /**
     * Applies CXF's attachment spill settings for this message to the replay cache, so a large
     * MCEDT download spills to the configured directory at the configured threshold exactly as
     * CXF's own attachment caching would. Mirrors the directory and threshold handling of CXF's
     * {@code AttachmentUtil.setStreamedAttachmentProperties}; {@code attachment-max-size} is
     * intentionally skipped (see the class Javadoc). The package-private test threshold, when
     * set, takes precedence.
     *
     * @throws IOException if a configured value has the wrong type or is not a number; the text
     *                     is fixed and carries no message content
     */
    private void applyCacheSettings(Message message, CachedOutputStream cache) throws IOException {
        Object directory = message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY);
        if (directory instanceof File dir) {
            cache.setOutputDir(dir);
        } else if (directory instanceof String dir) {
            cache.setOutputDir(new File(dir));
        } else if (directory != null) {
            throw new IOException("attachment-directory must be a File or a String");
        }

        Object threshold = message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD);
        if (threshold instanceof Number number) {
            cache.setThreshold(number.longValue());
        } else if (threshold instanceof String text) {
            try {
                cache.setThreshold(Long.parseLong(text.trim()));
            } catch (NumberFormatException e) {
                throw new IOException("attachment-memory-threshold is not a number", e);
            }
        } else if (threshold != null) {
            throw new IOException("attachment-memory-threshold must be a Number or a String");
        }

        if (cacheThreshold > 0) {
            cache.setThreshold(cacheThreshold);
        }
    }

    /**
     * Scans the cached prefix: an empty (whitespace-only) entity means "no encryption",
     * otherwise the envelope is located and its Security header counted.
     */
    private static EncryptionDetectionResult detectInPrefix(byte[] prefix, boolean truncated,
                                                            String contentType)
            throws IOException, XMLStreamException {
        int start = skipWhitespace(prefix, 0);
        if (start == prefix.length) {
            if (truncated) {
                throw new IOException(SCAN_LIMIT_MESSAGE);
            }
            return new EncryptionDetectionResult();
        }

        EncryptionDetectionResult result = scanEnvelope(
                locateEnvelope(prefix, start, contentType, truncated));
        logger.debug("Encryption detection result: hasEncryptedData={}, encryptedKeyCount={}",
                result.hasEncryptedData, result.encryptedKeyCount);
        return result;
    }

    private static void closeQuietly(Closeable closeable, Exception primary) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException | RuntimeException suppressed) {
            primary.addSuppressed(suppressed);
        }
    }

    /**
     * Returns the bytes of the XML envelope: the whole entity for plain SOAP, or the SOAP root
     * part of an MTOM/SwA {@code multipart/related} package.
     *
     * <p>Plain-vs-MIME is decided from the Content-Type when one is present, not by sniffing the
     * first byte, so a MIME preamble beginning with {@code <} or {@code -} cannot flip the
     * decision. For multipart content the boundary and optional {@code start} parameter come
     * from the Content-Type. The root is always the first body part, because that is the part
     * CXF's {@code AttachmentDeserializer} processes as the envelope; if {@code start} is present
     * it must match that part's {@code Content-ID}. Only when no Content-Type is available is the
     * boundary sniffed from a leading delimiter line.</p>
     *
     * <p>Only the preamble, the root part's headers and the delimiter that ends the root part
     * are validated. Later (attachment) parts are not walked: their framing is validated by CXF
     * and their content by WSS4J, so a truncated attachment fails there, not here.</p>
     *
     * <p>If a multipart Content-Type is declared but the entity contains no delimiter line for
     * its boundary and is itself an XML document, it is treated as an already-extracted root
     * part. CXF's {@code AttachmentInInterceptor} (binding interceptor, ordered after this
     * endpoint interceptor in RECEIVE) does that replacement; the fallback keeps detection
     * correct if the ordering ever changes, and cannot be triggered by a genuine MIME package
     * because such a package always has a delimiter line.</p>
     *
     * <p>When {@code truncated} is set, {@code content} is only a prefix of the entity. The
     * envelope must then be complete within it (the root part followed by its delimiter line);
     * a plain envelope, or a root part whose end is not found, is rejected as exceeding the scan
     * limit rather than being scanned partially.</p>
     *
     * @param content the scanned prefix of the entity (the whole entity unless {@code truncated})
     * @param start index of the first non-whitespace byte
     * @param contentType the message Content-Type, may be {@code null}
     * @param truncated whether the entity continues beyond {@code content}
     * @throws IOException if the content is neither XML nor a MIME package whose first part is a
     *                     delimited root part (matching {@code start}, when given), or the
     *                     envelope does not fit within a truncated prefix
     */
    static ByteArrayInputStream locateEnvelope(byte[] content, int start, String contentType,
                                               boolean truncated) throws IOException {
        boolean xmlStart = content[start] == '<' || startsWithUtf8Bom(content, start);
        boolean hasContentType = contentType != null && !contentType.isBlank();
        boolean multipart = hasContentType ? isMultipart(contentType) : !xmlStart;

        if (!multipart) {
            if (!xmlStart) {
                throw new IOException("MCEDT response is not an XML SOAP envelope");
            }
            if (truncated) {
                throw new IOException(SCAN_LIMIT_MESSAGE);
            }
            return new ByteArrayInputStream(content, start, content.length - start);
        }

        byte[] dashBoundary;
        String rootContentId = null;
        if (hasContentType) {
            Map<String, String> params = parseContentTypeParameters(contentType);
            String boundary = params.get("boundary");
            if (boundary == null || boundary.isEmpty() || boundary.length() > MAX_BOUNDARY_LENGTH) {
                throw new IOException("MCEDT response multipart Content-Type has no valid boundary");
            }
            dashBoundary = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            String startParam = params.get("start");
            if (startParam != null) {
                rootContentId = normalizeContentId(startParam);
            }
        } else {
            dashBoundary = sniffDashBoundary(content, start);
        }

        Delimiter delimiter = findDelimiter(content, dashBoundary, 0);
        if (delimiter == null) {
            if (truncated) {
                // The first delimiter (or, for the already-extracted-root fallback, the end of
                // the XML document) may lie beyond the prefix.
                throw new IOException(SCAN_LIMIT_MESSAGE);
            }
            if (hasContentType && xmlStart) {
                return new ByteArrayInputStream(content, start, content.length - start);
            }
            throw new IOException("MCEDT response has no MIME delimiter for its boundary");
        }
        if (delimiter.close) {
            throw new IOException("MCEDT response MIME package has no body parts");
        }

        // The root is always the FIRST body part: CXF's AttachmentDeserializer (4.1.x) ignores
        // the start parameter and hands the first part to the SOAP/WSS4J chain, so counting keys
        // in any other part would build an action list for an envelope WSS4J never sees.
        // A start parameter that names a later part is therefore rejected rather than followed.
        PartHeaders headers = readPartHeaders(content, delimiter.next, truncated);
        if (rootContentId != null && !rootContentId.equals(headers.contentId)) {
            throw new IOException("MCEDT response root MIME part is not the first part");
        }
        Delimiter end = findDelimiter(content, dashBoundary, headers.bodyStart);
        if (end == null) {
            throw new IOException(truncated ? SCAN_LIMIT_MESSAGE
                    : "MCEDT response MIME part is not terminated by a delimiter");
        }
        // Parts after the root are deliberately not walked: this interceptor only needs the
        // envelope. Attachment framing (truncation, missing close delimiter) is enforced by CXF's
        // AttachmentDeserializer and the attachment bytes by WSS4J decryption/signature checks.
        // The line break before a delimiter belongs to the delimiter (RFC 2046 5.1.1).
        int bodyEnd = end.lineStart - 1;
        if (bodyEnd > headers.bodyStart && content[bodyEnd - 1] == '\r') {
            bodyEnd--;
        }
        bodyEnd = Math.max(bodyEnd, headers.bodyStart);
        return new ByteArrayInputStream(content, headers.bodyStart, bodyEnd - headers.bodyStart);
    }

    /** A matched delimiter line: where it starts, where the next line starts, and whether it closes. */
    private record Delimiter(int lineStart, int next, boolean close) { }

    /** Headers of one body part that this class needs, plus where the part body begins. */
    private record PartHeaders(int bodyStart, String contentId) { }

    /**
     * Finds the first complete RFC 2046 delimiter line at or after {@code from}: at the start of
     * a line, {@code --boundary}, an optional {@code --} (close delimiter), optional transport
     * padding (SP/HT), then CRLF or LF, or end of stream for a close delimiter. A line that
     * merely begins with {@code --boundary} (for example {@code --boundary-not-a-delimiter}) is
     * not a delimiter.
     */
    private static Delimiter findDelimiter(byte[] content, byte[] dashBoundary, int from) {
        int i = from;
        while ((i = indexOf(content, dashBoundary, i, content.length)) >= 0) {
            if (i == 0 || content[i - 1] == '\n') {
                Delimiter d = matchDelimiterLine(content, i, dashBoundary.length);
                if (d != null) {
                    return d;
                }
            }
            i++;
        }
        return null;
    }

    private static Delimiter matchDelimiterLine(byte[] content, int lineStart, int dashBoundaryLength) {
        int j = lineStart + dashBoundaryLength;
        boolean close = false;
        if (j + 1 < content.length && content[j] == '-' && content[j + 1] == '-') {
            close = true;
            j += 2;
        }
        while (j < content.length && (content[j] == ' ' || content[j] == '\t')) {
            j++;
        }
        if (j == content.length) {
            return close ? new Delimiter(lineStart, j, true) : null;
        }
        if (content[j] == '\n') {
            return new Delimiter(lineStart, j + 1, close);
        }
        if (content[j] == '\r' && j + 1 < content.length && content[j + 1] == '\n') {
            return new Delimiter(lineStart, j + 2, close);
        }
        return null;
    }

    /**
     * Reads a body part's header block (up to the first empty line), unfolding continuation
     * lines, and returns the body start and the normalized {@code Content-ID}, if any.
     */
    private static PartHeaders readPartHeaders(byte[] content, int from, boolean truncated)
            throws IOException {
        String contentId = null;
        StringBuilder current = null;
        int lineStart = from;
        while (true) {
            int lf = indexOf(content, LF, lineStart, content.length);
            if (lf < 0) {
                throw new IOException(truncated ? SCAN_LIMIT_MESSAGE
                        : "MCEDT response MIME part has no header terminator");
            }
            int lineEnd = lf > lineStart && content[lf - 1] == '\r' ? lf - 1 : lf;
            if (lineEnd == lineStart) {
                if (current != null) {
                    contentId = contentIdOrDefault(current, contentId);
                }
                return new PartHeaders(lf + 1, contentId);
            }
            String line = new String(content, lineStart, lineEnd - lineStart, StandardCharsets.ISO_8859_1);
            if (current != null && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                current.append(line);
            } else {
                if (current != null) {
                    contentId = contentIdOrDefault(current, contentId);
                }
                current = new StringBuilder(line);
            }
            lineStart = lf + 1;
        }
    }

    private static String contentIdOrDefault(CharSequence header, String existing) {
        Matcher m = CONTENT_ID_HEADER.matcher(header);
        return m.matches() ? normalizeContentId(m.group(1)) : existing;
    }

    /** Strips whitespace and one pair of surrounding angle brackets from a Content-ID value. */
    static String normalizeContentId(String id) {
        String trimmed = id.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '<' && trimmed.charAt(trimmed.length() - 1) == '>') {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** Derives {@code --boundary} from a leading delimiter line when no Content-Type is known. */
    private static byte[] sniffDashBoundary(byte[] content, int start) throws IOException {
        if (start + 1 >= content.length || content[start] != '-' || content[start + 1] != '-') {
            throw new IOException("MCEDT response is neither XML nor a MIME multipart package");
        }
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
        if (boundaryEnd == start + 2 || boundaryEnd - start - 2 > MAX_BOUNDARY_LENGTH) {
            throw new IOException("MCEDT response has an invalid MIME boundary");
        }
        return Arrays.copyOfRange(content, start, boundaryEnd);
    }

    private static boolean isMultipart(String contentType) {
        int semi = contentType.indexOf(';');
        String mediaType = (semi < 0 ? contentType : contentType.substring(0, semi)).trim();
        return asciiLowerCase(mediaType).startsWith("multipart/");
    }

    /**
     * Parses Content-Type parameters (RFC 2045 5.1): {@code ;}-separated {@code name=value}
     * pairs where the value is a token or a quoted-string with backslash escapes. Names are
     * folded to ASCII lower case. Quoted {@code ;} and {@code =} do not split parameters; a
     * parameter without {@code =} is skipped, and the first occurrence of a name wins.
     */
    static Map<String, String> parseContentTypeParameters(String contentType) {
        Map<String, String> params = new HashMap<>();
        int n = contentType.length();
        int i = contentType.indexOf(';');
        if (i < 0) {
            return params;
        }
        while (i < n) {
            while (i < n && (contentType.charAt(i) == ';' || Character.isWhitespace(contentType.charAt(i)))) {
                i++;
            }
            int nameStart = i;
            while (i < n && contentType.charAt(i) != '=' && contentType.charAt(i) != ';') {
                i++;
            }
            String name = asciiLowerCase(contentType.substring(nameStart, i).trim());
            if (i >= n || contentType.charAt(i) != '=') {
                // A parameter without '=' (e.g. "; foo;") is skipped. This still makes
                // progress: i is at the ';' (or n), which the separator loop above consumes.
                continue;
            }
            i++;
            while (i < n && Character.isWhitespace(contentType.charAt(i))) {
                i++;
            }
            StringBuilder value = new StringBuilder();
            if (i < n && contentType.charAt(i) == '"') {
                i++;
                while (i < n && contentType.charAt(i) != '"') {
                    char c = contentType.charAt(i);
                    if (c == '\\' && i + 1 < n) {
                        c = contentType.charAt(++i);
                    }
                    value.append(c);
                    i++;
                }
                i++;
                while (i < n && contentType.charAt(i) != ';') {
                    i++;
                }
            } else {
                while (i < n && contentType.charAt(i) != ';') {
                    value.append(contentType.charAt(i));
                    i++;
                }
            }
            if (!name.isEmpty()) {
                params.putIfAbsent(name, value.toString().trim());
            }
        }
        return params;
    }

    /** ASCII-only lower-casing for protocol tokens (locale-independent, no Unicode folding). */
    private static String asciiLowerCase(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] = (char) (chars[i] + ('a' - 'A'));
            }
        }
        return new String(chars);
    }

    /**
     * Streams the envelope and counts {@code xenc:EncryptedKey} elements at any depth inside a
     * {@code wsse:Security} header block ({@code Envelope/Header/Security}), and notes whether
     * any {@code xenc:EncryptedData} is present. After the envelope only the XML epilog
     * (whitespace, comments, processing instructions) is accepted.
     *
     * @throws XMLStreamException if the XML is not well-formed or contains a DTD
     * @throws IOException if the document is not a SOAP envelope, has trailing content, or
     *                     exceeds {@link #MAX_ENCRYPTED_KEYS}
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
                        } else if ("EncryptedKey".equals(local) && securityDepth > 0
                                && ++result.encryptedKeyCount > MAX_ENCRYPTED_KEYS) {
                            throw new IOException("MCEDT response exceeds the maximum of "
                                    + MAX_ENCRYPTED_KEYS + " EncryptedKey elements");
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (depth == securityDepth) {
                        securityDepth = -1;
                    }
                    depth--;
                    if (depth == 0) {
                        requireOnlyMiscAfterRoot(reader);
                        return result;
                    }
                }
            }
            throw new XMLStreamException("MCEDT response SOAP Envelope is not closed");
        } finally {
            StaxUtils.close(reader);
        }
    }

    /**
     * After the Envelope closes, allows only what XML permits after a root element (whitespace,
     * comments, processing instructions). The input here is already just the envelope bytes
     * (whole entity, or the root MIME part sliced at its delimiter), so anything else is trailing
     * garbage that CXF/WSS4J would choke on later; reject it now with a clear error.
     */
    private static void requireOnlyMiscAfterRoot(XMLStreamReader reader)
            throws XMLStreamException, IOException {
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS -> {
                    if (!reader.isWhiteSpace()) {
                        throw new IOException("MCEDT response has content after the SOAP Envelope");
                    }
                }
                case XMLStreamConstants.COMMENT, XMLStreamConstants.PROCESSING_INSTRUCTION,
                        XMLStreamConstants.END_DOCUMENT -> {
                    // permitted epilog
                }
                default -> throw new IOException("MCEDT response has content after the SOAP Envelope");
            }
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
        for (int i = from; i <= limit; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }
}
