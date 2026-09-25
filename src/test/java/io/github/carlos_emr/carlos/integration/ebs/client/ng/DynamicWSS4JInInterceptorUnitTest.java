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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.xml.stream.XMLStreamException;

import org.apache.cxf.attachment.AttachmentDeserializer;
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
import org.junit.jupiter.api.io.TempDir;
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
    private EdtClientBuilder clientBuilder;
    private Message message;
    private InterceptorChain chain;
    private Map<String, Object> wssProps;
    /** Per-test spill directory for the replay cache; see {@link #givenSpillDirectory()}. */
    @TempDir
    File spillDir;

    @BeforeEach
    void setUp() {
        clientBuilder = mock(EdtClientBuilder.class);
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
    @DisplayName("should scan the first part when the start parameter names a later part, as CXF does")
    void shouldUseFirstPart_whenStartNamesLaterPart() {
        // CXF's AttachmentDeserializer ignores start and always treats the FIRST part as the
        // envelope, so the key count must come from the first part, whatever start says.
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

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(9));
    }

    @Test
    @DisplayName("should accept a first part whose Content-ID differs from the start parameter")
    void shouldUseFirstPart_whenStartDiffersFromFirstPartContentId() {
        // A gateway may label its root part differently from start; CXF accepts that, so must we.
        when(message.get(Message.CONTENT_TYPE)).thenReturn(
                "multipart/related; boundary=b1; start=\"<rootpart@soapui.org>\"");
        givenContent("--b1\r\nContent-Type: application/xop+xml\r\n"
                + "Content-ID: <root.message@cxf.apache.org>\r\n\r\n"
                + envelope(2, true)
                + "\r\n--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should accept a first part with no Content-ID when start is present")
    void shouldUseFirstPart_whenRootPartHasNoContentId() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1; start=\"<root>\"");
        givenContent("--b1\r\nContent-Type: application/xop+xml\r\n\r\n" + envelope(3, true) + "\r\n--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(3));
    }

    @Test
    @DisplayName("should scan the first part with an unquoted start parameter and a folded Content-ID")
    void shouldUseFirstPart_withUnquotedStartAndFoldedContentId() {
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
    @DisplayName("should scan the first part when no part matches the start parameter")
    void shouldUseFirstPart_whenNoPartMatchesStartParameter() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1; start=\"<missing>\"");
        givenContent("--b1\r\nContent-ID: <root>\r\n\r\n" + envelope(1, true) + "\r\n--b1--\r\n");

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
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
    void shouldParseQuotedParameters_withEmbeddedSeparators() throws IOException {
        Map<String, String> params = DynamicWSS4JInInterceptor.parseContentTypeParameters(
                "multipart/related; type=\"a; boundary=wrong\"; Boundary=\"right\\\"q\"; start=<r>");

        assertThat(params)
                .containsEntry("type", "a; boundary=wrong")
                .containsEntry("boundary", "right\"q")
                .containsEntry("start", "<r>");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "multipart/related; boundary=\"b1",
            "multipart/related; boundary=\"b1\\\"",
            "multipart/related; start=\"<root>; boundary=b1",
            "multipart/related; boundary=\""})
    @DisplayName("should reject a Content-Type whose quoted parameter has no closing quote")
    void shouldRejectContentType_whenQuotedParameterIsUnterminated(String contentType) {
        assertThatThrownBy(() -> DynamicWSS4JInInterceptor.parseContentTypeParameters(contentType))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("unterminated quoted parameter");
    }

    @Test
    @DisplayName("should parse a quoted boundary and one with an escaped quote")
    void shouldParseQuotedBoundary_withAndWithoutEscapedQuote() throws IOException {
        assertThat(DynamicWSS4JInInterceptor.parseContentTypeParameters("multipart/related; boundary=\"b1\""))
                .containsEntry("boundary", "b1");
        assertThat(DynamicWSS4JInInterceptor.parseContentTypeParameters(
                "multipart/related; boundary=\"b\\\"1\"; start=\"<r>\""))
                .containsEntry("boundary", "b\"1")
                .containsEntry("start", "<r>");
    }

    @Test
    @DisplayName("should reject the message when its multipart boundary quote is unterminated")
    void shouldRejectMessage_whenBoundaryQuoteIsUnterminated() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=\"b1");
        givenContent("--b1\r\nContent-Type: application/xop+xml\r\n\r\n" + envelope(1, true)
                + "\r\n--b1--\r\n");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasMessageContaining("unterminated quoted parameter");
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "multipart/related; foo; boundary=b1",
            "multipart/related;foo;boundary=b1",
            "multipart/related; foo ;; bar; boundary=b1; baz",
            "multipart/related; boundary=b1; foo"})
    @DisplayName("should skip parameters without '=' and terminate")
    void shouldSkipParametersWithoutAssignment_forMalformedContentType(String contentType) {
        Map<String, String> params = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> DynamicWSS4JInInterceptor.parseContentTypeParameters(contentType));

        assertThat(params).containsOnlyKeys("boundary").containsEntry("boundary", "b1");
    }

    @Test
    @DisplayName("should detect keys when the Content-Type has a parameter without '='")
    void shouldCountEncryptedKeys_whenContentTypeHasParameterWithoutAssignment() {
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; foo; boundary=b1");
        givenContent("--b1\r\nContent-Type: application/xop+xml\r\n\r\n" + envelope(2, true)
                + "\r\n--b1--\r\n");

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> interceptor.handleMessage(message));

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
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
                + "<e:EncryptedKey Id=\"EK-1\"><e:CipherData/>"
                + "<e:ReferenceList><e:DataReference URI=\"#ED-1\"/></e:ReferenceList></e:EncryptedKey>"
                + "</w:Security></s:Header>"
                + "<s:Body><e:EncryptedKey xmlns:e=\"" + XENC_NS + "\"/></s:Body>"
                + "</s:Envelope>";
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    @Test
    @DisplayName("should count a header EncryptedData with an embedded key once, not the nested key")
    void shouldCountHeaderEncryptedDataNotNestedKey_whenKeyIsEmbeddedInKeyInfo() {
        // WSS4J dispatches the direct EncryptedData (one result, via its embedded key) and the
        // direct EncryptedKey with references (one result). The nested key is never dispatched.
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<s:Envelope xmlns:s=\"" + SOAP_NS + "\">"
                + "<s:Header><w:Security xmlns:w=\"" + WSSE_NS + "\" xmlns:e=\"" + XENC_NS + "\">"
                + "<e:EncryptedData Id=\"ED-hdr\"><ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">"
                + "<e:EncryptedKey Id=\"EK-nested\"/></ds:KeyInfo></e:EncryptedData>"
                + "<e:EncryptedKey Id=\"EK-top\"><e:CipherData/>"
                + "<e:ReferenceList><e:DataReference URI=\"#ED-body\"/></e:ReferenceList></e:EncryptedKey>"
                + "</w:Security></s:Header>"
                + "<s:Body><e:EncryptedData xmlns:e=\"" + XENC_NS + "\" Id=\"ED-body\"/></s:Body>"
                + "</s:Envelope>";
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should not count a key-transport-only EncryptedKey that has no ReferenceList")
    void shouldNotCountEncryptedKey_whenItHasNoReferenceList() {
        // WSS4J skips an Encrypt result with no data references (WSHandler.checkReceiverResultsAnyOrder).
        String xml = envelope(1, true).replace("</wsse:Security>",
                "<xenc:EncryptedKey Id=\"EK-transport\"><xenc:CipherData/></xenc:EncryptedKey></wsse:Security>");
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    @Test
    @DisplayName("should not count an EncryptedKey whose ReferenceList is wrapped in another element")
    void shouldNotCountEncryptedKey_whenReferenceListIsWrapped() {
        // EncryptedKeyProcessor reads ReferenceList only as a direct child of the key, so a
        // wrapped list decrypts nothing: WSS4J skips the key, and no Encrypt action may be
        // configured for it. No EncryptedData anywhere, so the legacy fallback stays off.
        String xml = envelope(0, false).replace("</wsse:Security>",
                "<xenc:EncryptedKey Id=\"EK-wrapped\"><xenc:CipherData/>"
                + "<wrap><xenc:ReferenceList><xenc:DataReference URI=\"#ED-0\"/></xenc:ReferenceList></wrap>"
                + "</xenc:EncryptedKey></wsse:Security>");
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
    }

    @Test
    @DisplayName("should not treat a DataReference nested below the list's children as a reference")
    void shouldNotCountNestedDataReference_whenItIsNotAListChild() {
        // decryptDataRefs walks the list's direct children only.
        String xml = envelope(0, false).replace("</wsse:Security>",
                "<xenc:EncryptedKey Id=\"EK-deep\"><xenc:CipherData/>"
                + "<xenc:ReferenceList><note><xenc:DataReference URI=\"#ED-0\"/></note></xenc:ReferenceList>"
                + "</xenc:EncryptedKey></wsse:Security>");
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
    }

    @Test
    @DisplayName("should read only the first ReferenceList of an EncryptedKey, as WSS4J does")
    void shouldIgnoreSecondReferenceList_whenKeyHasTwoLists() {
        // getDirectChildElement returns the first list only. The key decrypts the body via that
        // list (one result); the second list's reference to the header EncryptedData is never
        // followed, so WSS4J dispatches that EncryptedData separately (a second result).
        String xml = envelope(0, true).replace("</wsse:Security>",
                "<xenc:EncryptedKey Id=\"EK-two-lists\"><xenc:CipherData/>"
                + "<xenc:ReferenceList><xenc:DataReference URI=\"#ED-0\"/></xenc:ReferenceList>"
                + "<xenc:ReferenceList><xenc:DataReference URI=\"#ED-extra\"/></xenc:ReferenceList>"
                + "</xenc:EncryptedKey>"
                + "<xenc:EncryptedData Id=\"ED-extra\"><ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"/>"
                + "</xenc:EncryptedData></wsse:Security>");
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should count a standalone ReferenceList in the Security header")
    void shouldCountStandaloneReferenceList_whenKeyCarriesNoReferences() {
        String xml = envelope(0, true).replace("</wsse:Security>",
                "<xenc:EncryptedKey Id=\"EK-transport\"><xenc:CipherData/></xenc:EncryptedKey>"
                + "<xenc:ReferenceList><xenc:DataReference URI=\"#ED-0\"/></xenc:ReferenceList></wsse:Security>");
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(1));
    }

    @Test
    @DisplayName("should count a header EncryptedData that no key references, but not one that is referenced")
    void shouldCountUnreferencedHeaderEncryptedData_whenNoKeyReferencesIt() {
        // envelope(3): three keys, and the two attachment EncryptedData in the header are
        // referenced by keys 1 and 2 (removed by WSS4J after decryption, so never dispatched).
        // An extra unreferenced one is dispatched to EncryptedDataProcessor: one more result.
        String xml = envelope(3, true).replace("</wsse:Security>",
                "<xenc:EncryptedData Id=\"ED-extra\"><ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"/>"
                + "</xenc:EncryptedData></wsse:Security>");
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(4));
    }

    @Test
    void shouldIgnoreEmptyReferenceList_whenNoDataIsEncrypted() {
        givenContent(envelope(0, false).replace("</wsse:Security>",
                "<xenc:ReferenceList/></wsse:Security>"));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
    }

    @Test
    void shouldIgnoreNestedDataReference_whenItIsNotAListChild() {
        givenContent(envelope(0, false).replace("</wsse:Security>",
                "<xenc:EncryptedKey><xenc:EncryptionProperties><xenc:DataReference URI=\"#ignored\"/>"
                + "</xenc:EncryptionProperties></xenc:EncryptedKey></wsse:Security>"));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
    }

    @Test
    void shouldRejectExcessiveReferenceLists_whenTheirResultsWouldBeEmpty() {
        givenContent(envelope(0, false).replace("</wsse:Security>",
                "<xenc:ReferenceList/>".repeat(DynamicWSS4JInInterceptor.MAX_ENCRYPTED_KEYS + 1)
                + "</wsse:Security>"));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class).hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
    }

    @Test
    void shouldIgnoreLaterKeyReferenceLists_whenTheFirstListIsEmpty() {
        givenContent(envelope(0, false).replace("</wsse:Security>",
                "<xenc:EncryptedKey><xenc:ReferenceList/>"
                + "<xenc:ReferenceList><xenc:DataReference URI=\"#ignored\"/></xenc:ReferenceList>"
                + "</xenc:EncryptedKey></wsse:Security>"));

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(TS_SIG);
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
    @DisplayName("should reject the message when key-transport-only EncryptedKeys exceed the bound")
    void shouldRejectMessage_whenUncountedEncryptedKeysExceedBound() {
        // A key without a ReferenceList predicts no Encrypt result, but WSS4J still performs an
        // RSA unwrap for each direct key, so the DoS bound must count them too.
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i <= DynamicWSS4JInInterceptor.MAX_ENCRYPTED_KEYS; i++) {
            keys.append("<xenc:EncryptedKey Id=\"EK-transport-").append(i).append("\"><xenc:CipherData/></xenc:EncryptedKey>");
        }
        givenContent(envelope(0, false).replace("</wsse:Security>", keys + "</wsse:Security>"));

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

    // ---------------------------------------------------------------- scan limit and replay cache

    @Test
    @DisplayName("should reject a plain envelope larger than the scan limit without echoing content")
    void shouldRejectMessage_whenPlainEnvelopeExceedsScanLimit() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 256, 0);
        givenContent(envelope(1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasMessageContaining("scan size limit")
                .hasMessageNotContaining("Envelope")
                .hasMessageNotContaining("EK-");
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should accept a plain envelope that exactly fills the scan limit")
    void shouldAcceptEnvelope_whenEntityExactlyFillsScanLimit() {
        String xml = envelope(2, true);
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder,
                xml.getBytes(StandardCharsets.UTF_8).length, 0);
        givenContent(xml);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should reject a MIME package whose root part does not end within the scan limit")
    void shouldRejectMessage_whenMimeRootPartExceedsScanLimit() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 256, 0);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent("--b1\r\nContent-Type: application/xop+xml\r\n\r\n" + envelope(1, true)
                + "\r\n--b1--\r\n");

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasMessageContaining("scan size limit")
                .hasMessageNotContaining("EK-");
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should detect keys and replay every byte when an attachment extends beyond the scan limit")
    void shouldDetectKeysAndReplayEveryByte_whenAttachmentExtendsBeyondScanLimit() throws IOException {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 0);
        byte[] mime = mimeWithBinaryAttachment(3, 256 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent(mime);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(3));
        try (InputStream replay = capturedReplay()) {
            assertThat(replay.readAllBytes()).isEqualTo(mime);
        }
    }

    @Test
    @DisplayName("should delete the spilled cache file once the replay stream is closed")
    void shouldDeleteSpilledTempFile_whenReplayStreamIsClosed() throws IOException {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 1024);
        byte[] mime = mimeWithBinaryAttachment(2, 64 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent(mime);
        givenSpillDirectory();

        interceptor.handleMessage(message);

        InputStream replay = capturedReplay();
        assertThat(spilledFiles()).as("entity above the threshold spills to disk").isNotEmpty();
        assertThat(replay.readAllBytes()).isEqualTo(mime);
        replay.close();
        assertThat(spilledFiles()).isEmpty();
    }

    @Test
    @DisplayName("should delete the spilled cache file when the stream fails part-way through the copy")
    void shouldDeleteSpilledTempFile_whenStreamReadFailsAfterSpill() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 1024);
        byte[] head = mimeWithBinaryAttachment(1, 16 * 1024);
        InputStream failsAfterHead = new SequenceInputStream(new ByteArrayInputStream(head), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        });
        when(message.getContent(InputStream.class)).thenReturn(failsAfterHead);
        givenSpillDirectory();

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
        assertThat(spilledFiles()).isEmpty();
    }

    @Test
    @DisplayName("should close the replay and delete the spilled cache file when MIME location fails")
    void shouldDeleteSpilledTempFile_whenEnvelopeLocationFails() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 1024);
        // The boundary in the Content-Type never occurs in the entity, so locateEnvelope fails.
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=nomatch");
        givenContent(mimeWithBinaryAttachment(1, 16 * 1024));
        givenSpillDirectory();

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
        assertThat(spilledFiles()).isEmpty();
    }

    @Test
    @DisplayName("should close the replay and delete the spilled cache file when the envelope scan fails")
    void shouldDeleteSpilledTempFile_whenEnvelopeScanFails() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 64 * 1024, 1024);
        // Well past the spill threshold, then an unclosed element: scanEnvelope throws.
        String padding = "<!--" + "x".repeat(8 * 1024) + "-->";
        givenContent(padding + "<s:Envelope xmlns:s=\"" + SOAP_NS + "\"><s:Header><unclosed></s:Header></s:Envelope>");
        givenSpillDirectory();

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasCauseInstanceOf(XMLStreamException.class);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
        assertThat(spilledFiles()).isEmpty();
    }

    @Test
    @DisplayName("should close the replay and delete the spilled cache file when WSS4J configuration fails")
    void shouldDeleteSpilledTempFile_whenWssConfigurationThrows() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent(mimeWithBinaryAttachment(2, 64 * 1024));
        when(clientBuilder.newWSSInInterceptorConfiguration())
                .thenThrow(new IllegalStateException("keystore unavailable"));
        givenSpillDirectory();

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
        assertThat(spilledFiles()).isEmpty();
    }

    @Test
    @DisplayName("should close the replay and delete the spilled cache file when adding the WSS4J interceptor fails")
    void shouldDeleteSpilledTempFile_whenChainAddThrows() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent(mimeWithBinaryAttachment(2, 64 * 1024));
        doThrow(new IllegalStateException("chain rejected")).when(chain).add(any(Interceptor.class));
        givenSpillDirectory();

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(message, never()).setContent(eq(InputStream.class), any());
        assertThat(spilledFiles()).isEmpty();
    }

    // ---------------------------------------------------------------- CXF attachment cache settings

    @Test
    @DisplayName("should spill the replay into attachment-directory at attachment-memory-threshold")
    void shouldSpillToAttachmentDirectory_whenAttachmentSettingsConfigured(@TempDir File dir) throws IOException {
        byte[] mime = mimeWithBinaryAttachment(2, 64 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY))
                .thenReturn(dir.getAbsolutePath());
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD))
                .thenReturn("1024");
        givenContent(mime);

        interceptor.handleMessage(message);

        InputStream replay = capturedReplay();
        assertThat(dir.list()).as("entity above the configured threshold spills to the configured directory")
                .isNotEmpty();
        assertThat(replay.readAllBytes()).isEqualTo(mime);
        replay.close();
        assertThat(dir.list()).isEmpty();
        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
    }

    @Test
    @DisplayName("should keep the replay in memory when attachment-memory-threshold exceeds the entity")
    void shouldKeepReplayInMemory_whenAttachmentThresholdExceedsEntity(@TempDir File dir) throws IOException {
        // Larger than CXF's 128 KiB default threshold, so only the configured value keeps it in memory.
        byte[] mime = mimeWithBinaryAttachment(1, 256 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY)).thenReturn(dir);
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD))
                .thenReturn(1024L * 1024L);
        givenContent(mime);

        interceptor.handleMessage(message);

        // The directory is configured, so it is the only place the cache could have spilled.
        assertThat(dir.list()).isEmpty();
        try (InputStream replay = capturedReplay()) {
            assertThat(replay.readAllBytes()).isEqualTo(mime);
        }
    }

    @Test
    @DisplayName("should not apply the per-attachment attachment-max-size to the whole entity")
    void shouldAcceptEntityLargerThanAttachmentMaxSize_forMultiFileDownload() throws IOException {
        byte[] mime = mimeWithBinaryAttachment(3, 64 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MAX_SIZE)).thenReturn(1024L);
        givenContent(mime);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(3));
        try (InputStream replay = capturedReplay()) {
            assertThat(replay.readAllBytes()).isEqualTo(mime);
        }
    }

    @Test
    @DisplayName("should reject the message when attachment-memory-threshold is not a number")
    void shouldRejectMessage_whenAttachmentThresholdIsNotNumeric() {
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD))
                .thenReturn("lots");
        givenContent(envelope(1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(NumberFormatException.class);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative/spill", "does-not-exist"})
    @DisplayName("should reject the message when attachment-directory is relative or does not exist")
    void shouldRejectMessage_whenAttachmentDirectoryIsInvalid(String relativeName, @TempDir File base) {
        // Relative as given, and an absolute path under the temp dir that was never created.
        for (Object configured : new Object[] {relativeName, new File(base, relativeName).getAbsolutePath(),
                new File(base, relativeName)}) {
            Message msg = mock(Message.class);
            when(msg.getInterceptorChain()).thenReturn(chain);
            when(msg.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY)).thenReturn(configured);
            when(msg.getContent(InputStream.class))
                    .thenReturn(new ByteArrayInputStream(envelope(1, true).getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() -> interceptor.handleMessage(msg))
                    .isInstanceOf(Fault.class)
                    .hasMessageContaining("attachment-directory must be an absolute, existing, writable directory");
            verify(msg, never()).setContent(eq(InputStream.class), any());
        }
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should reject the message when attachment-directory names a regular file")
    void shouldRejectMessage_whenAttachmentDirectoryIsAFile(@TempDir File base) throws IOException {
        File notADirectory = new File(base, "spill.txt");
        assertThat(notADirectory.createNewFile()).isTrue();
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY))
                .thenReturn(notADirectory.getAbsolutePath());
        givenContent(envelope(1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
    }

    // ---------------------------------------------------------------- source stream lifecycle

    @Test
    @DisplayName("should close the original response stream once it is copied and hand only the replay downstream")
    void shouldCloseSourceStream_whenDetectionSucceeds() throws IOException {
        byte[] body = envelope(1, true).getBytes(StandardCharsets.UTF_8);
        CountingInputStream source = new CountingInputStream(body);
        when(message.getContent(InputStream.class)).thenReturn(source);

        interceptor.handleMessage(message);

        assertThat(source.closed).isTrue();
        try (InputStream replay = capturedReplay()) {
            assertThat(replay).isNotSameAs(source);
            assertThat(replay.readAllBytes()).isEqualTo(body);
        }
    }

    @Test
    @DisplayName("should close the original response stream when reading it fails")
    void shouldCloseSourceStream_whenReadFails() {
        CountingInputStream source = new CountingInputStream(new byte[0]) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("connection reset");
            }
        };
        when(message.getContent(InputStream.class)).thenReturn(source);

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(IOException.class);
        assertThat(source.closed).isTrue();
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should close the original response stream when the cache configuration is rejected")
    void shouldCloseSourceStream_whenConfigurationIsRejected() {
        CountingInputStream source = new CountingInputStream(envelope(1, true).getBytes(StandardCharsets.UTF_8));
        when(message.getContent(InputStream.class)).thenReturn(source);
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD)).thenReturn("lots");

        assertThatThrownBy(() -> interceptor.handleMessage(message)).isInstanceOf(Fault.class);
        assertThat(source.closed).isTrue();
        assertThat(source.read).as("rejected before any byte is read").isZero();
        assertNoWssInterceptorAdded();
    }

    // ---------------------------------------------------------------- aggregate response cap

    @Test
    @DisplayName("should replay the entity intact when it is under the default response cap")
    void shouldReplayEntityIntact_whenUnderDefaultResponseCap() throws IOException {
        assertThat(DynamicWSS4JInInterceptor.DEFAULT_MAX_RESPONSE_BYTES)
                .as("generous enough for multi-file MCEDT batches").isGreaterThanOrEqualTo(512L * 1024 * 1024);
        byte[] mime = mimeWithBinaryAttachment(3, 256 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        givenContent(mime);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(3));
        try (InputStream replay = capturedReplay()) {
            assertThat(replay.readAllBytes()).isEqualTo(mime);
        }
    }

    @Test
    @DisplayName("should reject an oversized entity, delete the spilled cache and close the source stream")
    void shouldRejectAndDeleteCache_whenEntityExceedsResponseCap() {
        interceptor = new DynamicWSS4JInInterceptor(clientBuilder, 4096, 1024);
        byte[] mime = mimeWithBinaryAttachment(2, 256 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        when(message.getContextualProperty(DynamicWSS4JInInterceptor.RESPONSE_MAX_SIZE)).thenReturn(64L * 1024);
        CountingInputStream source = new CountingInputStream(mime);
        when(message.getContent(InputStream.class)).thenReturn(source);
        givenSpillDirectory();

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasRootCauseInstanceOf(org.apache.cxf.io.CacheSizeExceededException.class)
                .hasMessageContaining(DynamicWSS4JInInterceptor.RESPONSE_MAX_SIZE)
                .hasMessageContaining(String.valueOf(64 * 1024))
                .hasMessageNotContaining("EK-");
        assertThat(source.read).as("copy stops at the cap instead of draining the entity")
                .isLessThan(mime.length);
        assertThat(source.closed).isTrue();
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
        assertThat(spilledFiles()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"number", "string"})
    @DisplayName("should honour a configured response cap given as a Number or a String")
    void shouldHonourConfiguredResponseCap_forNumberOrStringValue(String form) {
        byte[] mime = mimeWithBinaryAttachment(1, 8 * 1024);
        long cap = mime.length - 1L;
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        when(message.getContextualProperty(DynamicWSS4JInInterceptor.RESPONSE_MAX_SIZE))
                .thenReturn("number".equals(form) ? (Object) cap : " " + cap + " ");
        givenContent(mime);

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasMessageContaining("limit of " + cap + " bytes");
        assertNoWssInterceptorAdded();
    }

    @Test
    @DisplayName("should accept an entity that is exactly the configured response cap")
    void shouldAcceptEntity_whenExactlyAtResponseCap() throws IOException {
        byte[] mime = mimeWithBinaryAttachment(2, 8 * 1024);
        when(message.get(Message.CONTENT_TYPE)).thenReturn("multipart/related; boundary=b1");
        when(message.getContextualProperty(DynamicWSS4JInInterceptor.RESPONSE_MAX_SIZE))
                .thenReturn(String.valueOf(mime.length));
        givenContent(mime);

        interceptor.handleMessage(message);

        assertThat(wssProps.get(WSHandlerConstants.ACTION)).isEqualTo(expectedAction(2));
        try (InputStream replay = capturedReplay()) {
            assertThat(replay.readAllBytes()).isEqualTo(mime);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "big"})
    @DisplayName("should reject the message when the configured response cap is not a positive number")
    void shouldRejectMessage_whenResponseCapIsInvalid(String configured) {
        when(message.getContextualProperty(DynamicWSS4JInInterceptor.RESPONSE_MAX_SIZE)).thenReturn(configured);
        givenContent(envelope(1, true));

        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class)
                .hasMessageContaining(DynamicWSS4JInInterceptor.RESPONSE_MAX_SIZE);
        assertNoWssInterceptorAdded();
        verify(message, never()).setContent(eq(InputStream.class), any());
    }

    // ---------------------------------------------------------------- helpers

    private void givenContent(String content) {
        when(message.getContent(InputStream.class))
                .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void givenContent(byte[] content) {
        when(message.getContent(InputStream.class)).thenReturn(new ByteArrayInputStream(content));
    }

    private InputStream capturedReplay() {
        ArgumentCaptor<InputStream> restored = ArgumentCaptor.forClass(InputStream.class);
        verify(message).setContent(eq(InputStream.class), restored.capture());
        return restored.getValue();
    }

    /**
     * Points the replay cache's spill directory at this test's own {@link TempDir}, so temp-file
     * assertions never see cache files created by other tests or parallel Surefire forks in the
     * shared JVM temp directory.
     */
    private void givenSpillDirectory() {
        when(message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY))
                .thenReturn(spillDir.getAbsolutePath());
    }

    /** Files currently in this test's spill directory. */
    private String[] spilledFiles() {
        String[] names = spillDir.list();
        return names == null ? new String[0] : names;
    }

    /**
     * Builds an MTOM-shaped package: a small root part with {@code keys} EncryptedKey elements,
     * then one binary attachment of {@code attachmentSize} pseudo-random bytes (synthetic
     * ciphertext stand-in, no real data), then the close delimiter.
     */
    private static byte[] mimeWithBinaryAttachment(int keys, int attachmentSize) {
        byte[] attachment = new byte[attachmentSize];
        new Random(3868).nextBytes(attachment);
        byte[] head = ("--b1\r\nContent-Type: application/xop+xml\r\nContent-ID: <root>\r\n\r\n"
                + envelope(keys, true)
                + "\r\n--b1\r\nContent-Type: application/octet-stream\r\nContent-ID: <att1>\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\r\n--b1--\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] all = Arrays.copyOf(head, head.length + attachment.length + tail.length);
        System.arraycopy(attachment, 0, all, head.length, attachment.length);
        System.arraycopy(tail, 0, all, head.length + attachment.length, tail.length);
        return all;
    }

    /**
     * Source stream that records how many bytes were read and whether it was closed. It extends
     * {@link InputStream} directly, not {@link ByteArrayInputStream}, so the interceptor's copy
     * goes through the default chunked {@code transferTo}, as it does for a real HTTP stream.
     */
    private static class CountingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private long read;
        private boolean closed;

        CountingInputStream(byte[] content) {
            delegate = new ByteArrayInputStream(content);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                read += n;
            }
            return n;
        }

        @Override
        public int read() {
            int b = delegate.read();
            if (b >= 0) {
                read++;
            }
            return b;
        }

        @Override
        public void close() {
            closed = true;
        }
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
     * header (the first for the body, the rest for attachments), each with a ReferenceList to its
     * EncryptedData; the attachments' EncryptedData sit in the Security header (SwA profile) and
     * the body's in the Body, when requested. No real data.
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
                    .append("<xenc:ReferenceList><xenc:DataReference URI=\"#ED-").append(i)
                    .append("\"/></xenc:ReferenceList>")
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
