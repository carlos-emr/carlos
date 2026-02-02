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
 * JAX-WS web service client for CAISI Integrator referral management.
 *
 * <p>This service client provides access to the CAISI Integrator referral web service,
 * enabling healthcare providers to manage patient referrals across integrated EMR systems.
 * The CAISI (Client Access to Integrated Services and Information) Integrator facilitates
 * data sharing and interoperability between multiple OpenO EMR installations and other
 * healthcare systems within a collaborative care network.</p>
 *
 * <p>The service uses JAX-WS (Java API for XML Web Services) to communicate with the
 * referral service endpoint defined in ReferralService.wsdl. It provides multiple
 * constructors to support various web service feature configurations and custom
 * WSDL locations for different deployment environments.</p>
 *
 * <p><strong>Healthcare Context:</strong></p>
 * <ul>
 *   <li>Supports inter-provider referral workflows in community health settings</li>
 *   <li>Enables referral tracking across integrated healthcare facilities</li>
 *   <li>Maintains referral data consistency in multi-site deployments</li>
 *   <li>Complies with healthcare information exchange standards</li>
 * </ul>
 *
 * <p><strong>Security Considerations:</strong></p>
 * <ul>
 *   <li>All referral data contains Protected Health Information (PHI)</li>
 *   <li>Transport security must be configured at the deployment level</li>
 *   <li>Authentication and authorization handled by service endpoint</li>
 *   <li>WSDL location must be properly secured in production environments</li>
 * </ul>
 *
 * @see ReferralWs
 * @see javax.xml.ws.Service
 * @see javax.xml.ws.WebServiceClient
 * @since 2026-01-24
 */
@WebServiceClient(name = "ReferralWsService", wsdlLocation = "file:ReferralService.wsdl", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class ReferralWsService extends Service
{
    /** The URL location of the WSDL document for this service. */
    public static final URL WSDL_LOCATION;

    /** The qualified name of the service in the WSDL namespace. */
    public static final QName SERVICE;

    /** The qualified name of the ReferralWs port in the WSDL namespace. */
    public static final QName ReferralWsPort;

    /**
     * Constructs a new ReferralWsService with a custom WSDL location.
     *
     * <p>This constructor allows clients to specify an alternative WSDL location
     * while using the default service QName. Useful for environments where the
     * WSDL document is hosted at a different URL than the default.</p>
     *
     * @param url URL the location of the WSDL document for this service
     */
    public ReferralWsService(final URL url) {
        super(url, ReferralWsService.SERVICE);
    }
    
    /**
     * Constructs a new ReferralWsService with custom WSDL location and service QName.
     *
     * <p>This constructor provides maximum flexibility by allowing specification of both
     * the WSDL location and the service qualified name. Typically used in advanced
     * integration scenarios or when the service definition differs from defaults.</p>
     *
     * @param url URL the location of the WSDL document for this service
     * @param qName QName the qualified name of the service
     */
    public ReferralWsService(final URL url, final QName qName) {
        super(url, qName);
    }

    /**
     * Constructs a new ReferralWsService using default configuration.
     *
     * <p>This is the default constructor that uses the predefined WSDL location
     * (file:ReferralService.wsdl) and service QName. This constructor is typically
     * used in standard deployments where the WSDL is deployed alongside the application.</p>
     */
    public ReferralWsService() {
        super(ReferralWsService.WSDL_LOCATION, ReferralWsService.SERVICE);
    }

    /**
     * Constructs a new ReferralWsService with custom web service features.
     *
     * <p>This constructor allows enabling JAX-WS features such as MTOM (Message
     * Transmission Optimization Mechanism), addressing, or custom handlers while
     * using the default WSDL location and service QName.</p>
     *
     * @param array WebServiceFeature[] array of web service features to enable
     */
    public ReferralWsService(final WebServiceFeature... array) {
        super(ReferralWsService.WSDL_LOCATION, ReferralWsService.SERVICE, array);
    }

    /**
     * Constructs a new ReferralWsService with custom WSDL location and web service features.
     *
     * <p>Combines custom WSDL location with configurable web service features,
     * using the default service QName. Useful for deployments that require both
     * alternative WSDL hosting and specific JAX-WS feature configuration.</p>
     *
     * @param url URL the location of the WSDL document for this service
     * @param array WebServiceFeature[] array of web service features to enable
     */
    public ReferralWsService(final URL url, final WebServiceFeature... array) {
        super(url, ReferralWsService.SERVICE, array);
    }

    /**
     * Constructs a new ReferralWsService with full customization.
     *
     * <p>This constructor provides complete control over the service configuration,
     * allowing specification of WSDL location, service QName, and web service features.
     * Intended for advanced integration scenarios requiring maximum flexibility.</p>
     *
     * @param url URL the location of the WSDL document for this service
     * @param qName QName the qualified name of the service
     * @param array WebServiceFeature[] array of web service features to enable
     */
    public ReferralWsService(final URL url, final QName qName, final WebServiceFeature... array) {
        super(url, qName, array);
    }
    
    /**
     * Retrieves the ReferralWs port for invoking referral service operations.
     *
     * <p>Returns a proxy instance that implements the ReferralWs interface, allowing
     * clients to invoke referral management operations on the remote web service.
     * The port is configured with default settings and no additional web service features.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * ReferralWsService service = new ReferralWsService();
     * ReferralWs port = service.getReferralWsPort();
     * // Use port to invoke referral operations
     * </pre>
     *
     * @return ReferralWs the service port for referral operations
     * @see ReferralWs
     */
    @WebEndpoint(name = "ReferralWsPort")
    public ReferralWs getReferralWsPort() {
        return (ReferralWs)super.getPort(ReferralWsService.ReferralWsPort, (Class)ReferralWs.class);
    }

    /**
     * Retrieves the ReferralWs port with custom web service features.
     *
     * <p>Returns a proxy instance that implements the ReferralWs interface with
     * specified JAX-WS features enabled. This allows clients to configure advanced
     * features such as MTOM for attachment handling, WS-Addressing for message routing,
     * or custom handlers for logging and security processing.</p>
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>
     * ReferralWsService service = new ReferralWsService();
     * MTOMFeature mtom = new MTOMFeature();
     * ReferralWs port = service.getReferralWsPort(mtom);
     * // Use port with MTOM enabled
     * </pre>
     *
     * @param array WebServiceFeature[] array of web service features to enable on the port
     * @return ReferralWs the service port configured with specified features
     * @see ReferralWs
     * @see javax.xml.ws.WebServiceFeature
     */
    @WebEndpoint(name = "ReferralWsPort")
    public ReferralWs getReferralWsPort(final WebServiceFeature... array) {
        return (ReferralWs)super.getPort(ReferralWsService.ReferralWsPort, (Class)ReferralWs.class, array);
    }
    
    static {
        SERVICE = new QName("http://ws.caisi_integrator.oscarehr.org/", "ReferralWsService");
        ReferralWsPort = new QName("http://ws.caisi_integrator.oscarehr.org/", "ReferralWsPort");
        URL wsdl_LOCATION = null;
        try {
            wsdl_LOCATION = new URL("file:ReferralService.wsdl");
        }
        catch (final MalformedURLException ex) {
            Logger.getLogger(ReferralWsService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:ReferralService.wsdl");
        }
        WSDL_LOCATION = wsdl_LOCATION;
    }
}
