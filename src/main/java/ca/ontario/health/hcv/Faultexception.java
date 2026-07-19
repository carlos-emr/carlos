package ca.ontario.health.hcv;

import ca.ontario.health.ebs.EbsFault;
import javax.xml.ws.WebFault;

@WebFault(name = "EBSFault", targetNamespace = "http://ebs.health.ontario.ca/")
public class Faultexception extends Exception
{
    private EbsFault ebsFault;
    
    public Faultexception() {
    }
    
    public Faultexception(final String message) {
        super(message);
    }
    
    public Faultexception(final String message, final Throwable cause) {
        super(message, cause);
    }
    
    public Faultexception(final String message, final EbsFault ebsFault) {
        super(message);
        this.ebsFault = ebsFault;
    }
    
    public Faultexception(final String message, final EbsFault ebsFault, final Throwable cause) {
        super(message, cause);
        this.ebsFault = ebsFault;
    }
    
    public EbsFault getFaultInfo() {
        return this.ebsFault;
    }
}
