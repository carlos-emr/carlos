package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory
{
    private static final QName _GetHnrClient_QNAME;
    private static final QName _GetHnrClientResponse_QNAME;
    private static final QName _GetMatchingHnrClients_QNAME;
    private static final QName _GetMatchingHnrClientsResponse_QNAME;
    private static final QName _SetHnrClientData_QNAME;
    private static final QName _SetHnrClientDataResponse_QNAME;
    private static final QName _SetHnrClientHidden_QNAME;
    private static final QName _SetHnrClientHiddenResponse_QNAME;
    private static final QName _ConnectException_QNAME;
    
    public GetHnrClient createGetHnrClient() {
        return new GetHnrClient();
    }
    
    public GetHnrClientResponse createGetHnrClientResponse() {
        return new GetHnrClientResponse();
    }
    
    public GetMatchingHnrClients createGetMatchingHnrClients() {
        return new GetMatchingHnrClients();
    }
    
    public GetMatchingHnrClientsResponse createGetMatchingHnrClientsResponse() {
        return new GetMatchingHnrClientsResponse();
    }
    
    public SetHnrClientData createSetHnrClientData() {
        return new SetHnrClientData();
    }
    
    public SetHnrClientDataResponse createSetHnrClientDataResponse() {
        return new SetHnrClientDataResponse();
    }
    
    public SetHnrClientHidden createSetHnrClientHidden() {
        return new SetHnrClientHidden();
    }
    
    public SetHnrClientHiddenResponse createSetHnrClientHiddenResponse() {
        return new SetHnrClientHiddenResponse();
    }
    
    public ConnectException createConnectException() {
        return new ConnectException();
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "getHnrClient")
    public JAXBElement<GetHnrClient> createGetHnrClient(final GetHnrClient getHnrClient) {
        return (JAXBElement<GetHnrClient>)new JAXBElement(ObjectFactory._GetHnrClient_QNAME, (Class)GetHnrClient.class, (Class)null, (Object)getHnrClient);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "getHnrClientResponse")
    public JAXBElement<GetHnrClientResponse> createGetHnrClientResponse(final GetHnrClientResponse getHnrClientResponse) {
        return (JAXBElement<GetHnrClientResponse>)new JAXBElement(ObjectFactory._GetHnrClientResponse_QNAME, (Class)GetHnrClientResponse.class, (Class)null, (Object)getHnrClientResponse);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "getMatchingHnrClients")
    public JAXBElement<GetMatchingHnrClients> createGetMatchingHnrClients(final GetMatchingHnrClients getMatchingHnrClients) {
        return (JAXBElement<GetMatchingHnrClients>)new JAXBElement(ObjectFactory._GetMatchingHnrClients_QNAME, (Class)GetMatchingHnrClients.class, (Class)null, (Object)getMatchingHnrClients);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "getMatchingHnrClientsResponse")
    public JAXBElement<GetMatchingHnrClientsResponse> createGetMatchingHnrClientsResponse(final GetMatchingHnrClientsResponse getMatchingHnrClientsResponse) {
        return (JAXBElement<GetMatchingHnrClientsResponse>)new JAXBElement(ObjectFactory._GetMatchingHnrClientsResponse_QNAME, (Class)GetMatchingHnrClientsResponse.class, (Class)null, (Object)getMatchingHnrClientsResponse);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "setHnrClientData")
    public JAXBElement<SetHnrClientData> createSetHnrClientData(final SetHnrClientData setHnrClientData) {
        return (JAXBElement<SetHnrClientData>)new JAXBElement(ObjectFactory._SetHnrClientData_QNAME, (Class)SetHnrClientData.class, (Class)null, (Object)setHnrClientData);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "setHnrClientDataResponse")
    public JAXBElement<SetHnrClientDataResponse> createSetHnrClientDataResponse(final SetHnrClientDataResponse setHnrClientDataResponse) {
        return (JAXBElement<SetHnrClientDataResponse>)new JAXBElement(ObjectFactory._SetHnrClientDataResponse_QNAME, (Class)SetHnrClientDataResponse.class, (Class)null, (Object)setHnrClientDataResponse);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "setHnrClientHidden")
    public JAXBElement<SetHnrClientHidden> createSetHnrClientHidden(final SetHnrClientHidden setHnrClientHidden) {
        return (JAXBElement<SetHnrClientHidden>)new JAXBElement(ObjectFactory._SetHnrClientHidden_QNAME, (Class)SetHnrClientHidden.class, (Class)null, (Object)setHnrClientHidden);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "setHnrClientHiddenResponse")
    public JAXBElement<SetHnrClientHiddenResponse> createSetHnrClientHiddenResponse(final SetHnrClientHiddenResponse setHnrClientHiddenResponse) {
        return (JAXBElement<SetHnrClientHiddenResponse>)new JAXBElement(ObjectFactory._SetHnrClientHiddenResponse_QNAME, (Class)SetHnrClientHiddenResponse.class, (Class)null, (Object)setHnrClientHiddenResponse);
    }
    
    @XmlElementDecl(namespace = "http://ws.caisi_integrator.oscarehr.org/", name = "ConnectException")
    public JAXBElement<ConnectException> createConnectException(final ConnectException ex) {
        return (JAXBElement<ConnectException>)new JAXBElement(ObjectFactory._ConnectException_QNAME, (Class)ConnectException.class, (Class)null, (Object)ex);
    }
    
    static {
        _GetHnrClient_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "getHnrClient");
        _GetHnrClientResponse_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "getHnrClientResponse");
        _GetMatchingHnrClients_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "getMatchingHnrClients");
        _GetMatchingHnrClientsResponse_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "getMatchingHnrClientsResponse");
        _SetHnrClientData_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "setHnrClientData");
        _SetHnrClientDataResponse_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "setHnrClientDataResponse");
        _SetHnrClientHidden_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "setHnrClientHidden");
        _SetHnrClientHiddenResponse_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "setHnrClientHiddenResponse");
        _ConnectException_QNAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "ConnectException");
    }
}
