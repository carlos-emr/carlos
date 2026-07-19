package ca.ontario.health.ebs;

import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory
{
    private static final QName _EBSFault_QNAME;
    private static final QName _EBS_QNAME;
    
    public EbsFault createEbsFault() {
        return new EbsFault();
    }
    
    public EbsHeader createEbsHeader() {
        return new EbsHeader();
    }
    
    @XmlElementDecl(namespace = "http://ebs.health.ontario.ca/", name = "EBSFault")
    public JAXBElement<EbsFault> createEBSFault(final EbsFault value) {
        return (JAXBElement<EbsFault>)new JAXBElement(ObjectFactory._EBSFault_QNAME, (Class)EbsFault.class, (Class)null, (Object)value);
    }
    
    @XmlElementDecl(namespace = "http://ebs.health.ontario.ca/", name = "EBS")
    public JAXBElement<EbsHeader> createEBS(final EbsHeader value) {
        return (JAXBElement<EbsHeader>)new JAXBElement(ObjectFactory._EBS_QNAME, (Class)EbsHeader.class, (Class)null, (Object)value);
    }
    
    static {
        _EBSFault_QNAME = new QName("http://ebs.health.ontario.ca/", "EBSFault");
        _EBS_QNAME = new QName("http://ebs.health.ontario.ca/", "EBS");
    }
}
