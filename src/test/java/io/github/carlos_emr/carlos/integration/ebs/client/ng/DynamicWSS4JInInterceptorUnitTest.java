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
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link DynamicWSS4JInInterceptor}: the WSS4J action list must contain one
 * {@code Encrypt} per {@code xenc:EncryptedKey} in the {@code wsse:Security} header so that
 * multi-file MCEDT downloads validate (issue #3868).
 *
 * <p>Adapted from the MagentaHealth/Open-O test suite (1d8151ef25) for CARLOS's StAX-based
 * detection, with added coverage for MTOM packages, the key bound, and malformed input.</p>
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("mcedt")
@DisplayName("DynamicWSS4JInInterceptor")
class DynamicWSS4JInInterceptorUnitTest {

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String XENC_NS = "http://www.w3.org/2001/04/xmlenc#";

    private static final String TS_SIG = WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.SIGNATURE;

    private DynamicWSS4JInInterceptor interceptor;
    private Message message;
    private InterceptorChain chain;
    private Map<String, Object> wssProps;

    @BeforeEach
    void setUp() {
        EdtClientBuilder clientBuilder = mock(EdtClientBuilder.class);
        message = mock(Message.class);
        chain = mock(InterceptorChain.class);
        wssProps = new HashMap<>();

        when(clientBuilder.newWSSInInterceptorConfiguration()).thenReturn(wssProps);
        when(message.getInterceptorChain()).thenReturn(chain);

        interceptor = new DynamicWSS4JInInterceptor(clientBuilder);
    }

    // ---------------------------------------------------------------- action list

    @Test
    @DisplayName("should configure Timestamp and Signature only when no EncryptedKey is present")
    void shouldConfigureTimestampAndSignatureOnly_whenNoEncryptedKeyPresent() {
        givenContent(envelope(0, false));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
        verify(chain).add(any(WSS4JInInterceptor.class));
    }

    @Test
    @DisplayName("should configure one Encrypt action when one EncryptedKey is present")
    void shouldConfigureOneEncryptAction_whenOneEncryptedKeyPresent() {
        givenContent(envelope(1, true));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    @Test
    @DisplayName("should configure two Encrypt actions when two EncryptedKeys are present")
    void shouldConfigureTwoEncryptActions_whenTwoEncryptedKeysPresent() {
        givenContent(envelope(2, true));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @ParameterizedTest(name = "{0} EncryptedKeys")
    @ValueSource(ints = {3, 4, 6, 11, DynamicWSS4JInInterceptor.MAX_ENCRYPTED_KEYS})
    @DisplayName("should configure N Encrypt actions for N EncryptedKeys")
    void shouldConfigureNEncryptActions_forNEncryptedKeys(int keys) {
        givenContent(envelope(keys, true));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(keys));
    }

    @Test
    @DisplayName("should fall back to one Encrypt action when EncryptedData has no EncryptedKey")
    void shouldFallBackToOneEncryptAction_whenEncryptedDataHasNoEncryptedKey() {
        givenContent(envelope(0, true));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    @Test
    @DisplayName("should count EncryptedKeys in the MTOM root part of a multipart response")
    void shouldCountEncryptedKeys_inMtomRootPart() {
        String boundary = "uuid:6b4a1d7e-0e0b-4c1f-9d7c-3f1b1c2d3e4f";
        String mime = "--" + boundary + "\r\n"
                + "Content-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\"\r\n"
                + "Content-Transfer-Encoding: binary\r\n"
                + "Content-ID: <root.message@cxf.apache.org>\r\n"
                + "\r\n"
                + envelope(3, true)
                + "\r\n--" + boundary + "\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Transfer-Encoding: binary\r\n"
                + "Content-ID: <attachment-1>\r\n"
                + "\r\n"
                // Ciphertext is arbitrary bytes; it must never be handed to the XML parser.
                + "<<not xml <xenc:EncryptedKey <xenc:EncryptedKey \u0000\u0001"
                + "\r\n--" + boundary + "--\r\n";
        givenContent(mime);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(3));
    }

    @Test
    @DisplayName("should locate the MTOM root part after a preamble using the Content-Type boundary")
    void shouldLocateRootPart_whenPreamblePrecedesFirstDelimiter() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn(
                "multipart/related; type=\"application/xop+xml\"; boundary=\"MIMEBoundary_abc\"; "
                        + "start=\"<root>\"; start-info=\"text/xml\"");
        givenContent("This is a multi-part message in MIME format.\r\n"
                + "--MIMEBoundary_abc\r\n"
                + "Content-Type: application/xop+xml\r\n"
                + "Content-ID: <root>\r\n\r\n"
                + envelope(4, true)
                + "\r\n--MIMEBoundary_abc--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(4));
    }

    @Test
    @DisplayName("should tolerate bare LF line endings in the MIME package")
    void shouldLocateRootPart_withBareLfLineEndings() {
        givenContent("--b1\nContent-Type: application/xop+xml\n\n"
                + envelope(2, true)
                + "\n--b1\nContent-Type: application/octet-stream\n\n<<binary>>\n--b1--\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    // ---------------------------------------------------------------- review follow-ups (PR #3898)

    @Test
    @DisplayName("should reject the message when the start parameter names a part other than the first")
    void shouldRejectMessage_whenStartNamesLaterPart() {
        // CXF's AttachmentDeserializer always treats the FIRST part as the envelope, so counting
        // keys in the start-named part would configure WSS4J for an envelope it never processes.
        when(message.get(Message.CONTENT_TYPE)).thenReturn(
                "multipart/related; boundary=b1; type=\"application/xop+xml\"; start=\"<soap-root@carlos>\"");
        givenContent("--b1\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-ID: <attachment-1>\r\n\r\n"
                + envelope(9, true)
                + "\r\n--b1\r\n"
                + "Content-Type: application/xop+xml\r\n"
                + "Content-ID: <soap-root@carlos>\r\n\r\n"
                + envelope(3, true)
                + "\r\n--b1--");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should match an unquoted start parameter against a bracketed, folded Content-ID")
    void shouldMatchStartParameter_withUnquotedStartAndBracketedContentId() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn(
                "Multipart/Related;BOUNDARY=b1;start=soap-root@carlos");
        givenContent("--b1\r\n"
                + "Content-Type: application/xop+xml\r\n"
                + "content-id:\r\n <soap-root@carlos>\r\n\r\n"
                + envelope(2, true)
                + "\r\n--b1\r\n"
                + "Content-ID: <other>\r\n\r\n"
                + "binary\r\n"
                + "--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should configure WSS4J and restore every byte when an attachment after the root is truncated")
    void shouldLeaveAttachmentValidationToCxf_whenAttachmentAfterRootIsTruncated() throws IOException {
        // Contract: only the root part is validated here; CXF's AttachmentDeserializer and WSS4J
        // reject broken attachments downstream, so they must still receive the original bytes.
        String mime = "--b1\r\nContent-Type: application/xop+xml\r\n\r\n"
                + envelope(2, true)
                + "\r\n--b1\r\nContent-Type: application/octet-stream\r\n\r\n<<truncated ciphertext";
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent(mime);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
        ArgumentCaptor<InputStream> restored = ArgumentCaptor.forClass(InputStream.class);
        verify(message).setContent(eq(InputStream.class), restored.capture());
        assertThat(restored.getValue().readAllBytes()).isEqualTo(mime.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should use the first part when the start parameter is absent")
    void shouldUseFirstPart_whenStartParameterAbsent() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=\"b1\"");
        givenContent("--b1\r\nContent-ID: <x>\r\n\r\n"
                + envelope(1, true)
                + "\r\n--b1\r\nContent-ID: <y>\r\n\r\n"
                + envelope(5, true)
                + "\r\n--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    @Test
    @DisplayName("should reject the message when no part matches the start parameter")
    void shouldRejectMessage_whenNoPartMatchesStartParameter() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1; start=\"<missing>\"");
        givenContent("--b1\r\nContent-ID: <root>\r\n\r\n" + envelope(1, true) + "\r\n--b1--\r\n");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should not treat a line that only starts with the delimiter as a delimiter")
    void shouldKeepRootPartIntact_whenLineOnlyStartsWithDelimiter() {
        String rootWithLookalikes = envelope(2, true).replace("<s:Body>",
                "<s:Body><note>\r\n--b1-not-a-delimiter\r\n--b1--x\r\n--b1 x\r\n</note>");
        givenContent("--b1-preamble-lookalike\r\n"
                + "--b1\r\nContent-Type: application/xop+xml\r\n\r\n"
                + rootWithLookalikes
                + "\r\n--b1 \t\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n<<binary>>\r\n--b1--");
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should reject non-whitespace content after a plain SOAP Envelope")
    void shouldRejectMessage_whenContentFollowsPlainEnvelope() {
        givenContent(envelope(1, true) + "\r\ntrailing-garbage");

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject a second element after a plain SOAP Envelope")
    void shouldRejectMessage_whenElementFollowsPlainEnvelope() {
        givenContent(envelope(1, true) + "<extra/>");

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should accept whitespace and comments after a plain SOAP Envelope")
    void shouldAcceptXmlEpilog_afterPlainEnvelope() {
        givenContent(envelope(2, true) + "\r\n  <!-- epilog -->\r\n<?pi data?>\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should use the Content-Type boundary when the MIME preamble starts with an angle bracket")
    void shouldParseMime_whenPreambleStartsWithAngleBracket() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent("<not the envelope, just a preamble>\r\n"
                + "--b1\r\nContent-Type: application/xop+xml\r\n\r\n"
                + envelope(3, true)
                + "\r\n--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(3));
    }

    @Test
    @DisplayName("should use the Content-Type boundary when the MIME preamble starts with dashes")
    void shouldParseMime_whenPreambleStartsWithDashes() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent("--- preamble text ---\r\n"
                + "--b1\r\nContent-Type: application/xop+xml\r\n\r\n"
                + envelope(2, true)
                + "\r\n--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should not sniff MIME when the Content-Type says plain XML")
    void shouldRejectMessage_whenPlainContentTypeBodyLooksLikeMime() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("text/xml; charset=UTF-8");
        givenContent("--b1\r\nContent-Type: application/xop+xml\r\n\r\n" + envelope(1, true) + "\r\n--b1--\r\n");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should treat XML content as the already-extracted root when a multipart type has no delimiter")
    void shouldScanXml_whenMultipartTypeButStreamIsAlreadyRootPart() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1; start=\"<root>\"");
        givenContent(envelope(2, true));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should reject a multipart Content-Type without a boundary")
    void shouldRejectMessage_whenMultipartContentTypeHasNoBoundary() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; type=\"application/xop+xml\"");
        givenContent("--b1\r\n\r\n" + envelope(1, true) + "\r\n--b1--\r\n");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should not split Content-Type parameters on quoted separators")
    void shouldParseQuotedParameters_withEmbeddedSeparators() {
        Map<String, String> params = DynamicWSS4JInInterceptor.parseContentTypeParameters(
                "multipart/related; type=\"a; boundary=wrong\"; Boundary=\"right\\\"q\"; start=<r>");

        assertThat(params)
                .containsEntry("type", "a; boundary=wrong")
                .containsEntry("boundary", "right\"q")
                .containsEntry("start", "<r>");
    }

    @Test
    @DisplayName("should ignore EncryptedKey text in comments, CDATA, the Body and foreign namespaces")
    void shouldIgnoreNonElementEncryptedKeyMarkers_forRobustCounting() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<s:Envelope xmlns:s=\"" + SOAP_NS + "\">"
                + "<s:Header><w:Security xmlns:w=\"" + WSSE_NS + "\" xmlns:e=\"" + XENC_NS + "\">"
                + "<!-- <xenc:EncryptedKey> -->"
                + "<note><![CDATA[<xenc:EncryptedKey>]]></note>"
                + "<other:EncryptedKey xmlns:other=\"urn:not-xenc\"/>"
                + "<e:EncryptedKey Id=\"EK-1\"><e:CipherData/></e:EncryptedKey>"
                + "</w:Security></s:Header>"
                + "<s:Body><e:EncryptedKey xmlns:e=\"" + XENC_NS + "\"/></s:Body>"
                + "</s:Envelope>";
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    // ---------------------------------------------------------------- stream handling

    @Test
    @DisplayName("should restore a readable copy of the message stream for downstream interceptors")
    void shouldRestoreMessageStream_afterDetection() throws IOException {
        String xml = envelope(2, true);
        givenContent(xml);

        interceptor.handleMessage(message);

        ArgumentCaptor<InputStream> restored = ArgumentCaptor.forClass(InputStream.class);
        verify(message).setContent(eq(InputStream.class), restored.capture());
        assertThat(new String(restored.getValue().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(xml);
    }

    @Test
    @DisplayName("should configure Timestamp and Signature when the message has no stream")
    void shouldConfigureTimestampAndSignature_whenStreamIsNull() {
        when(message.getContent(InputStream.class)).thenReturn(null);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
    }

    @Test
    @DisplayName("should configure Timestamp and Signature when the message body is empty")
    void shouldConfigureTimestampAndSignature_whenBodyIsEmpty() {
        givenContent("  \r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
    }

    // ---------------------------------------------------------------- bound and fail-fast

    @Test
    @DisplayName("should reject the message when EncryptedKeys exceed the bound")
    void shouldRejectMessage_whenEncryptedKeysExceedBound() {
        givenContent(envelope(DynamicWSS4JInInterceptor.MAX_ENCRYPTED_KEYS + 1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class)
                .rootCause().hasMessageContaining("maximum of " + DynamicWSS4JInInterceptor.MAX_ENCRYPTED_KEYS);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when the XML is not well-formed")
    void shouldRejectMessage_whenXmlIsMalformed() {
        givenContent("<s:Envelope xmlns:s=\"" + SOAP_NS + "\"><s:Header><unclosed></s:Header></s:Envelope>");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasCauseInstanceOf(XMLStreamException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when the Envelope is truncated")
    void shouldRejectMessage_whenEnvelopeIsTruncated() {
        String full = envelope(2, true);
        givenContent(full.substring(0, full.indexOf("</s:Header>")));

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when the root element is not a SOAP Envelope")
    void shouldRejectMessage_whenRootIsNotSoapEnvelope() {
        givenContent("<html><body>Service unavailable</body></html>");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when it contains a DOCTYPE")
    void shouldRejectMessage_whenDoctypePresent() {
        givenContent("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e \"EncryptedKey\">]>"
                + envelope(1, true).replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", ""));

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when the content is neither XML nor MIME")
    void shouldRejectMessage_whenContentIsNeitherXmlNorMime() {
        givenContent("HTTP garbage");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when the MIME root part is not terminated")
    void shouldRejectMessage_whenMimeRootPartIsUnterminated() {
        givenContent("--boundary\r\nContent-Type: application/xop+xml\r\n\r\n" + envelope(1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when the MIME boundary line is malformed")
    void shouldRejectMessage_whenMimeBoundaryIsMalformed() {
        givenContent("--" + "x".repeat(200) + "\r\n\r\n" + envelope(1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when reading the stream fails")
    void shouldRejectMessage_whenStreamReadFails() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };
        when(message.getContent(InputStream.class)).thenReturn(failing);

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    // ---------------------------------------------------------------- helpers

    private void givenContent(String content) {
        when(message.getContent(InputStream.class))
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertNoWssInterceptorAdded() {
        verify(chain, never()).add(any(Interceptor.class));
    }

    private static String expectedAction(int encryptCount) {
        StringBuilder sb = new StringBuilder(TS_SIG);
        for (int i = 0; i < encryptCount; i++) {
            sb.append(' ').append(WSHandlerConstants.ENCRYPTION);
        }
        return sb.toString();
    }

    /**
     * Builds a synthetic MCEDT-shaped response: {@code keys} EncryptedKey blocks in the Security
     * header (the first for the body, the rest for attachments), plus EncryptedData in the Body
     * when requested. No real data.
     */
    private static String envelope(int keys, boolean encryptedBody) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<s:Envelope xmlns:s=\"").append(SOAP_NS).append("\">")
                .append("<s:Header>")
                .append("<wsse:Security xmlns:wsse=\"").append(WSSE_NS)
                .append("\" xmlns:xenc=\"").append(XENC_NS).append("\" s:mustUnderstand=\"1\">")
                .append("<wsu:Timestamp xmlns:wsu=\"urn:wsu\"/>");
        for (int i = 0; i < keys; i++) {
            sb.append("<xenc:EncryptedKey Id=\"EK-").append(i).append("\">")
                    .append("<xenc:CipherData><xenc:CipherValue>AAAA</xenc:CipherValue></xenc:CipherData>")
                    .append("</xenc:EncryptedKey>");
            if (i > 0) {
                sb.append("<xenc:EncryptedData Id=\"ED-").append(i)
                        .append("\" Type=\"http://docs.oasis-open.org/wss/oasis-wss-SwAProfile-1.1#Attachment-Content-Only\"/>");
            }
        }
        sb.append("</wsse:Security></s:Header><s:Body>");
        if (encryptedBody) {
            sb.append("<xenc:EncryptedData xmlns:xenc=\"").append(XENC_NS).append("\" Id=\"ED-0\"/>");
        }
        return sb.append("</s:Body></s:Envelope>").toString();
    }
}
