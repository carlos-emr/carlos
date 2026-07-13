package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getTypeListResponse", propOrder = { "_return" })
public class GetTypeListResponse
{
    @XmlElement(name = "return", required = true)
    protected TypeListResult _return;
    
    public TypeListResult getReturn() {
        return this._return;
    }
    
    public void setReturn(final TypeListResult value) {
        this._return = value;
    }
}
