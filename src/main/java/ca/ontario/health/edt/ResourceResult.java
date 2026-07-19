package ca.ontario.health.edt;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "resourceResult", propOrder = { "auditID", "response" })
public class ResourceResult
{
    @XmlElement(required = true)
    protected String auditID;
    @XmlElement(required = true)
    protected List<ResponseResult> response;
    
    public String getAuditID() {
        return this.auditID;
    }
    
    public void setAuditID(final String value) {
        this.auditID = value;
    }
    
    public List<ResponseResult> getResponse() {
        if (this.response == null) {
            this.response = new ArrayList<ResponseResult>();
        }
        return this.response;
    }
}
