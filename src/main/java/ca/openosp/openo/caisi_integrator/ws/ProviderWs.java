package ca.openosp.openo.caisi_integrator.ws;

import javax.jws.WebResult;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import java.util.List;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "ProviderWs")
@XmlSeeAlso({ ObjectFactory.class })
public interface ProviderWs
{
    @WebMethod
    @RequestWrapper(localName = "setCachedProviders", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedProviders")
    @ResponseWrapper(localName = "setCachedProvidersResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedProvidersResponse")
    void setCachedProviders(@WebParam(name = "arg0", targetNamespace = "") final List<ProviderTransfer> p0);
    
    @WebMethod
    @RequestWrapper(localName = "deactivateProviderCommunication", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeactivateProviderCommunication")
    @ResponseWrapper(localName = "deactivateProviderCommunicationResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeactivateProviderCommunicationResponse")
    void deactivateProviderCommunication(@WebParam(name = "arg0", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "addProviderComunication", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddProviderComunication")
    @ResponseWrapper(localName = "addProviderComunicationResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddProviderComunicationResponse")
    void addProviderComunication(@WebParam(name = "arg0", targetNamespace = "") final ProviderCommunicationTransfer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getProviderRoles", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetProviderRoles")
    @ResponseWrapper(localName = "getProviderRolesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetProviderRolesResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<Role> getProviderRoles(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdStringCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "getAllProviders", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllProviders")
    @ResponseWrapper(localName = "getAllProvidersResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllProvidersResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedProvider> getAllProviders();
    
    @WebMethod
    @RequestWrapper(localName = "getProviderCommunications", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetProviderCommunications")
    @ResponseWrapper(localName = "getProviderCommunicationsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetProviderCommunicationsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<ProviderCommunicationTransfer> getProviderCommunications(@WebParam(name = "arg0", targetNamespace = "") final String p0, @WebParam(name = "arg1", targetNamespace = "") final String p1, @WebParam(name = "arg2", targetNamespace = "") final Boolean p2);
}
