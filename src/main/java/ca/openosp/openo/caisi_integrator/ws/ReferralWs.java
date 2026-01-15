package ca.openosp.openo.caisi_integrator.ws;

import java.util.List;
import javax.jws.WebResult;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "ReferralWs")
@XmlSeeAlso({ ObjectFactory.class })
public interface ReferralWs
{
    @WebMethod
    @RequestWrapper(localName = "getReferral", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetReferral")
    @ResponseWrapper(localName = "getReferralResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetReferralResponse")
    @WebResult(name = "return", targetNamespace = "")
    Referral getReferral(@WebParam(name = "referralId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedReferrals", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedReferrals")
    @ResponseWrapper(localName = "getLinkedReferralsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedReferralsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<Referral> getLinkedReferrals(@WebParam(name = "sourceCaisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getReferralsToProgram", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetReferralsToProgram")
    @ResponseWrapper(localName = "getReferralsToProgramResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetReferralsToProgramResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<Referral> getReferralsToProgram(@WebParam(name = "destinationCaisiProgramId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "makeReferral", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.MakeReferral")
    @ResponseWrapper(localName = "makeReferralResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.MakeReferralResponse")
    void makeReferral(@WebParam(name = "arg0", targetNamespace = "") final Referral p0);
    
    @WebMethod
    @RequestWrapper(localName = "removeReferral", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.RemoveReferral")
    @ResponseWrapper(localName = "removeReferralResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.RemoveReferralResponse")
    void removeReferral(@WebParam(name = "referralId", targetNamespace = "") final Integer p0);
}
