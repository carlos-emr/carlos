package ca.ontario.health.hcv;

import java.util.logging.Logger;
import javax.jws.WebService;

@WebService(serviceName = "HCValidationService", portName = "HCValidationPort.0", targetNamespace = "http://hcv.health.ontario.ca/", wsdlLocation = "file:/home/oscara/mcedt/hcv-stubs/src/main/resources/from_ohip_web_site/HCValidationService.wsdl", endpointInterface = "ca.ontario.health.hcv.HCValidation")
public class HCValidationImpl1 implements HCValidation
{
    private static final Logger LOG;
    
    @Override
    public HcvResults validate(final Requests requests, final String locale) throws Faultexception {
        HCValidationImpl1.LOG.info("Executing operation validate");
        System.out.println(requests);
        System.out.println(locale);
        try {
            final HcvResults _return = null;
            return _return;
        }
        catch (final Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
    
    static {
        LOG = Logger.getLogger(HCValidationImpl.class.getName());
    }
}
