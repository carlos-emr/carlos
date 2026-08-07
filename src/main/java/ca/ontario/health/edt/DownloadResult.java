package ca.ontario.health.edt;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "downloadResult", propOrder = { "auditID", "data" })
public class DownloadResult
{
    @XmlElement(required = true)
    protected String auditID;
    protected List<DownloadData> data;
    
    public String getAuditID() {
        return this.auditID;
    }
    
    public void setAuditID(final String value) {
        this.auditID = value;
    }
    
    public List<DownloadData> getData() {
        if (this.data == null) {
            this.data = new ArrayList<DownloadData>();
        }
        return this.data;
    }
}
