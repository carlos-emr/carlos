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

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.WSHandler;
import org.apache.wss4j.dom.message.WSSecEncrypt;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecTimestamp;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Compares the prediction with real WSS4J decryption and receiver-action validation. */
@Tag("unit")
@Tag("mcedt")
class DynamicWSS4JEncryptionResultsUnitTest {
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String FIXTURE_NS = "urn:carlos:encryption-test";
    private static final String ALIAS = "receiver";
    private static final String PASSWORD = "test-only-password";
    private static Merlin crypto;

    @BeforeAll
    static void createTestKey() throws Exception {
        WSSConfig.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var subject = new X500Name("CN=CARLOS synthetic encryption test");
        Instant now = Instant.now();
        var certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                        Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(86400)),
                        subject, pair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry(ALIAS, pair.getPrivate(), PASSWORD.toCharArray(),
                new java.security.cert.Certificate[]{certificate});
        crypto = new Merlin();
        crypto.setKeyStore(store);
        crypto.setTrustStore(store);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 20})
    void shouldCountDirectDataOnce_whenLaterListsReferenceAlreadyProcessedData(int lists) throws Exception {
        Document doc = encryptedEnvelope(true, false);
        Element security = security(doc);
        Node references = detachReferences(doc);
        security.appendChild(encryptedHeader(doc));
        for (int i = 0; i < lists; i++) {
            security.appendChild(references.cloneNode(true));
        }

        assertMatchesWss4j(doc, 1);
    }

    @Test
    void shouldCountReferenceListOnce_whenItPrecedesEncryptedHeaderData() throws Exception {
        Document doc = encryptedEnvelope(true, false);
        Element security = security(doc);
        security.appendChild(detachReferences(doc));
        security.appendChild(encryptedHeader(doc));

        assertMatchesWss4j(doc, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 20})
    void shouldIgnoreEmptyListResults_whenBodyWasDecryptedByKey(int lists) throws Exception {
        Document doc = encryptedEnvelope(false, true);
        Node references = doc.getElementsByTagNameNS(WSConstants.ENC_NS, "ReferenceList").item(0);
        for (int i = 0; i < lists; i++) {
            security(doc).appendChild(references.cloneNode(false));
        }

        assertMatchesWss4j(doc, 1);
    }

    @Test
    void shouldCountListResult_whenItAlsoDecryptsUnprocessedBodyData() throws Exception {
        Document doc = encryptedEnvelope(true, true);
        Node references = detachReferences(doc);
        security(doc).appendChild(encryptedHeader(doc));
        security(doc).appendChild(references);

        assertMatchesWss4j(doc, 2);
    }

    private static Document encryptedEnvelope(boolean encryptHeader, boolean encryptBody) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Fixed local fixture only; no external XML or keystore is read by this test.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        String xml = "<s:Envelope xmlns:s='" + SOAP_NS + "'><s:Header>"
                + "<header xmlns='" + FIXTURE_NS + "'>synthetic header</header></s:Header>"
                + "<s:Body><body xmlns='" + FIXTURE_NS + "'>synthetic body</body></s:Body></s:Envelope>";
        Document doc = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        WSSecHeader header = new WSSecHeader(doc);
        header.insertSecurityHeader();
        WSSecEncrypt encrypt = new WSSecEncrypt(header);
        encrypt.setUserInfo(ALIAS);
        encrypt.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
        encrypt.setSymmetricEncAlgorithm(WSConstants.AES_128);
        if (encryptHeader) {
            encrypt.getParts().add(new WSEncryptionPart("header", FIXTURE_NS, "Element"));
        }
        if (encryptBody) {
            encrypt.getParts().add(new WSEncryptionPart("Body", SOAP_NS, "Content"));
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        encrypt.build(crypto, generator.generateKey());
        return doc;
    }

    private static Element security(Document doc) {
        return (Element) doc.getElementsByTagNameNS(WSConstants.WSSE_NS, "Security").item(0);
    }

    private static Node encryptedHeader(Document doc) {
        Node soapHeader = doc.getElementsByTagNameNS(SOAP_NS, "Header").item(0);
        for (Node child = soapHeader.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (WSConstants.ENC_NS.equals(child.getNamespaceURI()) && "EncryptedData".equals(child.getLocalName())) {
                return child;
            }
        }
        throw new AssertionError("Encrypted fixture header is missing");
    }

    private static Node detachReferences(Document doc) {
        Node references = doc.getElementsByTagNameNS(WSConstants.ENC_NS, "ReferenceList").item(0);
        references.getParentNode().removeChild(references);
        return references;
    }

    private static void assertMatchesWss4j(Document doc, int expectedEncryptions) throws Exception {
        WSSecHeader header = new WSSecHeader(doc);
        header.insertSecurityHeader();
        new WSSecTimestamp(header).build();
        WSSecSignature signature = new WSSecSignature(header);
        signature.setUserInfo(ALIAS, PASSWORD);
        signature.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
        signature.build(crypto);
        StringWriter wire = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(wire));
        var prediction = DynamicWSS4JInInterceptor.scanEnvelope(
                new ByteArrayInputStream(wire.toString().getBytes(StandardCharsets.UTF_8)));

        var results = new WSSecurityEngine().processSecurityHeader(doc, null, callbacks -> {
            for (var callback : callbacks) {
                ((WSPasswordCallback) callback).setPassword(PASSWORD);
            }
        }, crypto).getResults();
        long encryptions = results.stream()
                .filter(result -> Integer.valueOf(WSConstants.ENCR).equals(result.get(WSSecurityEngineResult.TAG_ACTION)))
                .filter(result -> result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS) instanceof List<?> refs && !refs.isEmpty())
                .count();
        assertThat(encryptions).isEqualTo(expectedEncryptions);
        assertThat(doc.getElementsByTagNameNS(FIXTURE_NS, "header").item(0).getTextContent()).isEqualTo("synthetic header");
        assertThat(doc.getElementsByTagNameNS(FIXTURE_NS, "body").item(0).getTextContent()).isEqualTo("synthetic body");
        assertThat(prediction.encryptCount).isEqualTo(expectedEncryptions);
        assertThat(new ActionChecker().matches(results, prediction.encryptCount)).isTrue();
    }

    private static final class ActionChecker extends WSHandler {
        private boolean matches(List<WSSecurityEngineResult> results, int encryptions) {
            var actions = new ArrayList<Integer>(List.of(WSConstants.TS, WSConstants.SIGN));
            actions.addAll(Collections.nCopies(encryptions, WSConstants.ENCR));
            return checkReceiverResultsAnyOrder(results, actions);
        }

        @Override public Object getOption(String key) { return null; }
        @Override public Object getProperty(Object context, String key) { return null; }
        @Override public void setProperty(Object context, String key, Object value) { }
        @Override public String getPassword(Object context) { return null; }
        @Override public void setPassword(Object context, String value) { }
    }
}
