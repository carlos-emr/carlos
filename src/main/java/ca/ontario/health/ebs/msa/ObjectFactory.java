package ca.ontario.health.ebs.msa;

import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory
{
    private static final QName _MSA_QNAME;
    
    public MsaHeader createMsaHeader() {
        return new MsaHeader();
    }
    
    @XmlElementDecl(namespace = "http://msa.ebs.health.ontario.ca/", name = "MSA")
    public JAXBElement<MsaHeader> createMSA(final MsaHeader value) {
        return (JAXBElement<MsaHeader>)new JAXBElement(ObjectFactory._MSA_QNAME, (Class)MsaHeader.class, (Class)null, (Object)value);
    }
    
    static {
        _MSA_QNAME = new QName("http://msa.ebs.health.ontario.ca/", "MSA");
    }
}
