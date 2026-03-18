package ca.ontario.health.ebs.idp;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

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
