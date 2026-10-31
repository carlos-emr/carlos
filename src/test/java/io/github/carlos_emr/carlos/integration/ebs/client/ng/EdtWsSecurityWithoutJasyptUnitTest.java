/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.crypto.KeyGenerator;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.xml.ws.BindingProvider;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.engine.WSSConfig;
import org.apache.wss4j.dom.engine.WSSecurityEngine;
import org.apache.wss4j.dom.handler.RequestData;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the EBS Crypto-loading paths, including the legacy property prefix. */
@Tag("unit")
@Tag("mcedt")
class EdtWsSecurityWithoutJasyptUnitTest {
    private static final String ALIAS = "synthetic-ebs";
    private static final String PASSWORD = "synthetic-keystore-password";
    @TempDir
    static Path directory;
    private static Path keystore;

    @BeforeAll
    static void createKeystore() throws Exception {
        WSSConfig.init();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var subject = new X500Name("CN=Synthetic EBS fixture");
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
        keystore = directory.resolve("synthetic.p12");
        try (OutputStream out = Files.newOutputStream(keystore)) {
            store.store(out, PASSWORD.toCharArray());
        }
    }

    @Test
    void shouldExcludeJasypt_fromRuntimeClasspath() {
        assertThatThrownBy(() -> Class.forName("org.jasypt.encryption.pbe.StandardPBEStringEncryptor"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {Merlin.OLD_PREFIX, Merlin.PREFIX})
    void shouldSignAndDecrypt_withProductionCryptoConfiguration(String prefix) throws Exception {
        EdtClientBuilder builder = builder(prefix, PASSWORD);
        var outbound = new WSS4JOutInterceptor(builder.newWSSOutInterceptorConfiguration());
        Crypto signing = outbound.loadSignatureCrypto(request(builder));
        var inbound = new CryptoLoadingInInterceptor(builder.newWSSInInterceptorConfiguration());
        Crypto decrypting = inbound.loadDecryptionCrypto(request(builder));

        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                ("<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>"
                        + "<s:Header/><s:Body><result xmlns='urn:synthetic-ebs'>ok</result>"
                        + "</s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8)));
        var header = new WSSecHeader(document);
        header.insertSecurityHeader();
        new WSSecTimestamp(header).build();
        var signature = new WSSecSignature(header);
        signature.setUserInfo(ALIAS, PASSWORD);
        signature.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
        signature.build(signing);
        var encryption = new WSSecEncrypt(header);
        encryption.setUserInfo(ALIAS);
        encryption.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
        encryption.setSymmetricEncAlgorithm(WSConstants.AES_128);
        encryption.setKeyEncAlgo(WSConstants.KEYTRANSPORT_RSA15);
        var keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        encryption.build(signing, keyGenerator.generateKey());

        RequestData receiving = request(builder);
        receiving.setSigVerCrypto(decrypting);
        receiving.setDecCrypto(decrypting);
        receiving.setAllowRSA15KeyTransportAlgorithm(true);
        var results = new WSSecurityEngine().processSecurityHeader(document, receiving);
        assertThat(results.getActionResults()).containsKeys(WSConstants.TS, WSConstants.SIGN, WSConstants.ENCR);
        assertThat(document.getElementsByTagNameNS("urn:synthetic-ebs", "result").item(0).getTextContent())
                .isEqualTo("ok");
    }

    @Test
    void shouldConfigureContextEncryptor_whenBuildingEbsProxy() throws Exception {
        EdtClientBuilder builder = builder(Merlin.PREFIX, PASSWORD);
        builder.getConfig().setServiceUrl("http://localhost:1/synthetic-ebs");
        builder.getConfig().setConformanceKey("synthetic-conformance");
        builder.getConfig().setServiceId("synthetic-service");
        SyntheticService proxy = builder.build(SyntheticService.class);
        try {
            assertThat(((BindingProvider) proxy).getRequestContext())
                    .containsEntry(SecurityConstants.PASSWORD_ENCRYPTOR_INSTANCE,
                            PlaintextCryptoPasswordEncryptor.INSTANCE);
        } finally {
            ClientProxy.getClient(proxy).destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectEncryptedProperties_withoutExposingCiphertext(boolean inbound) throws Exception {
        String ciphertext = "synthetic-secret-ciphertext";
        EdtClientBuilder builder = builder(Merlin.PREFIX, "ENC(" + ciphertext + ")");
        assertThatThrownBy(() -> {
            if (inbound) {
                new CryptoLoadingInInterceptor(builder.newWSSInInterceptorConfiguration())
                        .loadDecryptionCrypto(request(builder));
            } else {
                new WSS4JOutInterceptor(builder.newWSSOutInterceptorConfiguration())
                        .loadSignatureCrypto(request(builder));
            }
        }).hasRootCauseInstanceOf(UnsupportedOperationException.class)
                .hasStackTraceContaining("deployment-managed Crypto properties")
                .hasStackTraceContaining("ebs-without-jasypt.md")
                .hasStackTraceContaining("ENC(...)")
                .satisfies(error -> assertThat(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(error))
                        .doesNotContain(ciphertext));
    }

    private static EdtClientBuilder builder(String prefix, String storePassword) throws Exception {
        Properties properties = new Properties();
        properties.setProperty(prefix + Merlin.KEYSTORE_TYPE, "PKCS12");
        properties.setProperty(prefix + Merlin.KEYSTORE_FILE, keystore.toString());
        properties.setProperty(prefix + Merlin.KEYSTORE_PASSWORD, storePassword);
        properties.setProperty(prefix + Merlin.TRUSTSTORE_TYPE, "PKCS12");
        properties.setProperty(prefix + Merlin.TRUSTSTORE_FILE, keystore.toString());
        properties.setProperty(prefix + Merlin.TRUSTSTORE_PASSWORD, PASSWORD);
        Path configFile = Files.createTempFile(directory, "crypto-", ".properties");
        try (OutputStream out = Files.newOutputStream(configFile)) {
            properties.store(out, "Synthetic fixture only");
        }
        var config = new EdtClientBuilderConfig();
        config.setKeystoreUser(ALIAS);
        config.setKeystorePassword(PASSWORD);
        config.setUserNameTokenUser("synthetic-user");
        config.setUserNameTokenPassword("synthetic-user-password");
        var builder = new EdtClientBuilder(config);
        builder.setClientKeystoreFilename(configFile.toString());
        return builder;
    }

    private static RequestData request(EdtClientBuilder builder) {
        var request = new RequestData();
        request.setMsgContext(new SoapMessage(Soap11.getInstance()));
        request.setCallbackHandler(builder.newCallback());
        return request;
    }

    private static class CryptoLoadingInInterceptor extends WSS4JInInterceptor {
        CryptoLoadingInInterceptor(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public Crypto loadDecryptionCrypto(RequestData request) throws WSSecurityException {
            return super.loadDecryptionCrypto(request);
        }
    }

    @WebService(targetNamespace = "urn:synthetic-ebs")
    public interface SyntheticService {
        @WebMethod
        String echo(String value);
    }
}
