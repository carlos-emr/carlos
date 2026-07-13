package ca.ontario.health.hcv;

import jakarta.xml.ws.ResponseWrapper;
import jakarta.jws.WebMethod;
import jakarta.xml.ws.RequestWrapper;
import jakarta.jws.WebResult;
import jakarta.jws.WebParam;
import ca.ontario.health.ebs.idp.ObjectFactory;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.jws.WebService;

@WebService(targetNamespace = "http://hcv.health.ontario.ca/", name = "HCValidation")
@XmlSeeAlso({ ObjectFactory.class, ca.ontario.health.ebs.ObjectFactory.class, ca.ontario.health.ebs.msa.ObjectFactory.class, ca.ontario.health.hcv.ObjectFactory.class })
public interface HCValidation
{
    @WebResult(name = "results", targetNamespace = "")
    @RequestWrapper(localName = "validate", targetNamespace = "http://hcv.health.ontario.ca/", className = "ca.ontario.health.hcv.Validate")
    @WebMethod
    @ResponseWrapper(localName = "validateResponse", targetNamespace = "http://hcv.health.ontario.ca/", className = "ca.ontario.health.hcv.ValidateResponse")
    HcvResults validate(@WebParam(name = "requests", targetNamespace = "") final Requests p0, @WebParam(name = "locale", targetNamespace = "") final String p1) throws Faultexception;
}
