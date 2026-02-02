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
 * JAX-WS client service for accessing the CAISI Integrator Facility Web Service.
 *
 * This service provides programmatic access to facility management operations in the CAISI
 * (Client Access to Integrated Services and Information) Integrator system. The Integrator
 * enables multiple OpenO EMR facilities to securely share and synchronize healthcare data
 * across organizational boundaries while maintaining patient consent and privacy controls.
 *
 * The FacilityWsService acts as a web service client stub that connects to the remote
 * FacilityWs SOAP endpoint. It provides methods to retrieve facility information, manage
 * data import logs, and synchronize facility metadata across the integrated network.
 *
 * <p><strong>Key Operations:</strong></p>
 * <ul>
 *   <li>Retrieve all facilities in the Integrator network</li>
 *   <li>Get and update local facility information</li>
 *   <li>Create and manage data import logs for audit trails</li>
 *   <li>Check file processing status for data synchronization</li>
 *   <li>Track facility last update timestamps</li>
 * </ul>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * This service is critical for multi-facility healthcare organizations that need to maintain
 * unified patient records across multiple sites while ensuring PIPEDA/HIPAA compliance through
 * controlled data sharing and comprehensive audit logging.
 *
 * <p><strong>WSDL Configuration:</strong></p>
 * The service expects a WSDL file at {@code file:FacilityService.wsdl}. If the WSDL location
 * is not accessible during initialization, a warning is logged but the service can still be
 * instantiated with alternative URL configurations.
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * FacilityWsService service = new FacilityWsService();
 * FacilityWs port = service.getFacilityWsPort();
 * List&lt;CachedFacility&gt; facilities = port.getAllFacility();
 * </pre>
 *
 * @see FacilityWs
 * @see CachedFacility
 * @see javax.xml.ws.Service
 * @since 2026-01-24
 */
@WebServiceClient(name = "FacilityWsService", wsdlLocation = "file:FacilityService.wsdl", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class FacilityWsService extends Service
{
    public static final URL WSDL_LOCATION;
    public static final QName SERVICE;
    public static final QName FacilityWsPort;

    /**
     * Constructs a FacilityWsService with a custom WSDL URL location.
     *
     * This constructor allows clients to override the default WSDL location specified
     * in the @WebServiceClient annotation. This is useful when the WSDL is hosted at
     * a different location than the default compile-time location.
     *
     * @param url URL the WSDL document location for the service
     */
    public FacilityWsService(final URL url) {
        super(url, FacilityWsService.SERVICE);
    }

    /**
     * Constructs a FacilityWsService with custom WSDL URL and service QName.
     *
     * This constructor provides full control over both the WSDL document location and
     * the service qualified name. This is typically used in advanced scenarios where
     * the service name differs from the default or when working with dynamically
     * generated service configurations.
     *
     * @param url URL the WSDL document location for the service
     * @param qName QName the qualified name of the service
     */
    public FacilityWsService(final URL url, final QName qName) {
        super(url, qName);
    }

    /**
     * Constructs a FacilityWsService using default WSDL location and service name.
     *
     * This is the standard constructor for normal usage. It initializes the service
     * using the WSDL location specified in the @WebServiceClient annotation
     * (file:FacilityService.wsdl) and the default service QName. If the WSDL file
     * cannot be found at the default location, a warning is logged but the service
     * instance is still created.
     */
    public FacilityWsService() {
        super(FacilityWsService.WSDL_LOCATION, FacilityWsService.SERVICE);
    }

    /**
     * Constructs a FacilityWsService with custom web service features.
     *
     * This constructor allows enabling specific JAX-WS features such as MTOM
     * (Message Transmission Optimization Mechanism), WS-Addressing, or custom
     * handler configurations. The service uses the default WSDL location and
     * service name while applying the specified features.
     *
     * @param array WebServiceFeature... variable-length array of web service features to enable
     */
    public FacilityWsService(final WebServiceFeature... array) {
        super(FacilityWsService.WSDL_LOCATION, FacilityWsService.SERVICE, array);
    }

    /**
     * Constructs a FacilityWsService with custom WSDL URL and web service features.
     *
     * This constructor combines a custom WSDL location with specific JAX-WS features.
     * It is useful when the WSDL is hosted at a non-default location and you need to
     * enable features like MTOM for large document transfers or WS-Addressing for
     * advanced routing scenarios common in healthcare integrations.
     *
     * @param url URL the WSDL document location for the service
     * @param array WebServiceFeature... variable-length array of web service features to enable
     */
    public FacilityWsService(final URL url, final WebServiceFeature... array) {
        super(url, FacilityWsService.SERVICE, array);
    }

    /**
     * Constructs a FacilityWsService with full customization of all parameters.
     *
     * This constructor provides complete control over the service configuration,
     * allowing specification of the WSDL location, service qualified name, and
     * web service features. This is the most flexible constructor, typically used
     * in advanced integration scenarios or when programmatically generating service
     * configurations from dynamic sources.
     *
     * @param url URL the WSDL document location for the service
     * @param qName QName the qualified name of the service
     * @param array WebServiceFeature... variable-length array of web service features to enable
     */
    public FacilityWsService(final URL url, final QName qName, final WebServiceFeature... array) {
        super(url, qName, array);
    }

    /**
     * Retrieves the FacilityWs port proxy for invoking web service operations.
     *
     * This method returns a port proxy that implements the FacilityWs interface,
     * providing access to all facility management operations such as retrieving
     * facility lists, managing import logs, and synchronizing facility data across
     * the CAISI Integrator network. The port is configured with default settings
     * and no additional web service features.
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * FacilityWsService service = new FacilityWsService();
     * FacilityWs port = service.getFacilityWsPort();
     * CachedFacility myFacility = port.getMyFacility();
     * </pre>
     *
     * @return FacilityWs the port proxy for invoking facility web service operations
     */
    @WebEndpoint(name = "FacilityWsPort")
    public FacilityWs getFacilityWsPort() {
        return (FacilityWs)super.getPort(FacilityWsService.FacilityWsPort, (Class)FacilityWs.class);
    }

    /**
     * Retrieves the FacilityWs port proxy with custom web service features enabled.
     *
     * This method returns a port proxy configured with the specified JAX-WS features.
     * This is useful when you need to enable specific capabilities for facility
     * operations, such as:
     * <ul>
     *   <li>MTOM (Message Transmission Optimization Mechanism) for efficient transfer
     *       of large facility data exports or document attachments</li>
     *   <li>WS-Addressing for advanced message routing in complex healthcare networks</li>
     *   <li>Custom security features for enhanced PHI protection during transmission</li>
     * </ul>
     *
     * @param array WebServiceFeature... variable-length array of web service features to enable
     * @return FacilityWs the port proxy for invoking facility web service operations with specified features
     */
    @WebEndpoint(name = "FacilityWsPort")
    public FacilityWs getFacilityWsPort(final WebServiceFeature... array) {
        return (FacilityWs)super.getPort(FacilityWsService.FacilityWsPort, (Class)FacilityWs.class, array);
    }
    
    static {
        SERVICE = new QName("http://ws.caisi_integrator.oscarehr.org/", "FacilityWsService");
        FacilityWsPort = new QName("http://ws.caisi_integrator.oscarehr.org/", "FacilityWsPort");
        URL wsdl_LOCATION = null;
        try {
            wsdl_LOCATION = new URL("file:FacilityService.wsdl");
        }
        catch (final MalformedURLException ex) {
            Logger.getLogger(FacilityWsService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:FacilityService.wsdl");
        }
        WSDL_LOCATION = wsdl_LOCATION;
    }
}
