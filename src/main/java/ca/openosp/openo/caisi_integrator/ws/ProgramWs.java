package ca.openosp.openo.caisi_integrator.ws;

import javax.jws.WebResult;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import java.util.List;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "ProgramWs")
@XmlSeeAlso({ ObjectFactory.class })
public interface ProgramWs
{
    @WebMethod
    @RequestWrapper(localName = "setCachedPrograms", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedPrograms")
    @ResponseWrapper(localName = "setCachedProgramsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedProgramsResponse")
    void setCachedPrograms(@WebParam(name = "arg0", targetNamespace = "") final List<CachedProgram> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getAllProgramsAllowingIntegratedReferrals", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllProgramsAllowingIntegratedReferrals")
    @ResponseWrapper(localName = "getAllProgramsAllowingIntegratedReferralsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllProgramsAllowingIntegratedReferralsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedProgram> getAllProgramsAllowingIntegratedReferrals();
    
    @WebMethod
    @RequestWrapper(localName = "deleteCachedProgramsMissingFromList", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedProgramsMissingFromList")
    @ResponseWrapper(localName = "deleteCachedProgramsMissingFromListResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedProgramsMissingFromListResponse")
    void deleteCachedProgramsMissingFromList(@WebParam(name = "arg0", targetNamespace = "") final List<Integer> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getAllPrograms", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllPrograms")
    @ResponseWrapper(localName = "getAllProgramsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllProgramsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedProgram> getAllPrograms();
}
