package ca.ontario.health.edt;

import java.util.ArrayList;
import jakarta.xml.bind.annotation.XmlElement;
import java.math.BigInteger;
import java.util.List;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "download", propOrder = { "resourceIDs" })
public class Download
{
    @XmlElement(required = true)
    protected List<BigInteger> resourceIDs;
    
    public List<BigInteger> getResourceIDs() {
        if (this.resourceIDs == null) {
            this.resourceIDs = new ArrayList<BigInteger>();
        }
        return this.resourceIDs;
    }
}
