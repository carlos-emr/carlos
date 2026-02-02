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
 * JAX-WS web service client for the Hospital/Network Report (HNR) integration service.
 *
 * This service provides a client interface for accessing the HNR Web Service, which is part
 * of the CAISI Integrator module. The CAISI Integrator enables data sharing between multiple
 * OpenO EMR installations and external healthcare systems. The HNR service specifically handles
 * hospital and network reporting functionality, allowing retrieval and management of hospital
 * reports and related healthcare information across integrated systems.
 *
 * The service client is generated from the HnrService.wsdl contract and provides multiple
 * constructor options for different configuration scenarios, including custom WSDL locations
 * and JAX-WS features for advanced web service capabilities.
 *
 * @see HnrWs
 * @see javax.xml.ws.Service
 * @since 2026-01-24
 */
@WebServiceClient(name = "HnrWsService", wsdlLocation = "file:HnrService.wsdl", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class HnrWsService extends Service
{
    /** The WSDL location URL for the HNR web service. */
    public static final URL WSDL_LOCATION;

    /** The qualified name for the HNR web service. */
    public static final QName SERVICE;

    /** The qualified name for the HNR web service port. */
    public static final QName HnrWsPort;

    /**
     * Constructs a new HnrWsService client with a custom WSDL location.
     *
     * This constructor allows specifying a custom WSDL location URL while using the
     * default service name. This is useful when the WSDL is hosted at a location
     * different from the default file:HnrService.wsdl.
     *
     * @param url URL the WSDL location URL for the service
     */
    public HnrWsService(final URL url) {
        super(url, HnrWsService.SERVICE);
    }
    
    /**
     * Constructs a new HnrWsService client with custom WSDL location and service name.
     *
     * This constructor provides full control over both the WSDL location and the
     * qualified service name, useful for advanced integration scenarios where both
     * the WSDL location and service name need to be customized.
     *
     * @param url URL the WSDL location URL for the service
     * @param qName QName the qualified name of the service
     */
    public HnrWsService(final URL url, final QName qName) {
        super(url, qName);
    }

    /**
     * Constructs a new HnrWsService client with default configuration.
     *
     * This is the default no-argument constructor that uses the WSDL location
     * specified in the @WebServiceClient annotation (file:HnrService.wsdl) and
     * the default service name. This is the most common constructor for typical
     * service usage.
     */
    public HnrWsService() {
        super(HnrWsService.WSDL_LOCATION, HnrWsService.SERVICE);
    }

    /**
     * Constructs a new HnrWsService client with default WSDL location and custom JAX-WS features.
     *
     * This constructor allows enabling specific JAX-WS features such as MTOM (Message
     * Transmission Optimization Mechanism), addressing, or custom bindings while using
     * the default WSDL location and service name.
     *
     * @param array WebServiceFeature varargs array of JAX-WS features to enable
     */
    public HnrWsService(final WebServiceFeature... array) {
        super(HnrWsService.WSDL_LOCATION, HnrWsService.SERVICE, array);
    }

    /**
     * Constructs a new HnrWsService client with custom WSDL location and JAX-WS features.
     *
     * This constructor combines a custom WSDL location with JAX-WS feature configuration,
     * useful when the WSDL is hosted at a non-default location and specific web service
     * features need to be enabled.
     *
     * @param url URL the WSDL location URL for the service
     * @param array WebServiceFeature varargs array of JAX-WS features to enable
     */
    public HnrWsService(final URL url, final WebServiceFeature... array) {
        super(url, HnrWsService.SERVICE, array);
    }

    /**
     * Constructs a new HnrWsService client with full customization options.
     *
     * This is the most flexible constructor providing complete control over WSDL location,
     * service name, and JAX-WS features. This is useful for advanced integration scenarios
     * requiring complete customization of the service client configuration.
     *
     * @param url URL the WSDL location URL for the service
     * @param qName QName the qualified name of the service
     * @param array WebServiceFeature varargs array of JAX-WS features to enable
     */
    public HnrWsService(final URL url, final QName qName, final WebServiceFeature... array) {
        super(url, qName, array);
    }
    
    /**
     * Retrieves the HNR web service port with default configuration.
     *
     * Returns a proxy to the HNR web service endpoint using the default port configuration.
     * The returned HnrWs interface provides access to all operations defined in the WSDL
     * for hospital and network reporting functionality.
     *
     * @return HnrWs the web service port interface for accessing HNR operations
     */
    @WebEndpoint(name = "HnrWsPort")
    public HnrWs getHnrWsPort() {
        return (HnrWs)super.getPort(HnrWsService.HnrWsPort, (Class)HnrWs.class);
    }

    /**
     * Retrieves the HNR web service port with custom JAX-WS features.
     *
     * Returns a proxy to the HNR web service endpoint with specific JAX-WS features enabled.
     * This allows customization of the port behavior through features such as MTOM for
     * optimized binary data transmission or WS-Addressing for enhanced message routing.
     *
     * @param array WebServiceFeature varargs array of JAX-WS features to enable on the port
     * @return HnrWs the web service port interface for accessing HNR operations
     */
    @WebEndpoint(name = "HnrWsPort")
    public HnrWs getHnrWsPort(final WebServiceFeature... array) {
        return (HnrWs)super.getPort(HnrWsService.HnrWsPort, (Class)HnrWs.class, array);
    }
    
    static {
        SERVICE = new QName("http://ws.caisi_integrator.oscarehr.org/", "HnrWsService");
        HnrWsPort = new QName("http://ws.caisi_integrator.oscarehr.org/", "HnrWsPort");
        URL wsdl_LOCATION = null;
        try {
            wsdl_LOCATION = new URL("file:HnrService.wsdl");
        }
        catch (final MalformedURLException ex) {
            Logger.getLogger(HnrWsService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:HnrService.wsdl");
        }
        WSDL_LOCATION = wsdl_LOCATION;
    }
}
