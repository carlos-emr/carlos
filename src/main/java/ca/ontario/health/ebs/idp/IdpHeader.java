package ca.ontario.health.ebs.idp;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "idp_header", propOrder = { "serviceUserMUID" })
public class IdpHeader
{
    @XmlElement(name = "ServiceUserMUID", required = true)
    protected String serviceUserMUID;
    
    public String getServiceUserMUID() {
        return this.serviceUserMUID;
    }
    
    public void setServiceUserMUID(final String value) {
        this.serviceUserMUID = value;
    }
}
