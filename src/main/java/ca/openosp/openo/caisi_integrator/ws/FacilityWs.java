package ca.openosp.openo.caisi_integrator.ws;

import java.util.List;
import javax.jws.WebResult;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebMethod;
import java.util.Calendar;
import javax.jws.WebParam;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "FacilityWs")
@XmlSeeAlso({ ObjectFactory.class })
public interface FacilityWs
{
    @WebMethod
    @RequestWrapper(localName = "createImportLogWithStatus", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CreateImportLogWithStatus")
    @ResponseWrapper(localName = "createImportLogWithStatusResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CreateImportLogWithStatusResponse")
    @WebResult(name = "return", targetNamespace = "")
    ImportLog createImportLogWithStatus(@WebParam(name = "arg0", targetNamespace = "") final String p0, @WebParam(name = "arg1", targetNamespace = "") final String p1, @WebParam(name = "arg2", targetNamespace = "") final Calendar p2, @WebParam(name = "arg3", targetNamespace = "") final Calendar p3, @WebParam(name = "arg4", targetNamespace = "") final String p4, @WebParam(name = "arg5", targetNamespace = "") final String p5);
    
    @WebMethod
    @RequestWrapper(localName = "getMyFacility", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMyFacility")
    @ResponseWrapper(localName = "getMyFacilityResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMyFacilityResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedFacility getMyFacility();
    
    @WebMethod
    @RequestWrapper(localName = "errorImportLog", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.ErrorImportLog")
    @ResponseWrapper(localName = "errorImportLogResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.ErrorImportLogResponse")
    @WebResult(name = "return", targetNamespace = "")
    ImportLog errorImportLog(@WebParam(name = "arg0", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "updateMyFacilityLastUpdateDate", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.UpdateMyFacilityLastUpdateDate")
    @ResponseWrapper(localName = "updateMyFacilityLastUpdateDateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.UpdateMyFacilityLastUpdateDateResponse")
    void updateMyFacilityLastUpdateDate(@WebParam(name = "arg0", targetNamespace = "") final Calendar p0);
    
    @WebMethod
    @RequestWrapper(localName = "completeImportLog", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CompleteImportLog")
    @ResponseWrapper(localName = "completeImportLogResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CompleteImportLogResponse")
    @WebResult(name = "return", targetNamespace = "")
    ImportLog completeImportLog(@WebParam(name = "arg0", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setMyFacility", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetMyFacility")
    @ResponseWrapper(localName = "setMyFacilityResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetMyFacilityResponse")
    void setMyFacility(@WebParam(name = "arg0", targetNamespace = "") final CachedFacility p0);
    
    @WebMethod
    @RequestWrapper(localName = "getAllFacility", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllFacility")
    @ResponseWrapper(localName = "getAllFacilityResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetAllFacilityResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedFacility> getAllFacility();
    
    @WebMethod
    @RequestWrapper(localName = "canProcessFile", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CanProcessFile")
    @ResponseWrapper(localName = "canProcessFileResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CanProcessFileResponse")
    @WebResult(name = "return", targetNamespace = "")
    String canProcessFile(@WebParam(name = "arg0", targetNamespace = "") final String p0, @WebParam(name = "arg1", targetNamespace = "") final String p1, @WebParam(name = "arg2", targetNamespace = "") final Calendar p2, @WebParam(name = "arg3", targetNamespace = "") final Calendar p3, @WebParam(name = "arg4", targetNamespace = "") final String p4);
    
    @WebMethod
    @RequestWrapper(localName = "getImportLogByFilenameAndChecksum", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetImportLogByFilenameAndChecksum")
    @ResponseWrapper(localName = "getImportLogByFilenameAndChecksumResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetImportLogByFilenameAndChecksumResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<ImportLog> getImportLogByFilenameAndChecksum(@WebParam(name = "arg0", targetNamespace = "") final String p0, @WebParam(name = "arg1", targetNamespace = "") final String p1);
    
    @WebMethod
    @RequestWrapper(localName = "createImportLog", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CreateImportLog")
    @ResponseWrapper(localName = "createImportLogResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.CreateImportLogResponse")
    @WebResult(name = "return", targetNamespace = "")
    ImportLog createImportLog(@WebParam(name = "arg0", targetNamespace = "") final String p0, @WebParam(name = "arg1", targetNamespace = "") final String p1, @WebParam(name = "arg2", targetNamespace = "") final Calendar p2, @WebParam(name = "arg3", targetNamespace = "") final Calendar p3, @WebParam(name = "arg4", targetNamespace = "") final String p4);
    
    @WebMethod
    @RequestWrapper(localName = "getImportLogsSince", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetImportLogsSince")
    @ResponseWrapper(localName = "getImportLogsSinceResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetImportLogsSinceResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<ImportLog> getImportLogsSince(@WebParam(name = "arg0", targetNamespace = "") final Calendar p0);
}
