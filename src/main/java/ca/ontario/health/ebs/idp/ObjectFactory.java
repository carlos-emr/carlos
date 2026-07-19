package ca.ontario.health.ebs.idp;

import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory
{
    private static final QName _IDP_QNAME;
    
    public IdpHeader createIdpHeader() {
        return new IdpHeader();
    }
    
    @XmlElementDecl(namespace = "http://idp.ebs.health.ontario.ca/", name = "IDP")
    public JAXBElement<IdpHeader> createIDP(final IdpHeader value) {
        return (JAXBElement<IdpHeader>)new JAXBElement(ObjectFactory._IDP_QNAME, (Class)IdpHeader.class, (Class)null, (Object)value);
    }
    
    static {
        _IDP_QNAME = new QName("http://idp.ebs.health.ontario.ca/", "IDP");
    }
}
