package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "updateResponse", propOrder = { "_return" })
public class UpdateResponse
{
    @XmlElement(name = "return", required = true)
    protected ResourceResult _return;
    
    public ResourceResult getReturn() {
        return this._return;
    }
    
    public void setReturn(final ResourceResult value) {
        this._return = value;
    }
}
