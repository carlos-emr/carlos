package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "csnData", propOrder = { "soloCsn", "groupCsn" })
public class CsnData
{
    protected String soloCsn;
    protected String groupCsn;
    
    public String getSoloCsn() {
        return this.soloCsn;
    }
    
    public void setSoloCsn(final String value) {
        this.soloCsn = value;
    }
    
    public String getGroupCsn() {
        return this.groupCsn;
    }
    
    public void setGroupCsn(final String value) {
        this.groupCsn = value;
    }
}
