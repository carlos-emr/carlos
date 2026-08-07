package ca.ontario.health.hcv;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "hcvResults", propOrder = { "auditUID", "results" })
public class HcvResults
{
    @XmlElement(required = true)
    protected String auditUID;
    @XmlElement(required = true, nillable = true)
    protected List<Person> results;
    
    public String getAuditUID() {
        return this.auditUID;
    }
    
    public void setAuditUID(final String value) {
        this.auditUID = value;
    }
    
    public List<Person> getResults() {
        if (this.results == null) {
            this.results = new ArrayList<Person>();
        }
        return this.results;
    }
}
