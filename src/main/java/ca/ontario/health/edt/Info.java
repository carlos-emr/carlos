package ca.ontario.health.edt;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.math.BigInteger;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "info", propOrder = { "resourceIDs" })
public class Info
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
