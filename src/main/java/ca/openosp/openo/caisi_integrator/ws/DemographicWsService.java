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
 * JAX-WS web service client for CAISI Integrator demographic data integration.
 *
 * This service provides a SOAP client interface to interact with the CAISI (Client Access to
 * Integrated Services and Information) Integrator demographic web service. It enables OpenO EMR
 * instances to share and synchronize patient demographic data, clinical records, and other
 * healthcare information across multiple facilities within an integrated healthcare network.
 *
 * The service acts as a client stub generated from the DemographicService WSDL, providing
 * type-safe access to demographic operations including:
 * <ul>
 *   <li>Patient demographic synchronization across facilities</li>
 *   <li>Clinical data sharing (allergies, medications, lab results)</li>
 *   <li>Appointment and encounter information exchange</li>
 *   <li>Document and form management</li>
 *   <li>Consent and privacy management</li>
 * </ul>
 *
 * This class extends {@link javax.xml.ws.Service} and follows the JAX-WS service pattern,
 * providing multiple constructors for different initialization scenarios with support for
 * custom WSDL locations and web service features.
 *
 * @see DemographicWs
 * @see javax.xml.ws.Service
 * @see javax.xml.ws.WebServiceClient
 * @since 2026-01-24
 */
@WebServiceClient(name = "DemographicWsService", wsdlLocation = "file:DemographicService.wsdl", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class DemographicWsService extends Service
{
    /** The WSDL location URL for the demographic web service. */
    public static final URL WSDL_LOCATION;

    /** The QName representing the service in the WSDL namespace. */
    public static final QName SERVICE;

    /** The QName representing the demographic web service port. */
    public static final QName DemographicWsPort;

    /**
     * Constructs a new DemographicWsService with a custom WSDL location.
     *
     * This constructor allows clients to specify a custom WSDL URL for the service,
     * useful when the service endpoint is located at a different location than the
     * default WSDL file location. The service QName is always set to the default
     * SERVICE constant.
     *
     * @param url URL the custom WSDL location URL
     */
    public DemographicWsService(final URL url) {
        super(url, DemographicWsService.SERVICE);
    }
    
    /**
     * Constructs a new DemographicWsService with custom WSDL location and service QName.
     *
     * This constructor provides full control over both the WSDL location and the service
     * qualified name. This is useful when connecting to services with non-standard
     * configurations or when working with multiple service definitions.
     *
     * @param url URL the custom WSDL location URL
     * @param qName QName the qualified name identifying the service
     */
    public DemographicWsService(final URL url, final QName qName) {
        super(url, qName);
    }

    /**
     * Constructs a new DemographicWsService with default configuration.
     *
     * This is the default constructor that uses the WSDL location and service QName
     * defined as constants in this class. This is the recommended constructor for
     * standard CAISI Integrator demographic service connections.
     */
    public DemographicWsService() {
        super(DemographicWsService.WSDL_LOCATION, DemographicWsService.SERVICE);
    }

    /**
     * Constructs a new DemographicWsService with default configuration and web service features.
     *
     * This constructor allows specifying JAX-WS features such as MTOM (Message Transmission
     * Optimization Mechanism), addressing, or other WS-* specifications while using the
     * default WSDL location and service QName.
     *
     * @param array WebServiceFeature array of web service features to enable (e.g., MTOM, addressing)
     */
    public DemographicWsService(final WebServiceFeature... array) {
        super(DemographicWsService.WSDL_LOCATION, DemographicWsService.SERVICE, array);
    }

    /**
     * Constructs a new DemographicWsService with custom WSDL location and web service features.
     *
     * This constructor combines a custom WSDL URL with JAX-WS features, useful for
     * connecting to non-standard service locations with specific protocol requirements.
     *
     * @param url URL the custom WSDL location URL
     * @param array WebServiceFeature array of web service features to enable
     */
    public DemographicWsService(final URL url, final WebServiceFeature... array) {
        super(url, DemographicWsService.SERVICE, array);
    }

    /**
     * Constructs a new DemographicWsService with full customization.
     *
     * This is the most flexible constructor, allowing specification of WSDL location,
     * service QName, and web service features. This provides complete control over all
     * aspects of the service connection configuration.
     *
     * @param url URL the custom WSDL location URL
     * @param qName QName the qualified name identifying the service
     * @param array WebServiceFeature array of web service features to enable
     */
    public DemographicWsService(final URL url, final QName qName, final WebServiceFeature... array) {
        super(url, qName, array);
    }
    
    /**
     * Retrieves the demographic web service port with default configuration.
     *
     * This method returns a proxy object that implements the {@link DemographicWs} interface,
     * which provides access to all demographic web service operations including patient data
     * synchronization, clinical information sharing, and consent management across the CAISI
     * Integrator network.
     *
     * The returned port can be used to invoke all demographic service methods defined in the
     * DemographicWs interface, such as:
     * <ul>
     *   <li>Linking/unlinking demographics across facilities</li>
     *   <li>Retrieving and setting cached patient data (allergies, drugs, notes, etc.)</li>
     *   <li>Managing appointments, documents, and lab results</li>
     *   <li>Handling consent and privacy settings</li>
     * </ul>
     *
     * @return DemographicWs the demographic web service port proxy
     */
    @WebEndpoint(name = "DemographicWsPort")
    public DemographicWs getDemographicWsPort() {
        return (DemographicWs)super.getPort(DemographicWsService.DemographicWsPort, (Class)DemographicWs.class);
    }

    /**
     * Retrieves the demographic web service port with custom web service features.
     *
     * This method returns a proxy object that implements the {@link DemographicWs} interface
     * with specified JAX-WS features enabled. This is useful for configuring protocol-specific
     * behaviors such as MTOM for binary data transfer (e.g., document contents, lab images),
     * WS-Addressing for advanced routing, or other WS-* specifications.
     *
     * Example use cases:
     * <ul>
     *   <li>Enabling MTOM when transferring large medical documents or images</li>
     *   <li>Configuring timeouts for slow network connections between facilities</li>
     *   <li>Adding security features for enhanced PHI protection</li>
     * </ul>
     *
     * @param array WebServiceFeature array of web service features to enable on this port
     * @return DemographicWs the demographic web service port proxy with specified features
     */
    @WebEndpoint(name = "DemographicWsPort")
    public DemographicWs getDemographicWsPort(final WebServiceFeature... array) {
        return (DemographicWs)super.getPort(DemographicWsService.DemographicWsPort, (Class)DemographicWs.class, array);
    }
    
    static {
        SERVICE = new QName("http://ws.caisi_integrator.oscarehr.org/", "DemographicWsService");
        DemographicWsPort = new QName("http://ws.caisi_integrator.oscarehr.org/", "DemographicWsPort");
        URL wsdl_LOCATION = null;
        try {
            wsdl_LOCATION = new URL("file:DemographicService.wsdl");
        }
        catch (final MalformedURLException ex) {
            Logger.getLogger(DemographicWsService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:DemographicService.wsdl");
        }
        WSDL_LOCATION = wsdl_LOCATION;
    }
}
