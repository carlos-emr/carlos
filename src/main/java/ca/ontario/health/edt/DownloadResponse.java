package ca.ontario.health.edt;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

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
