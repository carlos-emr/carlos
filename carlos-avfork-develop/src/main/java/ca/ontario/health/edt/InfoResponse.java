package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "infoResponse", propOrder = { "_return" })
public class InfoResponse
{
    @XmlElement(name = "return", required = true)
    protected Detail _return;
    
    public Detail getReturn() {
        return this._return;
    }
    
    public void setReturn(final Detail value) {
        this._return = value;
    }
}
