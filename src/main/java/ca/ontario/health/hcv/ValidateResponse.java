package ca.ontario.health.hcv;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "validateResponse", propOrder = { "results" })
public class ValidateResponse
{
    @XmlElement(required = true)
    protected HcvResults results;
    
    public HcvResults getResults() {
        return this.results;
    }
    
    public void setResults(final HcvResults value) {
        this.results = value;
    }
}
