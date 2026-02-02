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
 * JAX-WS service client for the CAISI Integrator Program web service.
 *
 * This service provides connectivity to the CAISI (Client Access to Integrated Services and Information)
 * Integrator system for program management operations. The CAISI Integrator enables inter-EMR data sharing
 * and integration across multiple OpenO installations, supporting community health programs and regional
 * health information exchange.
 *
 * The service client is auto-generated from the WSDL definition and provides access to program-related
 * operations through the ProgramWs port interface. It supports multiple instantiation patterns including
 * default configuration, custom WSDL locations, and JAX-WS feature customization.
 *
 * @since 2026-01-24
 * @see ProgramWs
 * @see Service
 */
@WebServiceClient(name = "ProgramWsService", wsdlLocation = "file:ProgramService.wsdl", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class ProgramWsService extends Service
{
    public static final URL WSDL_LOCATION;
    public static final QName SERVICE;
    public static final QName ProgramWsPort;

    /**
     * Creates a new ProgramWsService instance with a custom WSDL location.
     *
     * @param url URL the WSDL location for the Program web service
     */
    public ProgramWsService(final URL url) {
        super(url, ProgramWsService.SERVICE);
    }

    /**
     * Creates a new ProgramWsService instance with a custom WSDL location and service QName.
     *
     * @param url URL the WSDL location for the Program web service
     * @param qName QName the qualified name of the service
     */
    public ProgramWsService(final URL url, final QName qName) {
        super(url, qName);
    }

    /**
     * Creates a new ProgramWsService instance using the default WSDL location.
     *
     * The default WSDL location is loaded from the static initializer and points to
     * the ProgramService.wsdl file.
     */
    public ProgramWsService() {
        super(ProgramWsService.WSDL_LOCATION, ProgramWsService.SERVICE);
    }

    /**
     * Creates a new ProgramWsService instance using the default WSDL location with custom features.
     *
     * @param array WebServiceFeature[] array of JAX-WS features to enable on the service (e.g., MTOM, addressing)
     */
    public ProgramWsService(final WebServiceFeature... array) {
        super(ProgramWsService.WSDL_LOCATION, ProgramWsService.SERVICE, array);
    }

    /**
     * Creates a new ProgramWsService instance with a custom WSDL location and features.
     *
     * @param url URL the WSDL location for the Program web service
     * @param array WebServiceFeature[] array of JAX-WS features to enable on the service (e.g., MTOM, addressing)
     */
    public ProgramWsService(final URL url, final WebServiceFeature... array) {
        super(url, ProgramWsService.SERVICE, array);
    }

    /**
     * Creates a new ProgramWsService instance with a custom WSDL location, service QName, and features.
     *
     * This is the most flexible constructor allowing full customization of the service configuration.
     *
     * @param url URL the WSDL location for the Program web service
     * @param qName QName the qualified name of the service
     * @param array WebServiceFeature[] array of JAX-WS features to enable on the service (e.g., MTOM, addressing)
     */
    public ProgramWsService(final URL url, final QName qName, final WebServiceFeature... array) {
        super(url, qName, array);
    }
    
    /**
     * Retrieves the ProgramWs port for accessing program management operations.
     *
     * This method returns the default port for the CAISI Integrator Program web service,
     * providing access to program-related operations such as program enrollment, case management,
     * and inter-EMR program data exchange.
     *
     * @return ProgramWs the Program web service port interface
     */
    @WebEndpoint(name = "ProgramWsPort")
    public ProgramWs getProgramWsPort() {
        return (ProgramWs)super.getPort(ProgramWsService.ProgramWsPort, (Class)ProgramWs.class);
    }

    /**
     * Retrieves the ProgramWs port with custom JAX-WS features enabled.
     *
     * This method allows customization of the port with specific features such as MTOM
     * (Message Transmission Optimization Mechanism) for binary attachments or WS-Addressing
     * for enhanced message routing in the CAISI Integrator system.
     *
     * @param array WebServiceFeature[] array of JAX-WS features to enable on the port
     * @return ProgramWs the Program web service port interface with features applied
     */
    @WebEndpoint(name = "ProgramWsPort")
    public ProgramWs getProgramWsPort(final WebServiceFeature... array) {
        return (ProgramWs)super.getPort(ProgramWsService.ProgramWsPort, (Class)ProgramWs.class, array);
    }
    
    static {
        SERVICE = new QName("http://ws.caisi_integrator.oscarehr.org/", "ProgramWsService");
        ProgramWsPort = new QName("http://ws.caisi_integrator.oscarehr.org/", "ProgramWsPort");
        URL wsdl_LOCATION = null;
        try {
            wsdl_LOCATION = new URL("file:ProgramService.wsdl");
        }
        catch (final MalformedURLException ex) {
            Logger.getLogger(ProgramWsService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:ProgramService.wsdl");
        }
        WSDL_LOCATION = wsdl_LOCATION;
    }
}
