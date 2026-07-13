package ca.ontario.health.edt;

import java.util.ArrayList;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "update", propOrder = { "updates" })
public class Update
{
    @XmlElement(required = true)
    protected List<UpdateRequest> updates;
    
    public List<UpdateRequest> getUpdates() {
        if (this.updates == null) {
            this.updates = new ArrayList<UpdateRequest>();
        }
        return this.updates;
    }
}
