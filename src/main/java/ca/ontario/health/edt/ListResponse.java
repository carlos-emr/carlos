package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "listResponse", propOrder = { "_return" })
public class ListResponse
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
