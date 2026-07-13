package ca.ontario.health.edt;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "typeListResult", propOrder = { "auditID", "data" })
public class TypeListResult
{
    @XmlElement(required = true)
    protected String auditID;
    @XmlElement(required = true)
    protected List<TypeListData> data;
    
    public String getAuditID() {
        return this.auditID;
    }
    
    public void setAuditID(final String value) {
        this.auditID = value;
    }
    
    public List<TypeListData> getData() {
        if (this.data == null) {
            this.data = new ArrayList<TypeListData>();
        }
        return this.data;
    }
}
