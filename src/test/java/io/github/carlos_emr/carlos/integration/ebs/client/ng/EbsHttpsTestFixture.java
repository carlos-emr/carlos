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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.KeyGenerator;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.xml.bind.JAXBContext;
import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.Attachment;
import org.apache.wss4j.common.ext.AttachmentRequestCallback;
import org.apache.wss4j.common.ext.AttachmentResultCallback;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.WSDataRef;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.RequestData;
import org.apache.wss4j.dom.message.WSSecEncrypt;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecTimestamp;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ca.ontario.health.edt.CommonResult;
import ca.ontario.health.edt.DownloadData;
import ca.ontario.health.edt.DownloadResponse;
import ca.ontario.health.edt.DownloadResult;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.ResponseResult;
import ca.ontario.health.edt.UploadResponse;
import ca.ontario.health.hcv.HcvResults;
import ca.ontario.health.hcv.ValidateResponse;

/** Independent loopback SOAP peer: real WSS4J verification, signing, encryption and MIME. */
final class EbsHttpsTestFixture implements AutoCloseable {
    static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String EBS = "http://ebs.health.ontario.ca/";
    static final String IDP = "http://idp.ebs.health.ontario.ca/";
    static final String PASSWORD = "synthetic-keystore-password";
    static final String TOKEN_PASSWORD = "synthetic-token-password";
    static final String AUDIT = "synthetic-audit";
    static final String ATTACHMENT_ID = "synthetic-download";
    static final byte[] PAYLOAD = new byte[32 * 1024];
    static {
        for (int i = 0; i < PAYLOAD.length; i++) {
            PAYLOAD[i] = (byte) (i * 31 + 7);
        }
    }

    enum Mode { NORMAL, UNSIGNED, TAMPERED_SIGNATURE, UNTRUSTED_SIGNER, EXPIRED,
        STALLED_HEADERS, STALLED_BODY, PROGRESSING_BODY, TRUNCATED, TAMPERED_ATTACHMENT,
        SOAP_FAULT, ENCRYPTED_SOAP_FAULT }

    record Keys(Path clientProperties, KeyManager[] clientIdentity, TrustManager[] trust,
                SSLContext tlsServer, Merlin serverCrypto, Merlin rogueCrypto) { }
    record Request(String operation, boolean multipart, List<byte[]> attachments, Set<QName> signedParts) { }
    private record Wire(byte[] bytes, String contentType, boolean encryptedAttachment) { }
    private record Identity(KeyPair pair, X509Certificate certificate) { }

    private final Keys keys;
    private final HttpsServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final JAXBContext binding;
    private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    final AtomicInteger activeRequests = new AtomicInteger();
    volatile Mode mode = Mode.NORMAL;
    volatile boolean sentEncryptedAttachment;
    volatile String negotiatedProtocol;

    EbsHttpsTestFixture(Keys keys, boolean requireClientIdentity) throws Exception {
        this.keys = keys;
        binding = JAXBContext.newInstance(ca.ontario.health.edt.ObjectFactory.class,
                ca.ontario.health.hcv.ObjectFactory.class);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(keys.tlsServer()) {
            @Override public void configure(HttpsParameters parameters) {
                SSLParameters ssl = getSSLContext().getDefaultSSLParameters();
                ssl.setNeedClientAuth(requireClientIdentity);
                parameters.setSSLParameters(ssl);
            }
        });
        server.setExecutor(workers);
        server.createContext("/", this::handle);
        server.start();
    }

    String address() { return "https://localhost:" + server.getAddress().getPort(); }
    Keys keys() { return keys; }

    void assertHealthy() {
        if (!failures.isEmpty()) {
            throw new AssertionError("Synthetic EBS peer failed", failures.peek());
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        activeRequests.incrementAndGet();
        boolean writing = false;
        try (exchange) {
            if (exchange.getRequestURI().getPath().equals("/control")) {
                byte[] body = "control-ok".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                writing = true;
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                return;
            }
            Mode responseMode = mode;
            byte[] input = exchange.getRequestBody().readNBytes(2 * 1024 * 1024 + 1);
            if (input.length > 2 * 1024 * 1024) {
                throw new IOException("Synthetic EBS request exceeds fixture limit");
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            negotiatedProtocol = ((HttpsExchange) exchange).getSSLSession().getProtocol();
            Request request = verifyRequest(input, contentType);
            requests.add(request);
            Wire reply = response(request.operation(), responseMode);
            sentEncryptedAttachment = reply.encryptedAttachment();
            writing = true;
            if (responseMode == Mode.STALLED_HEADERS) {
                pause(1500);
            }
            exchange.getResponseHeaders().set("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(isSoapFault(responseMode) ? 500 : 200, reply.bytes().length);
            if (responseMode == Mode.TRUNCATED) {
                exchange.getResponseBody().write(reply.bytes(), 0, reply.bytes().length / 2);
            } else if (responseMode == Mode.STALLED_BODY) {
                int first = Math.min(4096, reply.bytes().length - 1);
                exchange.getResponseBody().write(reply.bytes(), 0, first);
                exchange.getResponseBody().flush();
                pause(1500);
                exchange.getResponseBody().write(reply.bytes(), first, reply.bytes().length - first);
            } else if (responseMode == Mode.PROGRESSING_BODY) {
                for (int offset = 0; offset < reply.bytes().length; offset += 4096) {
                    exchange.getResponseBody().write(reply.bytes(), offset, Math.min(4096, reply.bytes().length - offset));
                    exchange.getResponseBody().flush();
                    pause(250);
                }
            } else {
                exchange.getResponseBody().write(reply.bytes());
            }
        } catch (Exception e) {
            // Timeouts/truncation deliberately close a live response; do not hide peer setup errors.
            if (!writing) failures.add(e);
        } finally {
            activeRequests.decrementAndGet();
        }
    }

    private Request verifyRequest(byte[] input, String contentType) throws Exception {
        boolean multipart = contentType != null && contentType.startsWith("multipart/");
        List<byte[]> binaries = new ArrayList<>();
        Map<String, Attachment> attachments = new LinkedHashMap<>();
        byte[] envelope = input;
        if (multipart) {
            MimeMultipart mime = new MimeMultipart(new ByteArrayDataSource(input, contentType));
            envelope = mime.getBodyPart(0).getInputStream().readAllBytes();
            for (int i = 1; i < mime.getCount(); i++) {
                var part = mime.getBodyPart(i);
                byte[] data = part.getInputStream().readAllBytes();
                binaries.add(data);
                Attachment attachment = new Attachment();
                String id = part.getHeader("Content-ID")[0].replace("<", "").replace(">", "");
                attachment.setId(id);
                attachment.setMimeType(part.getContentType());
                attachment.setSourceStream(new ByteArrayInputStream(data));
                attachments.put(id, attachment);
            }
        }
        Document document = parse(envelope);
        RequestData data = new RequestData();
        data.setSigVerCrypto(keys.serverCrypto());
        data.setDecCrypto(keys.serverCrypto());
        data.setExpandXopInclude(false);
        data.setAttachmentCallbackHandler(new Attachments(attachments));
        data.setCallbackHandler(callbacks -> {
            for (Callback callback : callbacks) {
                WSPasswordCallback password = (WSPasswordCallback) callback;
                password.setPassword(password.getUsage() == WSPasswordCallback.USERNAME_TOKEN
                        ? TOKEN_PASSWORD : PASSWORD);
            }
        });
        var verified = new WSSecurityEngine().processSecurityHeader(document, data).getResults();
        Set<QName> signed = new java.util.HashSet<>();
        boolean username = false;
        for (var result : verified) {
            if (Integer.valueOf(WSConstants.UT).equals(result.get(WSSecurityEngineResult.TAG_ACTION))) {
                username = true;
            }
            if (Integer.valueOf(WSConstants.SIGN).equals(result.get(WSSecurityEngineResult.TAG_ACTION))
                    && result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS) instanceof List<?> refs) {
                for (Object ref : refs) signed.add(((WSDataRef) ref).getName());
            }
        }
        Set<QName> required = Set.of(new QName(SOAP, "Body"), new QName(WSConstants.WSU_NS, "Timestamp"),
                new QName(EBS, "EBS"), new QName(IDP, "IDP"));
        if (!username || !signed.containsAll(required)) {
            throw new IOException("Synthetic EBS request is not authenticated and signed as required");
        }
        if (!"fixture-conformance".equals(text(document, "", "SoftwareConformanceKey"))
                || !"fixture-audit".equals(text(document, "", "AuditId"))
                || !"fixture-service".equals(text(document, "", "ServiceUserMUID"))) {
            throw new IOException("Synthetic EBS/IDP headers do not match the fixture contract");
        }
        Element body = (Element) document.getElementsByTagNameNS(SOAP, "Body").item(0);
        Element operation = null;
        for (var node = body.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) { operation = element; break; }
        }
        if (operation == null) throw new IOException("Synthetic EBS request has no operation");
        return new Request(operation.getLocalName(), multipart, binaries, Set.copyOf(signed));
    }

    private Wire response(String operation, Mode responseMode) throws Exception {
        Document doc = parse(("<s:Envelope xmlns:s='" + SOAP + "'><s:Header/><s:Body/></s:Envelope>")
                .getBytes(StandardCharsets.UTF_8));
        Element body = (Element) doc.getElementsByTagNameNS(SOAP, "Body").item(0);
        Object value;
        Map<String, Attachment> attachments = new LinkedHashMap<>();
        if (isSoapFault(responseMode)) {
            Element fault = doc.createElementNS(SOAP, "s:Fault");
            Element code = doc.createElementNS(null, "faultcode"); code.setTextContent("s:Server");
            Element description = doc.createElementNS(null, "faultstring"); description.setTextContent("synthetic service fault");
            fault.appendChild(code); fault.appendChild(description); body.appendChild(fault);
            value = null;
        } else if (operation.equals("validate")) {
            HcvResults result = new HcvResults(); result.setAuditUID(AUDIT);
            ValidateResponse response = new ValidateResponse(); response.setResults(result);
            value = new ca.ontario.health.hcv.ObjectFactory().createValidateResponse(response);
        } else if (operation.equals("upload")) {
            ResourceResult result = new ResourceResult(); result.setAuditID(AUDIT);
            ResponseResult resource = new ResponseResult(); resource.setResourceID(BigInteger.ONE);
            CommonResult common = new CommonResult(); common.setCode("SUCCESS"); common.setMsg("synthetic");
            resource.setResult(common); result.getResponse().add(resource);
            UploadResponse response = new UploadResponse(); response.setReturn(result);
            value = new ca.ontario.health.edt.ObjectFactory().createUploadResponse(response);
        } else if (operation.equals("download")) {
            DownloadData data = new DownloadData(); data.setResourceID(BigInteger.ONE);
            data.setResourceType("fixture"); data.setDescription("synthetic"); data.setContent(PAYLOAD);
            DownloadResult result = new DownloadResult(); result.setAuditID(AUDIT); result.getData().add(data);
            DownloadResponse response = new DownloadResponse(); response.setReturn(result);
            value = new ca.ontario.health.edt.ObjectFactory().createDownloadResponse(response);
        } else throw new IOException("Unexpected synthetic EBS operation");
        if (value != null) binding.createMarshaller().marshal(value, body);
        if (operation.equals("download")) {
            Element content;
            if (isSoapFault(responseMode)) {
                Element fault = (Element) doc.getElementsByTagNameNS(SOAP, "Fault").item(0);
                Element detail = doc.createElementNS(null, "detail");
                content = doc.createElementNS("urn:synthetic:fault", "fixture:content");
                content.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:fixture", "urn:synthetic:fault");
                detail.appendChild(content); fault.appendChild(detail);
            } else {
                content = (Element) doc.getElementsByTagName("content").item(0);
            }
            content.setTextContent("");
            Element xop = doc.createElementNS("http://www.w3.org/2004/08/xop/include", "xop:Include");
            xop.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xop",
                    "http://www.w3.org/2004/08/xop/include");
            xop.setAttribute("href", "cid:" + ATTACHMENT_ID); content.appendChild(xop);
            Attachment attachment = new Attachment(); attachment.setId(ATTACHMENT_ID);
            attachment.setMimeType("application/octet-stream");
            attachment.addHeaders(Map.of("Content-ID", "<" + ATTACHMENT_ID + ">"));
            attachment.setSourceStream(new ByteArrayInputStream(PAYLOAD));
            attachments.put(ATTACHMENT_ID, attachment);
        }
        if (responseMode != Mode.UNSIGNED) {
            WSSecHeader security = new WSSecHeader(doc); security.insertSecurityHeader();
            WSSecTimestamp timestamp = new WSSecTimestamp(security);
            if (responseMode == Mode.EXPIRED) timestamp.setWsTimeSource(() -> Instant.now().minusSeconds(900));
            timestamp.build();
            Attachments callback = new Attachments(attachments);
            WSSecSignature signature = new WSSecSignature(security);
            signature.setUserInfo(responseMode == Mode.UNTRUSTED_SIGNER ? "rogue" : "server", PASSWORD);
            signature.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
            signature.getParts().add(new WSEncryptionPart("Body", SOAP, "Element"));
            signature.getParts().add(new WSEncryptionPart("Timestamp", WSConstants.WSU_NS, "Element"));
            if (!attachments.isEmpty()) {
                signature.getParts().add(new WSEncryptionPart("cid:Attachments", "Content"));
                signature.setAttachmentCallbackHandler(callback);
            }
            signature.build(responseMode == Mode.UNTRUSTED_SIGNER ? keys.rogueCrypto() : keys.serverCrypto());
            if (responseMode != Mode.SOAP_FAULT) {
                WSSecEncrypt encrypt = new WSSecEncrypt(security); encrypt.setUserInfo("client");
                encrypt.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
                encrypt.setKeyEncAlgo(WSConstants.KEYTRANSPORT_RSA15);
                encrypt.setSymmetricEncAlgorithm(WSConstants.AES_128);
                encrypt.getParts().add(new WSEncryptionPart("Body", SOAP, "Content"));
                if (!attachments.isEmpty()) {
                    encrypt.getParts().add(new WSEncryptionPart("cid:Attachments", "Content"));
                    encrypt.setAttachmentCallbackHandler(callback);
                }
                KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(128);
                encrypt.build(keys.serverCrypto(), generator.generateKey());
            }
            if (responseMode == Mode.TAMPERED_SIGNATURE) {
                Element digest = (Element) doc.getElementsByTagNameNS(WSConstants.SIG_NS, "DigestValue").item(0);
                String original = digest.getTextContent();
                digest.setTextContent((original.startsWith("A") ? "B" : "A") + original.substring(1));
            }
        }
        byte[] envelope = serialize(doc);
        if (attachments.isEmpty()) return new Wire(envelope, "text/xml; charset=UTF-8", false);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        String boundary = "carlos-synthetic-ebs";
        write(wire, "--" + boundary + "\r\nContent-Type: application/xop+xml; charset=UTF-8; type=\"text/xml\""
                + "\r\nContent-ID: <root>\r\nContent-Transfer-Encoding: binary\r\n\r\n");
        wire.write(envelope);
        boolean encrypted = false;
        for (Attachment attachment : attachments.values()) {
            byte[] bytes = attachment.getSourceStream().readAllBytes();
            encrypted |= !Arrays.equals(bytes, PAYLOAD);
            if (responseMode == Mode.TAMPERED_ATTACHMENT) bytes[bytes.length / 2] ^= 1;
            write(wire, "\r\n--" + boundary + "\r\nContent-Type: " + attachment.getMimeType()
                    + "\r\nContent-ID: <" + attachment.getId() + ">\r\nContent-Transfer-Encoding: binary\r\n\r\n");
            wire.write(bytes);
        }
        write(wire, "\r\n--" + boundary + "--\r\n");
        return new Wire(wire.toByteArray(), "multipart/related; boundary=\"" + boundary
                + "\"; type=\"application/xop+xml\"; start=\"<root>\"; start-info=\"text/xml\"", encrypted);
    }

    private static boolean isSoapFault(Mode mode) {
        return mode == Mode.SOAP_FAULT || mode == Mode.ENCRYPTED_SOAP_FAULT;
    }

    private static final class Attachments implements CallbackHandler {
        private final Map<String, Attachment> attachments;
        Attachments(Map<String, Attachment> attachments) { this.attachments = attachments; }
        @Override public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof AttachmentRequestCallback request) {
                    String id = request.getAttachmentId();
                    request.setAttachments(id == null || id.equals("Attachments")
                            ? new ArrayList<>(attachments.values())
                            : attachments.containsKey(id) ? List.of(attachments.get(id)) : List.of());
                } else if (callback instanceof AttachmentResultCallback result) {
                    attachments.put(result.getAttachmentId(), result.getAttachment());
                } else throw new UnsupportedCallbackException(callback);
            }
        }
    }

    static Keys createKeys(Path directory) throws Exception {
        WSSConfig.init();
        Identity client = identity("synthetic-client", false), server = identity("localhost", true);
        Identity rogue = identity("synthetic-untrusted", false);
        KeyStore clientStore = store("client", client, "server", server);
        KeyStore serverStore = store("server", server, "client", client);
        KeyStore rogueStore = store("rogue", rogue, "client", client);
        Path clientPath = directory.resolve("client.p12");
        try (var output = Files.newOutputStream(clientPath)) { clientStore.store(output, PASSWORD.toCharArray()); }
        Properties properties = new Properties();
        properties.setProperty("org.apache.wss4j.crypto.provider", Merlin.class.getName());
        String prefix = "org.apache.wss4j.crypto.merlin.";
        for (String part : List.of("keystore", "truststore")) {
            properties.setProperty(prefix + part + ".type", "PKCS12");
            properties.setProperty(prefix + part + ".password", PASSWORD);
            properties.setProperty(prefix + part + ".file", clientPath.toUri().toString());
        }
        Path props = directory.resolve("client.properties");
        try (var output = Files.newOutputStream(props)) { properties.store(output, "Synthetic test keys only"); }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(clientStore);
        SSLContext tls = SSLContext.getInstance("TLS");
        TrustManagerFactory serverTrust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        serverTrust.init(serverStore);
        tls.init(keyManagers(serverStore), serverTrust.getTrustManagers(), null);
        Merlin serverCrypto = new Merlin(); serverCrypto.setKeyStore(serverStore); serverCrypto.setTrustStore(serverStore);
        Merlin rogueCrypto = new Merlin(); rogueCrypto.setKeyStore(rogueStore); rogueCrypto.setTrustStore(rogueStore);
        return new Keys(props, keyManagers(clientStore), trust.getTrustManagers(), tls, serverCrypto, rogueCrypto);
    }

    private static Identity identity(String name, boolean localhost) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair(); Instant now = Instant.now();
        X500Name subject = new X500Name("CN=" + name);
        var builder = new JcaX509v3CertificateBuilder(subject, new BigInteger(80, new java.security.SecureRandom()),
                Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(86400)), subject, pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        if (localhost) builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        return new Identity(pair, certificate);
    }

    private static KeyStore store(String alias, Identity owner, String peerAlias, Identity peer) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, null);
        store.setKeyEntry(alias, owner.pair().getPrivate(), PASSWORD.toCharArray(),
                new java.security.cert.Certificate[]{owner.certificate()});
        store.setCertificateEntry(peerAlias, peer.certificate()); return store;
    }

    private static KeyManager[] keyManagers(KeyStore store) throws Exception {
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, PASSWORD.toCharArray()); return factory.getKeyManagers();
    }

    private static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private static byte[] serialize(Document doc) throws Exception {
        TransformerFactory factory = TransformerFactory.newDefaultInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        factory.newTransformer().transform(new DOMSource(doc), new StreamResult(output)); return output.toByteArray();
    }

    private static String text(Document doc, String namespace, String name) {
        var node = doc.getElementsByTagNameNS(namespace, name).item(0);
        return node == null ? null : node.getTextContent();
    }

    private static void write(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Deliberate wire delay: exercise idle timeouts and responses that keep making progress. */
    @SuppressWarnings("java:S2925") // A simulated slow peer must delay actual network bytes.
    private static void pause(long milliseconds) throws InterruptedException {
        Thread.sleep(milliseconds);
    }

    @Override public void close() {
        server.stop(0); workers.shutdownNow();
    }
}
