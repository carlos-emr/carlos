package ca.openosp.openo.caisi_integrator.ws;

import ca.openosp.openo.ws.MatchingClientScore;
import java.util.List;
import ca.openosp.openo.ws.MatchingClientParameters;
import java.util.Calendar;
import javax.jws.WebResult;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebMethod;
import ca.openosp.openo.ws.Client;
import javax.jws.WebParam;
import ca.openosp.openo.ws.ObjectFactory;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "HnrWs")
@XmlSeeAlso({ ObjectFactory.class, ca.openosp.openo.caisi_integrator.ws.ObjectFactory.class })
public interface HnrWs
{
    @WebMethod
    @RequestWrapper(localName = "getHnrClient", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetHnrClient")
    @ResponseWrapper(localName = "getHnrClientResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetHnrClientResponse")
    @WebResult(name = "return", targetNamespace = "")
    Client getHnrClient(@WebParam(name = "linkingId", targetNamespace = "") final Integer p0) throws ConnectException_Exception;
    
    @WebMethod
    @RequestWrapper(localName = "setHnrClientData", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetHnrClientData")
    @ResponseWrapper(localName = "setHnrClientDataResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetHnrClientDataResponse")
    @WebResult(name = "return", targetNamespace = "")
    Integer setHnrClientData(@WebParam(name = "arg0", targetNamespace = "") final Client p0) throws DuplicateHinExceptionException, ConnectException_Exception, InvalidHinExceptionException;
    
    @WebMethod
    @RequestWrapper(localName = "setHnrClientHidden", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetHnrClientHidden")
    @ResponseWrapper(localName = "setHnrClientHiddenResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetHnrClientHiddenResponse")
    void setHnrClientHidden(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final boolean p1, @WebParam(name = "arg2", targetNamespace = "") final Calendar p2) throws ConnectException_Exception;
    
    @WebMethod
    @RequestWrapper(localName = "getMatchingHnrClients", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMatchingHnrClients")
    @ResponseWrapper(localName = "getMatchingHnrClientsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMatchingHnrClientsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<MatchingClientScore> getMatchingHnrClients(@WebParam(name = "arg0", targetNamespace = "") final MatchingClientParameters p0) throws ConnectException_Exception;
}
