package ca.ontario.health.edt;

import java.net.MalformedURLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.WebEndpoint;
import javax.xml.namespace.QName;
import java.net.URL;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.Service;

/**
 * JAX-WS web service client for Ontario's Electronic Data Transfer (EDT) system.
 *
 * This service provides access to Ontario's Medical Certificate Electronic Data Transfer (MCEDT)
 * system, which enables secure electronic submission of medical certificates and related healthcare
 * documentation to the Ontario Health Insurance Plan (OHIP). The EDT system is part of Ontario's
 * provincial healthcare integration infrastructure for healthcare providers.
 *
 * The service acts as a SOAP client wrapper around the EDT web service endpoint, handling the
 * communication protocol and message formatting required for MCEDT submissions. It provides
 * multiple constructor options for different initialization scenarios and supports JAX-WS features
 * for advanced web service configuration.
 *
 * <p><strong>Healthcare Context:</strong> EDT is used by healthcare providers in Ontario to
 * electronically submit various medical certificates including disability certificates, return-to-work
 * certificates, and other healthcare documentation required by employers, insurance companies, and
 * government agencies. This integration ensures compliance with Ontario's healthcare data exchange
 * standards.</p>
 *
 * <p><strong>Security:</strong> All EDT communications must be conducted over secure channels with
 * appropriate authentication credentials as mandated by Ontario Health privacy regulations.</p>
 *
 * @see EDTDelegate
 * @see javax.xml.ws.Service
 * @since 2026-01-24
 */
@WebServiceClient(name = "EDTService", wsdlLocation = "file:/home/oscara/mcedt/edt-stubs/src/main/resources/from_ohip_web_site/EDTService.wsdl", targetNamespace = "http://edt.health.ontario.ca/")
public class EDTService extends Service
{
    /**
     * The default WSDL location for the EDT service endpoint.
     * Initialized from the OHIP web site WSDL definition.
     */
    public static final URL WSDL_LOCATION;

    /**
     * The qualified name (QName) for the EDT web service.
     * Namespace: http://edt.health.ontario.ca/
     */
    public static final QName SERVICE;

    /**
     * The qualified name (QName) for the EDT service port.
     * Used to identify the specific endpoint for EDT operations.
     */
    public static final QName EDTPort;

    /**
     * Constructs an EDTService client with a custom WSDL location.
     *
     * This constructor allows overriding the default WSDL location, which is useful for
     * testing environments or when connecting to alternative EDT service endpoints.
     * The service name is fixed to the standard EDT service QName.
     *
     * @param wsdlLocation URL the URL pointing to the WSDL definition for the EDT service.
     *                     Must be a valid URL pointing to an accessible WSDL document.
     */
    public EDTService(final URL wsdlLocation) {
        super(wsdlLocation, EDTService.SERVICE);
    }
    
    /**
     * Constructs an EDTService client with custom WSDL location and service name.
     *
     * This constructor provides maximum flexibility for configuring the EDT service client,
     * allowing both the WSDL location and service qualified name to be customized. This is
     * primarily used for advanced integration scenarios or when working with non-standard
     * EDT service configurations.
     *
     * @param wsdlLocation URL the URL pointing to the WSDL definition for the EDT service.
     *                     Must be a valid URL pointing to an accessible WSDL document.
     * @param serviceName QName the qualified name identifying the specific web service.
     *                    Must match the service name defined in the WSDL document.
     */
    public EDTService(final URL wsdlLocation, final QName serviceName) {
        super(wsdlLocation, serviceName);
    }
    
    /**
     * Constructs an EDTService client with default WSDL location and service name.
     *
     * This is the standard constructor for EDT service integration, using the default
     * WSDL location obtained from OHIP's web site and the standard EDT service QName.
     * This constructor should be used for normal production EDT operations in Ontario.
     *
     * The WSDL location is initialized from a static URL pointing to the official
     * EDT service definition provided by Ontario Health.
     */
    public EDTService() {
        super(EDTService.WSDL_LOCATION, EDTService.SERVICE);
    }
    
    /**
     * Retrieves the EDT service port for executing electronic data transfer operations.
     *
     * This method returns a proxy object implementing the EDTDelegate interface, which provides
     * access to all EDT web service operations for submitting medical certificates and related
     * healthcare documentation to Ontario's MCEDT system.
     *
     * The returned delegate can be used to invoke EDT operations such as certificate submission,
     * status queries, and document retrieval according to Ontario Health's EDT specifications.
     *
     * @return EDTDelegate a proxy instance for invoking EDT web service operations.
     *         The delegate is configured with the default endpoint and service settings.
     */
    @WebEndpoint(name = "EDTPort")
    public EDTDelegate getEDTPort() {
        return (EDTDelegate)super.getPort(EDTService.EDTPort, (Class)EDTDelegate.class);
    }
    
    /**
     * Retrieves the EDT service port with custom JAX-WS features enabled.
     *
     * This method returns a proxy object implementing the EDTDelegate interface with additional
     * JAX-WS features configured. Features can include WS-Security settings, MTOM (Message
     * Transmission Optimization Mechanism) for attachments, WS-Addressing, or other advanced
     * SOAP web service capabilities required for specific EDT integration scenarios.
     *
     * Common use cases include enabling message-level security for PHI (Protected Health Information)
     * transmission, configuring custom timeout values, or enabling specialized logging features
     * for audit compliance in healthcare environments.
     *
     * @param features WebServiceFeature... variable-length array of JAX-WS features to enable
     *                 on the service port. Features are applied in the order provided and affect
     *                 all subsequent web service operations through this port.
     * @return EDTDelegate a proxy instance for invoking EDT web service operations with the
     *         specified features enabled. The delegate is configured with the provided features
     *         in addition to default endpoint settings.
     */
    @WebEndpoint(name = "EDTPort")
    public EDTDelegate getEDTPort(final WebServiceFeature... features) {
        return (EDTDelegate)super.getPort(EDTService.EDTPort, (Class)EDTDelegate.class, features);
    }
    
    static {
        SERVICE = new QName("http://edt.health.ontario.ca/", "EDTService");
        EDTPort = new QName("http://edt.health.ontario.ca/", "EDTPort");
        URL url = null;
        try {
            url = new URL("file:/home/oscara/mcedt/edt-stubs/src/main/resources/from_ohip_web_site/EDTService.wsdl");
        }
        catch (final MalformedURLException e) {
            Logger.getLogger(EDTService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:/home/oscara/mcedt/edt-stubs/src/main/resources/from_ohip_web_site/EDTService.wsdl");
        }
        WSDL_LOCATION = url;
    }
}
