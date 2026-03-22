package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import jakarta.xml.bind.annotation.XmlRegistry;

/**
 * JAXB object factory for creating HNR web service DTOs and JAXBElement wrappers.
 *
 * <p>Provides factory methods for all JAXB-bound types in the HNR web service namespace,
 * including request/response objects, client models, and exception fault beans.
 *
 * @since 2012-08-13
 */
@XmlRegistry
public class ObjectFactory
{
    private static final QName _HelloWorld_QNAME;
    private static final QName _HelloWorld2_QNAME;
    private static final QName _HelloWorld2Response_QNAME;
    private static final QName _HelloWorldResponse_QNAME;
    private static final QName _InvalidHinException_QNAME;
    private static final QName _DuplicateHinException_QNAME;
    
    public HelloWorld createHelloWorld() {
        return new HelloWorld();
    }
    
    public HelloWorld2 createHelloWorld2() {
        return new HelloWorld2();
    }
    
    public HelloWorld2Response createHelloWorld2Response() {
        return new HelloWorld2Response();
    }
    
    public HelloWorldResponse createHelloWorldResponse() {
        return new HelloWorldResponse();
    }
    
    public InvalidHinException createInvalidHinException() {
        return new InvalidHinException();
    }
    
    public DuplicateHinException createDuplicateHinException() {
        return new DuplicateHinException();
    }
    
    public MatchingClientParameters createMatchingClientParameters() {
        return new MatchingClientParameters();
    }
    
    public MatchingClientScore createMatchingClientScore() {
        return new MatchingClientScore();
    }
    
    public Client createClient() {
        return new Client();
    }
    
    @XmlElementDecl(namespace = "http://ws.hnr.oscarehr.org/", name = "helloWorld")
    public JAXBElement<HelloWorld> createHelloWorld(final HelloWorld helloWorld) {
        return (JAXBElement<HelloWorld>)new JAXBElement(ObjectFactory._HelloWorld_QNAME, (Class)HelloWorld.class, (Class)null, (Object)helloWorld);
    }
    
    @XmlElementDecl(namespace = "http://ws.hnr.oscarehr.org/", name = "helloWorld2")
    public JAXBElement<HelloWorld2> createHelloWorld2(final HelloWorld2 helloWorld2) {
        return (JAXBElement<HelloWorld2>)new JAXBElement(ObjectFactory._HelloWorld2_QNAME, (Class)HelloWorld2.class, (Class)null, (Object)helloWorld2);
    }
    
    @XmlElementDecl(namespace = "http://ws.hnr.oscarehr.org/", name = "helloWorld2Response")
    public JAXBElement<HelloWorld2Response> createHelloWorld2Response(final HelloWorld2Response helloWorld2Response) {
        return (JAXBElement<HelloWorld2Response>)new JAXBElement(ObjectFactory._HelloWorld2Response_QNAME, (Class)HelloWorld2Response.class, (Class)null, (Object)helloWorld2Response);
    }
    
    @XmlElementDecl(namespace = "http://ws.hnr.oscarehr.org/", name = "helloWorldResponse")
    public JAXBElement<HelloWorldResponse> createHelloWorldResponse(final HelloWorldResponse helloWorldResponse) {
        return (JAXBElement<HelloWorldResponse>)new JAXBElement(ObjectFactory._HelloWorldResponse_QNAME, (Class)HelloWorldResponse.class, (Class)null, (Object)helloWorldResponse);
    }
    
    @XmlElementDecl(namespace = "http://ws.hnr.oscarehr.org/", name = "InvalidHinException")
    public JAXBElement<InvalidHinException> createInvalidHinException(final InvalidHinException ex) {
        return (JAXBElement<InvalidHinException>)new JAXBElement(ObjectFactory._InvalidHinException_QNAME, (Class)InvalidHinException.class, (Class)null, (Object)ex);
    }
    
    @XmlElementDecl(namespace = "http://ws.hnr.oscarehr.org/", name = "DuplicateHinException")
    public JAXBElement<DuplicateHinException> createDuplicateHinException(final DuplicateHinException ex) {
        return (JAXBElement<DuplicateHinException>)new JAXBElement(ObjectFactory._DuplicateHinException_QNAME, (Class)DuplicateHinException.class, (Class)null, (Object)ex);
    }
    
    static {
        _HelloWorld_QNAME = new QName("http://ws.hnr.oscarehr.org/", "helloWorld");
        _HelloWorld2_QNAME = new QName("http://ws.hnr.oscarehr.org/", "helloWorld2");
        _HelloWorld2Response_QNAME = new QName("http://ws.hnr.oscarehr.org/", "helloWorld2Response");
        _HelloWorldResponse_QNAME = new QName("http://ws.hnr.oscarehr.org/", "helloWorldResponse");
        _InvalidHinException_QNAME = new QName("http://ws.hnr.oscarehr.org/", "InvalidHinException");
        _DuplicateHinException_QNAME = new QName("http://ws.hnr.oscarehr.org/", "DuplicateHinException");
    }
}
