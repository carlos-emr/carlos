package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.ws.WebFault;

@WebFault(name = "ConnectException", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/")
public class ConnectException_Exception extends Exception
{
    private ConnectException connectException;
    
    public ConnectException_Exception() {
    }
    
    public ConnectException_Exception(final String message) {
        super(message);
    }
    
    public ConnectException_Exception(final String message, final Throwable cause) {
        super(message, cause);
    }
    
    public ConnectException_Exception(final String message, final ConnectException connectException) {
        super(message);
        this.connectException = connectException;
    }
    
    public ConnectException_Exception(final String message, final ConnectException connectException, final Throwable cause) {
        super(message, cause);
        this.connectException = connectException;
    }
    
    public ConnectException getFaultInfo() {
        return this.connectException;
    }
}
