package ca.openosp.openo.integration.ebs.client.ng;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.X509TrustManager;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPFactory;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.headers.Header;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.xml.security.exceptions.AlgorithmAlreadyRegisteredException;
import org.apache.xml.security.transforms.Transform;
import org.apache.xml.security.transforms.InvalidTransformException;
import org.apache.xml.security.utils.resolver.ResourceResolver;
import org.apache.logging.log4j.Logger;

import ca.openosp.openo.utility.MiscUtils;
import ca.ontario.health.ebs.idp.IdpHeader;

/**
 * Client that encapsulates MCEDT service access.
 * 
 * <h3>Implementation Details</h3>
 * 
 * MCEDT service operates under IPD (Identity Provider Model) authentication
 * model. This means that the sender must use public key technology to sign the
 * SOAP headers and body. The signing certificate can be any available
 * certificate and can be self-signed. If any response data is specified to be
 * encrypted, at least AES128-CBC symmetric encryption algorithm with the public
 * key belonging to the signer of the initial SOAP request must be used.
 * 
 * <p/>
 * 
 * The SOAP message must contain the EBS and IDP headers in the SOAP message
 * header with the user name and password (for the Go-Secure IDP in a
 * WS-Security username token). The SOAP headers and body are then digitally
 * signed to guarantee message integrity and source.
 * 
 * <p/>
 * 
 * If any request data is specified to be encrypted, by the specific www
 * service, it will use the public key of the EBS system. SOAP must be signed
 * with a Timestamp element for each message TTL for the SOAP message
 * will be 10 minutes. Each message must also include the Username token.
 * 
 * <p/>
 * 
 * EBS SOAP header must contains the software conformance key and the service
 * requester
 * audit ID. Security header must be specified with "must understand" flag set
 * to 1
 * 
 * <p/>
 *
 * The service requester must sign all headers and the body using a certificate
 * issued by an issuer approved by MHLTC. The signature must meet the following requirements:
 * Identifier Type
 * - Key Identifier Type: Binary Security Token, Direct Reference Signature
 * - Signature Canonicalization: Inclusive Canonicalization
 */
public class EdtClientBuilder {

    private static final Logger log = MiscUtils.getLogger();
    private static final String DEFAULT_CLIENT_KEYSTORE = "clientKeystore.properties";

    // EBS and IDP SOAP header element names and namespaces
    private static final String TAG_NAME_EBS                      = "EBS";
    private static final String TAG_NAME_SOFTWARE_CONFORMANCE_KEY = "SoftwareConformanceKey";
    private static final String TAG_NAME_AUDIT_ID                 = "AuditId";
    private static final String TAG_NAME_IDP                      = "IDP";
    private static final String TAG_NAME_SERVICE_USER_MUID        = "ServiceUserMUID";
    private static final String NS_EBS                            = "http://ebs.health.ontario.ca/";
    private static final String NS_IDP                            = "http://idp.ebs.health.ontario.ca/";

    private static final QName QNAME_EBS = new QName(NS_EBS, TAG_NAME_EBS, "ebs");
    private static final QName QNAME_IDP = new QName(NS_IDP, TAG_NAME_IDP, "idp");

    // Directives for which SOAP parts to sign
    private static final String SIGNED_MESSAGE_ELEMENTS =
        "{Element}{" + NS_EBS + "}EBS;" +
        "{Element}{" + NS_IDP + "}IDP;" +
        "{Element}{http://docs.oasis-open.org/wss/2004/01/"
            + "oasis-200401-wss-wssecurity-utility-1.0.xsd}Timestamp;" +
        "{Element}{http://schemas.xmlsoap.org/soap/envelope/}Body;";

    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static String clientKeystore = DEFAULT_CLIENT_KEYSTORE;

    protected EdtClientBuilderConfig config;

    /**
     * Creates a new builder and registers the attachment resolver
     * necessary for handling custom transforms.
     */
    public EdtClientBuilder() {
        registerAttachmentResolver();
    }

    /**
     * Creates a new builder with the given configuration.
     *
     * @param config user-specified builder configuration
     */
    public EdtClientBuilder(EdtClientBuilderConfig config) {
        this();
        setConfig(config);
    }

    /**
     * @return the current builder configuration
     */
    public EdtClientBuilderConfig getConfig() {
        return config;
    }

    /**
     * @param config the configuration to use for building clients
     */
    public void setConfig(EdtClientBuilderConfig config) {
        this.config = config;
    }

    /**
     * Builds and configures a SOAP client of the given service interface.
     * <p>
     * Steps performed:
     * <ol>
     *   <li>Create proxy via JaxWsProxyFactoryBean</li>
     *   <li>Configure SSL/TLS settings</li>
     *   <li>Install WS-Security in/out interceptors</li>
     *   <li>Enable MTOM on the SOAPBinding if requested</li>
     *   <li>Set HTTP timeouts</li>
     *   <li>Add custom EBS/IDP headers</li>
     * </ol>
     *
     * @param <T>         the service interface
     * @param clientClass the service interface class
     * @return a configured proxy implementing the interface
     */
    public <T> T build(Class<T> clientClass) {
        T port = newDelegate(clientClass);

        Client client = ClientProxy.getClient(port);
        // Set up SSL trust-all if needed
        configureSsl((HTTPConduit) client.getConduit());

        // Add sec interceptors
        configureOutInterceptor(client);
        configureInInterceptor(client);

        // Enable MTOM on SOAPBinding if configured
        BindingProvider bp = (BindingProvider) port;
        SOAPBinding sb = (SOAPBinding) bp.getBinding();
        if (getConfig().isMtomEnabled()) {
            sb.setMTOMEnabled(true);
        }

        // Configure HTTP timeouts
        bp.getRequestContext().put("com.sun.xml.internal.webserv.request.timeout", 240_000);
        HTTPConduit conduit = (HTTPConduit) client.getConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();
        policy.setConnectionTimeout(100_000);
        policy.setReceiveTimeout(240_000);
        conduit.setClient(policy);

        // Point WS-Security signature properties to our keystore
        bp.getRequestContext().put("signaturePropFile", clientKeystore);

        // Add EBS/IDP SOAP headers
        try {
            configureHeaderList(bp);
        } catch (Exception e) {
            throw new RuntimeException("Unable to configure EBS/IDP headers", e);
        }

        return port;
    }

    /**
     * Creates the actual JAX-WS proxy for the service interface.
     */
    @SuppressWarnings("unchecked")
    protected <T> T newDelegate(Class<T> clientClass) {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(clientClass);
        factory.setAddress(getConfig().getServiceUrl());
        return (T) factory.create();
    }

    /**
     * Adds the EBS and IDP SOAP headers (AuditId, SoftwareConformanceKey, ServiceUserMUID).
     */
    protected void configureHeaderList(BindingProvider bp) throws Exception {
        SOAPFactory sf = SOAPFactory.newInstance();

        // Build <ebs:EBS> header
        SOAPElement ebs = sf.createElement(QNAME_EBS);
        SOAPElement scKey = sf.createElement(TAG_NAME_SOFTWARE_CONFORMANCE_KEY);
        scKey.setTextContent(getConfig().getConformanceKey());
        ebs.addChildElement(scKey);

        SOAPElement audit = sf.createElement(TAG_NAME_AUDIT_ID);
        audit.setTextContent(getConfig().getAuditId());
        ebs.addChildElement(audit);

        // Build <idp:IDP> header
        SOAPElement idp = sf.createElement(QNAME_IDP);
        SOAPElement muid = sf.createElement(TAG_NAME_SERVICE_USER_MUID);
        muid.setTextContent(getConfig().getServiceId());
        idp.addChildElement(muid);

        List<Header> headers = new ArrayList<>();
        headers.add(new Header(QNAME_EBS, ebs));
        headers.add(new Header(QNAME_IDP, idp));
        bp.getRequestContext().put(Header.HEADER_LIST, headers);
    }

    /**
     * Installs inbound interceptors: logging, attachment caching, WS-Security, etc.
     */
    protected void configureInInterceptor(Client client) {
        if (getConfig().isLoggingRequired()) {
            client.getEndpoint().getInInterceptors().add(new LoggingInInterceptor());
            client.getEndpoint().getInInterceptors().add(new DownloadInInterceptor());
        }

        // Cache attachments for later processing
        client.getEndpoint().getInInterceptors().add(new AttachmentCachingInterceptor());

        // Apply WS-Security in interceptor
        client.getEndpoint().getInInterceptors().add(new DynamicWSS4JInInterceptor(this));

        client.getEndpoint().getInInterceptors().add(new AttachmentCleanupInterceptor());
    }

    /**
     * Builds the property map for the inbound WS-Security interceptor.
     */
    protected Map<String,Object> newWSSInInterceptorConfiguration() {
        System.out.println("=== MCEDT DEBUG: Configuring WSS4J In Interceptor ===");
        System.out.println("Keystore file: " + clientKeystore);

        Map<String,Object> props = new HashMap<>();
        props.put(WSHandlerConstants.ACTION, getCxfInHandlerDirectives());
        System.out.println("WSS4J Actions: " + getCxfInHandlerDirectives());

        props.put(WSHandlerConstants.PW_CALLBACK_REF, newCallback());

        // Decryption properties - more explicit in CXF 3.5+
        props.put(WSHandlerConstants.DEC_PROP_FILE, clientKeystore);
        props.put(WSHandlerConstants.DEC_PROP_REF_ID, "decryptionProperties");
        Properties keystoreProps = loadKeystoreProperties();
        props.put("decryptionProperties", keystoreProps);

        System.out.println("Decryption properties loaded: " + keystoreProps);
        dumpKeystoreInfo(keystoreProps);

        // Algorithm support
        props.put(WSHandlerConstants.ALLOW_RSA15_KEY_TRANSPORT_ALGORITHM, "true");
        props.put(WSHandlerConstants.ENC_SYM_ALGO, "http://www.w3.org/2001/04/xmlenc#aes128-cbc");
        props.put(WSHandlerConstants.ENC_KEY_TRANSPORT, "http://www.w3.org/2001/04/xmlenc#rsa-1_5");

        // MTOM settings
        props.put(WSHandlerConstants.STORE_BYTES_IN_ATTACHMENT, "false");
        props.put(WSHandlerConstants.EXPAND_XOP_INCLUDE, "false");

        System.out.println("=== MCEDT DEBUG: WSS4J In Interceptor Configured ===");
        return props;
    }

    /**
     * Installs outbound interceptors: logging and WS-Security signing/encryption.
     */
    protected void configureOutInterceptor(Client client) {
        if (getConfig().isLoggingRequired()) {
            client.getEndpoint().getOutInterceptors().add(new org.apache.cxf.ext.logging.LoggingOutInterceptor());
        }
        Map<String,Object> outProps = newWSSOutInterceptorConfiguration();
        WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(outProps);
        // MTOM is enabled on the SOAPBinding, so remove deprecated setAllowMTOM
        client.getEndpoint().getOutInterceptors().add(wssOut);
    }

    /**
     * Builds the property map for the outbound WS-Security interceptor.
     */
    protected Map<String,Object> newWSSOutInterceptorConfiguration() {
        Map<String,Object> props = new HashMap<>();
        props.put(WSHandlerConstants.MUST_UNDERSTAND, "1");
        props.put(WSHandlerConstants.ACTION, getCxfOutHandlerDirectives());
        props.put(WSHandlerConstants.USER, getConfig().getKeystoreUser());
        props.put(WSHandlerConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
        props.put(WSHandlerConstants.PW_CALLBACK_REF, newCallback());
        props.put(WSHandlerConstants.SIGNATURE_PARTS, SIGNED_MESSAGE_ELEMENTS);
        props.put(WSHandlerConstants.SIG_PROP_FILE, clientKeystore);
        props.put(WSHandlerConstants.SIG_KEY_ID, "DirectReference");
        props.put(WSHandlerConstants.STORE_BYTES_IN_ATTACHMENT, "0");
        props.put(WSHandlerConstants.EXPAND_XOP_INCLUDE, "0");
        return props;
    }

    protected String getCxfInHandlerDirectives() {
        return WSHandlerConstants.TIMESTAMP + " "
             + WSHandlerConstants.SIGNATURE + " "
             + WSHandlerConstants.ENCRYPTION;  // This means "decrypt incoming encrypted content"
    }

    /**
     * @return WS-Security directives for username token, timestamp, and signature
     */
    protected String getCxfOutHandlerDirectives() {
        return WSHandlerConstants.USERNAME_TOKEN + " "
             + WSHandlerConstants.TIMESTAMP     + " "
             + WSHandlerConstants.SIGNATURE;
    }

    /**
     * @return callback for retrieving passwords during WS-Security processing
     */
    protected ClientPasswordCallback newCallback() {
        return new ClientPasswordCallback(
            getConfig().getUserNameTokenUser(),
            getConfig().getUserNameTokenPassword(),
            getConfig().getKeystoreUser(),
            getConfig().getKeystorePassword()
        );
    }

    /**
     * Creates an IDP header object for the given user MUID.
     */
    public static IdpHeader createIdpHeader(String userMuid) {
        IdpHeader header = new IdpHeader();
        header.setServiceUserMUID(userMuid);
        return header;
    }

    /**
     * Configures SSL/TLS parameters on the HTTPConduit.
     */
    public static void configureSsl(HTTPConduit conduit) {
        TLSClientParameters tls = conduit.getTlsClientParameters();
        if (tls == null) {
            tls = new TLSClientParameters();
        }
        tls.setDisableCNCheck(true);
        tls.setTrustManagers(new X509TrustManager[]{new TrustAllManager()});
        tls.setSecureSocketProtocol("TLS");
        conduit.setTlsClientParameters(tls);
    }

    /**
     * Allows overriding the client keystore filename.
     */
    public static void setClientKeystoreFilename(String filename) {
        clientKeystore = filename;
    }

    /**
     * Trust manager implementation that accepts all certificates.
     */
    public static class TrustAllManager implements X509TrustManager {
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { /* no-op */ }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { /* no-op */ }
    }

    /**
     * Registers the attachment resolver and transform for custom MTOM handling.
     * Ensures registration only happens once.
     */
    private static void registerAttachmentResolver() {
        if (isInitialized.compareAndSet(false, true)) {
            ResourceResolver.register(new AttachmentResolverSpi(), true);
            try {
                Transform.register(
                    TransformAttachmentCiphertext.TRANSFORM_ATTACHMENT_CIPHERTEXT,
                    TransformAttachmentCiphertext.class
                );
            } catch (AlgorithmAlreadyRegisteredException | InvalidTransformException e) {
                // ignore if already registered or invalid transform
            }
        }
    }

    private Properties loadKeystoreProperties() {
        System.out.println("=== MCEDT DEBUG: Loading keystore properties ===");
        System.out.println("Original keystore path: " + clientKeystore);

        Properties props = new Properties();

        // Normalize path if it starts with "file:" to strip that prefix
        String normalizedPath = clientKeystore;
        if (normalizedPath != null && normalizedPath.startsWith("file:")) {
            normalizedPath = normalizedPath.substring("file:".length());
            System.out.println("Normalized path (stripped 'file:'): " + normalizedPath);
        }

        // First try as classpath resource
        System.out.println("Attempting to load from classpath: " + clientKeystore);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(clientKeystore)) {
            if (is != null) {
                props.load(is);
                System.out.println("SUCCESS: Loaded from classpath");
                return props;
            } else {
                System.out.println("NOT FOUND: in classpath, trying filesystem...");
            }
        } catch (Exception e) {
            System.out.println("ERROR loading from classpath: " + e.getMessage());
        }

        // If classpath resource not found, try filesystem
        System.out.println("Attempting to load from filesystem: " + normalizedPath);
        try (InputStream is = new FileInputStream(normalizedPath)) {
            props.load(is);
            System.out.println("SUCCESS: Loaded from filesystem");
            return props;
        } catch (Exception e) {
            String errorMsg = "Failed to load keystore properties from either classpath or file system: " + clientKeystore;
            System.out.println("ERROR: " + errorMsg);
            System.out.println("Error: " + e.getMessage());
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Dumps detailed keystore information for debugging.
     */
    private void dumpKeystoreInfo(Properties keystoreProps) {
        System.out.println("=== MCEDT DEBUG: Keystore Information ===");
        try {
            String keystoreFile = keystoreProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.file");
            String keystorePassword = keystoreProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.password");
            String keystoreAlias = keystoreProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.alias");
            String keystoreType = keystoreProps.getProperty("org.apache.ws.security.crypto.merlin.keystore.type");

            System.out.println("Keystore file: " + keystoreFile);
            System.out.println("Keystore type: " + keystoreType);
            System.out.println("Keystore alias: " + keystoreAlias);
            System.out.println("Keystore password: " + (keystorePassword != null ? "*****(length=" + keystorePassword.length() + ")" : "null"));

            // Try to load the actual keystore and dump certificate info
            if (keystoreFile != null && keystorePassword != null) {
                java.io.File ksFile = new java.io.File(keystoreFile);
                System.out.println("Keystore file exists: " + ksFile.exists());
                System.out.println("Keystore file readable: " + ksFile.canRead());
                System.out.println("Keystore file size: " + ksFile.length() + " bytes");

                if (ksFile.exists()) {
                    try {
                        java.security.KeyStore ks = java.security.KeyStore.getInstance(keystoreType != null ? keystoreType : "JKS");
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(ksFile)) {
                            ks.load(fis, keystorePassword.toCharArray());
                            System.out.println("Keystore loaded successfully!");

                            // List all aliases
                            System.out.println("Aliases in keystore:");
                            java.util.Enumeration<String> aliases = ks.aliases();
                            while (aliases.hasMoreElements()) {
                                String alias = aliases.nextElement();
                                System.out.println("  - " + alias + " (isKey: " + ks.isKeyEntry(alias) + ", isCert: " + ks.isCertificateEntry(alias) + ")");

                                // Get certificate details
                                if (ks.isKeyEntry(alias) || ks.isCertificateEntry(alias)) {
                                    java.security.cert.Certificate cert = ks.getCertificate(alias);
                                    if (cert instanceof java.security.cert.X509Certificate) {
                                        java.security.cert.X509Certificate x509 = (java.security.cert.X509Certificate) cert;
                                        System.out.println("    Subject: " + x509.getSubjectX500Principal());
                                        System.out.println("    Valid from: " + x509.getNotBefore());
                                        System.out.println("    Valid until: " + x509.getNotAfter());
                                        System.out.println("    Currently valid: " + isCurrentlyValid(x509));
                                    }
                                }
                            }

                            // Check if the configured alias exists
                            if (keystoreAlias != null) {
                                boolean hasKey = ks.isKeyEntry(keystoreAlias);
                                System.out.println("Configured alias '" + keystoreAlias + "' has private key: " + hasKey);
                                if (!hasKey) {
                                    System.out.println("WARNING: Configured alias does not have a private key entry!");
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("ERROR loading keystore: " + e.getMessage());
                        log.error("Error loading keystore for debug", e);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR dumping keystore info: " + e.getMessage());
            log.error("Error dumping keystore info", e);
        }
        System.out.println("=== MCEDT DEBUG: End Keystore Information ===");
    }

    private boolean isCurrentlyValid(java.security.cert.X509Certificate cert) {
        try {
            cert.checkValidity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
