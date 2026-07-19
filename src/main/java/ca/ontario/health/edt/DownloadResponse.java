package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "downloadResponse", propOrder = { "_return" })
public class DownloadResponse
{
    @XmlElement(name = "return", required = true)
    protected DownloadResult _return;
    
    public DownloadResult getReturn() {
        return this._return;
    }
    
    public void setReturn(final DownloadResult value) {
        this._return = value;
    }
}
