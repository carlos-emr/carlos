package ca.ontario.health.hcv;

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
 * JAX-WS client service for Ontario Health Card (HC) Validation.
 *
 * <p>This service provides integration with the Ontario Health Insurance Plan (OHIP)
 * Health Card Validation web service. It enables real-time validation of Ontario
 * health card numbers to verify patient eligibility and coverage status.</p>
 *
 * <p>The service supports multiple endpoint versions (Port.0 and Port.1) to maintain
 * backward compatibility with different versions of the OHIP validation service.</p>
 *
 * <p><strong>Healthcare Context:</strong> This service is critical for verifying patient
 * eligibility before providing medical services in Ontario. It helps ensure that:</p>
 * <ul>
 *   <li>Health Insurance Numbers (HINs) are valid and active</li>
 *   <li>Patient coverage is current and not expired</li>
 *   <li>Billing can be processed correctly through Ontario's healthcare system</li>
 * </ul>
 *
 * <p><strong>Provincial Integration:</strong> This is an Ontario-specific service that
 * integrates with provincial healthcare infrastructure. It is part of the Medical
 * Certificate Electronic Data Transfer (MCEDT) system.</p>
 *
 * @see HCValidation
 * @since 2026-01-24
 */
@WebServiceClient(name = "HCValidationService", wsdlLocation = "file:/home/oscara/mcedt/hcv-stubs/src/main/resources/from_ohip_web_site/HCValidationService.wsdl", targetNamespace = "http://hcv.health.ontario.ca/")
public class HCValidationService extends Service
{
    public static final URL WSDL_LOCATION;
    public static final QName SERVICE;
    public static final QName HCValidationPort1;
    public static final QName HCValidationPort0;

    /**
     * Constructs a new HCValidationService with a custom WSDL location.
     *
     * <p>This constructor allows specifying a custom location for the WSDL file,
     * which can be useful for testing or when the WSDL is hosted at a different
     * location than the default.</p>
     *
     * @param wsdlLocation URL the location of the WSDL file describing the service
     */
    public HCValidationService(final URL wsdlLocation) {
        super(wsdlLocation, HCValidationService.SERVICE);
    }

    /**
     * Constructs a new HCValidationService with custom WSDL location and service name.
     *
     * <p>This constructor provides full control over both the WSDL location and the
     * qualified service name, allowing for maximum flexibility in service configuration.</p>
     *
     * @param wsdlLocation URL the location of the WSDL file describing the service
     * @param serviceName QName the qualified name of the service
     */
    public HCValidationService(final URL wsdlLocation, final QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    /**
     * Constructs a new HCValidationService with default WSDL location and service name.
     *
     * <p>This is the recommended constructor for normal usage. It uses the default
     * WSDL location configured for the Ontario OHIP Health Card Validation Service.</p>
     *
     * <p>The default WSDL location is loaded from the static initializer and points to
     * the official OHIP web service endpoint.</p>
     */
    public HCValidationService() {
        super(HCValidationService.WSDL_LOCATION, HCValidationService.SERVICE);
    }

    /**
     * Retrieves the Health Card Validation endpoint (version 1).
     *
     * <p>This method returns a proxy to the HCValidation web service endpoint,
     * configured for version 1 of the service port. This port represents the
     * current/newer version of the validation service API.</p>
     *
     * <p><strong>Usage:</strong> Call methods on the returned HCValidation interface
     * to validate health card numbers against the OHIP database.</p>
     *
     * @return HCValidation a proxy instance for the Health Card Validation service endpoint
     */
    @WebEndpoint(name = "HCValidationPort.1")
    public HCValidation getHCValidationPort1() {
        return (HCValidation)super.getPort(HCValidationService.HCValidationPort1, (Class)HCValidation.class);
    }

    /**
     * Retrieves the Health Card Validation endpoint (version 1) with custom web service features.
     *
     * <p>This method returns a proxy to the HCValidation web service endpoint with
     * additional JAX-WS features enabled. Features can include MTOM (Message Transmission
     * Optimization Mechanism), addressing, or other WS-* specifications.</p>
     *
     * <p><strong>Advanced Usage:</strong> This overloaded method allows fine-grained
     * control over the web service client configuration.</p>
     *
     * @param features WebServiceFeature[] variable-length array of JAX-WS features to enable
     * @return HCValidation a proxy instance for the Health Card Validation service endpoint
     */
    @WebEndpoint(name = "HCValidationPort.1")
    public HCValidation getHCValidationPort1(final WebServiceFeature... features) {
        return (HCValidation)super.getPort(HCValidationService.HCValidationPort1, (Class)HCValidation.class, features);
    }

    /**
     * Retrieves the Health Card Validation endpoint (version 0).
     *
     * <p>This method returns a proxy to the HCValidation web service endpoint,
     * configured for version 0 of the service port. This port represents the
     * legacy/older version of the validation service API maintained for backward
     * compatibility.</p>
     *
     * <p><strong>Note:</strong> Use this method only when integration with older
     * OHIP validation service versions is required. New implementations should
     * prefer {@link #getHCValidationPort1()}.</p>
     *
     * @return HCValidation a proxy instance for the Health Card Validation service endpoint
     */
    @WebEndpoint(name = "HCValidationPort.0")
    public HCValidation getHCValidationPort0() {
        return (HCValidation)super.getPort(HCValidationService.HCValidationPort0, (Class)HCValidation.class);
    }

    /**
     * Retrieves the Health Card Validation endpoint (version 0) with custom web service features.
     *
     * <p>This method returns a proxy to the HCValidation web service endpoint with
     * additional JAX-WS features enabled. This is the legacy service port with
     * customizable features for advanced configuration.</p>
     *
     * <p><strong>Note:</strong> Use this method only when integration with older
     * OHIP validation service versions is required and custom JAX-WS features are
     * needed. New implementations should prefer {@link #getHCValidationPort1(WebServiceFeature...)}.</p>
     *
     * @param features WebServiceFeature[] variable-length array of JAX-WS features to enable
     * @return HCValidation a proxy instance for the Health Card Validation service endpoint
     */
    @WebEndpoint(name = "HCValidationPort.0")
    public HCValidation getHCValidationPort0(final WebServiceFeature... features) {
        return (HCValidation)super.getPort(HCValidationService.HCValidationPort0, (Class)HCValidation.class, features);
    }
    
    static {
        SERVICE = new QName("http://hcv.health.ontario.ca/", "HCValidationService");
        HCValidationPort1 = new QName("http://hcv.health.ontario.ca/", "HCValidationPort.1");
        HCValidationPort0 = new QName("http://hcv.health.ontario.ca/", "HCValidationPort.0");
        URL url = null;
        try {
            url = new URL("file:/home/oscara/mcedt/hcv-stubs/src/main/resources/from_ohip_web_site/HCValidationService.wsdl");
        }
        catch (final MalformedURLException e) {
            Logger.getLogger(HCValidationService.class.getName()).log(Level.INFO, "Can not initialize the default wsdl from {0}", "file:/home/oscara/mcedt/hcv-stubs/src/main/resources/from_ohip_web_site/HCValidationService.wsdl");
        }
        WSDL_LOCATION = url;
    }
}
