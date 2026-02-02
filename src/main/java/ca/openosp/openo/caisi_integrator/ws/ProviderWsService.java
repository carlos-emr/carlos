package ca.openosp.openo.caisi_integrator.ws;

import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceFeature;
import javax.xml.namespace.QName;
import java.net.URL;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.Service;

/**
 * JAX-WS web service client for accessing provider information through the CAISI Integrator system.
 *
 * <p>This service provides a client interface for accessing healthcare provider data across
 * multiple OpenO EMR installations through the CAISI Integrator inter-EMR data sharing system.
 * The service is generated from the ProviderService.wsdl definition and provides multiple
 * constructor variations to support different web service configuration scenarios.</p>
 *
 * <p>The service supports JAX-WS WebServiceFeature configurations, allowing clients to customize
 * behavior such as MTOM (Message Transmission Optimization Mechanism), addressing, and other
 * WS-* specifications as needed for healthcare data exchange.</p>
 *
 * <p><strong>Healthcare Context:</strong> This service facilitates provider information exchange
 * between different EMR installations, supporting collaborative care scenarios where patient
 * information needs to be shared across healthcare organizations while maintaining proper
 * access controls and PHI protection.</p>
 *
 * @see ProviderWs
 * @see Service
 * @since 2026-01-24
 */
@WebServiceClient(name = "ProviderWsService", wsdlLocation = "file:ProviderService.wsdl", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class ProviderWsService extends Service
{
    /**
     * The WSDL location URL for the ProviderWsService.
     * Initialized from the file:ProviderService.wsdl location in the static initializer.
     */
    public static final URL WSDL_LOCATION;

    /**
     * The QName for the ProviderWsService.
     * Namespace: http://ws.caisi_integrator.oscarehr.org/
     */
    public static final QName SERVICE;

    /**
     * The QName for the ProviderWsPort endpoint.
     * Namespace: http://ws.caisi_integrator.oscarehr.org/
     */
    public static final QName ProviderWsPort;

    /**
     * Constructs a ProviderWsService client with a custom WSDL location URL.
     *
     * <p>This constructor allows clients to specify an alternative WSDL location
     * instead of using the default file:ProviderService.wsdl location. This is useful
     * when the WSDL is hosted at a different location or retrieved from a remote server.</p>
     *
     * @param url URL the custom WSDL location for the service definition
     */
    public ProviderWsService(final URL url) {
        super(url, ProviderWsService.SERVICE);
    }

    /**
     * Constructs a ProviderWsService client with a custom WSDL location and custom service QName.
     *
     * <p>This constructor provides full control over both the WSDL location and the service
     * QName. This is the most flexible constructor and is typically used in advanced scenarios
     * where both the WSDL location and service definition need to be customized.</p>
     *
     * @param url URL the custom WSDL location for the service definition
     * @param qName QName the custom qualified name for the service
     */
    public ProviderWsService(final URL url, final QName qName) {
        super(url, qName);
    }

    /**
     * Constructs a ProviderWsService client using the default WSDL location and service QName.
     *
     * <p>This is the default no-argument constructor that uses the WSDL_LOCATION and SERVICE
     * constants initialized from the static block. This is the most common constructor used
     * when creating a service client with default configuration.</p>
     */
    public ProviderWsService() {
        super(ProviderWsService.WSDL_LOCATION, ProviderWsService.SERVICE);
    }

    /**
     * Constructs a ProviderWsService client with default WSDL location and custom web service features.
     *
     * <p>This constructor allows clients to enable specific JAX-WS features while using the
     * default WSDL location and service QName. Common features include MTOM for optimized
     * binary data transmission, WS-Addressing for enhanced message routing, and other
     * WS-* specifications supported by JAX-WS.</p>
     *
     * @param array WebServiceFeature... array of web service features to enable for this client
     */
    public ProviderWsService(final WebServiceFeature... array) {
        super(ProviderWsService.WSDL_LOCATION, ProviderWsService.SERVICE, array);
    }

    /**
     * Constructs a ProviderWsService client with custom WSDL location and web service features.
     *
     * <p>This constructor combines a custom WSDL location with the ability to enable specific
     * JAX-WS features. This is useful when the WSDL is hosted at a different location and
     * specific features need to be enabled for the service interaction.</p>
     *
     * @param url URL the custom WSDL location for the service definition
     * @param array WebServiceFeature... array of web service features to enable for this client
     */
    public ProviderWsService(final URL url, final WebServiceFeature... array) {
        super(url, ProviderWsService.SERVICE, array);
    }

    /**
     * Constructs a ProviderWsService client with fully customized configuration.
     *
     * <p>This is the most flexible constructor, allowing full control over the WSDL location,
     * service QName, and enabled web service features. This constructor is typically used in
     * advanced integration scenarios where all aspects of the service configuration need to
     * be customized.</p>
     *
     * @param url URL the custom WSDL location for the service definition
     * @param qName QName the custom qualified name for the service
     * @param array WebServiceFeature... array of web service features to enable for this client
     */
    public ProviderWsService(final URL url, final QName qName, final WebServiceFeature... array) {
        super(url, qName, array);
    }

    /**
     * Retrieves the ProviderWs port for accessing provider web service operations.
     *
     * <p>This method returns a proxy to the ProviderWs web service endpoint, which provides
     * operations for querying and managing healthcare provider information through the CAISI
     * Integrator system. The returned port can be used to invoke web service operations
     * defined in the ProviderWs interface.</p>
     *
     * <p><strong>Healthcare Usage:</strong> Use this port to access provider directories,
     * retrieve provider credentials, and obtain provider demographic information for
     * inter-EMR collaboration and referral workflows.</p>
     *
     * @return ProviderWs the web service port for provider operations
     */
    @WebEndpoint(name = "ProviderWsPort")
    public ProviderWs getProviderWsPort() {
        return (ProviderWs)super.getPort(ProviderWsService.ProviderWsPort, (Class)ProviderWs.class);
    }

    /**
     * Retrieves the ProviderWs port with custom web service features enabled.
     *
     * <p>This method returns a proxy to the ProviderWs web service endpoint with specific
     * JAX-WS features enabled. This allows clients to customize the behavior of the web
     * service interaction, such as enabling MTOM for optimized binary data transmission,
     * WS-Addressing for enhanced message routing, or other WS-* specifications.</p>
     *
     * <p><strong>Example Features:</strong> Enable MTOM when transferring provider documents
     * or credentials, or enable WS-Addressing when specific message routing is required
     * in the healthcare integration architecture.</p>
     *
     * @param array WebServiceFeature... array of web service features to enable for this port
     * @return ProviderWs the web service port for provider operations with features enabled
     */
    @WebEndpoint(name = "ProviderWsPort")
    public ProviderWs getProviderWsPort(final WebServiceFeature... array) {
        return (ProviderWs)super.getPort(ProviderWsService.ProviderWsPort, (Class)ProviderWs.class, array);
    }
    
    static {
        SERVICE = new QName("http://ws.caisi_integrator.oscarehr.org/", "ProviderWsService");
        ProviderWsPort = new QName("http://ws.caisi_integrator.oscarehr.org/", "ProviderWsPort");
        URL wsdl_LOCATION = null;
        try {
            wsdl_LOCATION = new URL("file:ProviderService.wsdl");
        }
        catch (final MalformedURLException ex) {
            Logger.getLogger(ProviderWsService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:ProviderService.wsdl");
        }
        WSDL_LOCATION = wsdl_LOCATION;
    }
}
