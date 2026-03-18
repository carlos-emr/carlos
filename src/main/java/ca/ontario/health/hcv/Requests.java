package ca.ontario.health.hcv;

import java.util.ArrayList;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "requests", propOrder = { "hcvRequest" })
public class Requests
{
    @XmlElement(required = true)
    protected List<HcvRequest> hcvRequest;
    
    public List<HcvRequest> getHcvRequest() {
        if (this.hcvRequest == null) {
            this.hcvRequest = new ArrayList<HcvRequest>();
        }
        return this.hcvRequest;
    }
}
