package ca.ontario.health.edt;

import javax.xml.ws.ResponseWrapper;
import javax.jws.WebMethod;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebResult;
import javax.jws.WebParam;
import java.math.BigInteger;
import java.util.List;
import ca.ontario.health.ebs.idp.ObjectFactory;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://edt.health.ontario.ca/", name = "EDTDelegate")
@XmlSeeAlso({ ObjectFactory.class, ca.ontario.health.ebs.ObjectFactory.class, ca.ontario.health.edt.ObjectFactory.class, ca.ontario.health.ebs.msa.ObjectFactory.class })
public interface EDTDelegate
{
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "submit", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.Submit")
    @WebMethod
    @ResponseWrapper(localName = "submitResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.SubmitResponse")
    ResourceResult submit(@WebParam(name = "resourceIDs", targetNamespace = "") final List<BigInteger> p0) throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "upload", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.Upload")
    @WebMethod
    @ResponseWrapper(localName = "uploadResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.UploadResponse")
    ResourceResult upload(@WebParam(name = "upload", targetNamespace = "") final List<UploadData> p0) throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "info", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.Info")
    @WebMethod
    @ResponseWrapper(localName = "infoResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.InfoResponse")
    Detail info(@WebParam(name = "resourceIDs", targetNamespace = "") final List<BigInteger> p0) throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "delete", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.Delete")
    @WebMethod
    @ResponseWrapper(localName = "deleteResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.DeleteResponse")
    ResourceResult delete(@WebParam(name = "resourceIDs", targetNamespace = "") final List<BigInteger> p0) throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "download", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.Download")
    @WebMethod
    @ResponseWrapper(localName = "downloadResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.DownloadResponse")
    DownloadResult download(@WebParam(name = "resourceIDs", targetNamespace = "") final List<BigInteger> p0) throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "update", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.Update")
    @WebMethod
    @ResponseWrapper(localName = "updateResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.UpdateResponse")
    ResourceResult update(@WebParam(name = "updates", targetNamespace = "") final List<UpdateRequest> p0) throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "getTypeList", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.GetTypeList")
    @WebMethod
    @ResponseWrapper(localName = "getTypeListResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.GetTypeListResponse")
    TypeListResult getTypeList() throws Faultexception;
    
    @WebResult(name = "return", targetNamespace = "")
    @RequestWrapper(localName = "list", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.List")
    @WebMethod
    @ResponseWrapper(localName = "listResponse", targetNamespace = "http://edt.health.ontario.ca/", className = "ca.ontario.health.edt.ListResponse")
    Detail list(@WebParam(name = "resourceType", targetNamespace = "") final String p0, @WebParam(name = "status", targetNamespace = "") final ResourceStatus p1, @WebParam(name = "pageNo", targetNamespace = "") final BigInteger p2) throws Faultexception;
}
