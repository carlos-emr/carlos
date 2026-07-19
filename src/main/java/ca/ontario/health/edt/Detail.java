package ca.ontario.health.edt;

import java.util.ArrayList;
import java.math.BigInteger;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "detail", propOrder = { "auditID", "data", "resultSize" })
public class Detail
{
    @XmlElement(required = true)
    protected String auditID;
    protected List<DetailData> data;
    @XmlElement(required = true)
    protected BigInteger resultSize;
    
    public String getAuditID() {
        return this.auditID;
    }
    
    public void setAuditID(final String value) {
        this.auditID = value;
    }
    
    public List<DetailData> getData() {
        if (this.data == null) {
            this.data = new ArrayList<DetailData>();
        }
        return this.data;
    }
    
    public BigInteger getResultSize() {
        return this.resultSize;
    }
    
    public void setResultSize(final BigInteger value) {
        this.resultSize = value;
    }
}
