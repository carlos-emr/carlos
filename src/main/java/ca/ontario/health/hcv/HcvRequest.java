package ca.ontario.health.hcv;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "hcvRequest", propOrder = { "healthNumber", "versionCode", "feeServiceCodes" })
public class HcvRequest
{
    @XmlElement(required = true)
    protected String healthNumber;
    @XmlElement(required = true)
    protected String versionCode;
    @XmlElement(nillable = true)
    protected List<String> feeServiceCodes;
    
    public String getHealthNumber() {
        return this.healthNumber;
    }
    
    public void setHealthNumber(final String value) {
        this.healthNumber = value;
    }
    
    public String getVersionCode() {
        return this.versionCode;
    }
    
    public void setVersionCode(final String value) {
        this.versionCode = value;
    }
    
    public List<String> getFeeServiceCodes() {
        if (this.feeServiceCodes == null) {
            this.feeServiceCodes = new ArrayList<String>();
        }
        return this.feeServiceCodes;
    }
}
