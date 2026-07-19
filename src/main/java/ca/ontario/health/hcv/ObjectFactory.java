package ca.ontario.health.hcv;

import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory
{
    private static final QName _Validate_QNAME;
    private static final QName _ValidateResponse_QNAME;
    
    public HcvResults createHcvResults() {
        return new HcvResults();
    }
    
    public ValidateResponse createValidateResponse() {
        return new ValidateResponse();
    }
    
    public FeeServiceDetails createFeeServiceDetails() {
        return new FeeServiceDetails();
    }
    
    public Validate createValidate() {
        return new Validate();
    }
    
    public HcvRequest createHcvRequest() {
        return new HcvRequest();
    }
    
    public Person createPerson() {
        return new Person();
    }
    
    public Requests createRequests() {
        return new Requests();
    }
    
    @XmlElementDecl(namespace = "http://hcv.health.ontario.ca/", name = "validate")
    public JAXBElement<Validate> createValidate(final Validate value) {
        return (JAXBElement<Validate>)new JAXBElement(ObjectFactory._Validate_QNAME, (Class)Validate.class, (Class)null, (Object)value);
    }
    
    @XmlElementDecl(namespace = "http://hcv.health.ontario.ca/", name = "validateResponse")
    public JAXBElement<ValidateResponse> createValidateResponse(final ValidateResponse value) {
        return (JAXBElement<ValidateResponse>)new JAXBElement(ObjectFactory._ValidateResponse_QNAME, (Class)ValidateResponse.class, (Class)null, (Object)value);
    }
    
    static {
        _Validate_QNAME = new QName("http://hcv.health.ontario.ca/", "validate");
        _ValidateResponse_QNAME = new QName("http://hcv.health.ontario.ca/", "validateResponse");
    }
}
