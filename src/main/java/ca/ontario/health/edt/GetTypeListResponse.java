package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

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
