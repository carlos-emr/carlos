package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "commonResult", propOrder = { "code", "msg" })
public class CommonResult
{
    @XmlElement(required = true)
    protected String code;
    @XmlElement(required = true)
    protected String msg;
    
    public String getCode() {
        return this.code;
    }
    
    public void setCode(final String value) {
        this.code = value;
    }
    
    public String getMsg() {
        return this.msg;
    }
    
    public void setMsg(final String value) {
        this.msg = value;
    }
}
