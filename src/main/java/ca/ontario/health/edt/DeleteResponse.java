package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "deleteResponse", propOrder = { "_return" })
public class DeleteResponse
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
