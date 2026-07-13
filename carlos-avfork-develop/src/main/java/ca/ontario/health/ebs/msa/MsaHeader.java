package ca.ontario.health.ebs.msa;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "msa_header", propOrder = { "serviceUserMUID", "userID" })
public class MsaHeader
{
    @XmlElement(name = "ServiceUserMUID", required = true)
    protected String serviceUserMUID;
    @XmlElement(name = "UserID", required = true)
    protected String userID;
    
    public String getServiceUserMUID() {
        return this.serviceUserMUID;
    }
    
    public void setServiceUserMUID(final String value) {
        this.serviceUserMUID = value;
    }
    
    public String getUserID() {
        return this.userID;
    }
    
    public void setUserID(final String value) {
        this.userID = value;
    }
}
