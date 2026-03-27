package ca.ontario.health.edt;

import java.util.ArrayList;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "upload", propOrder = { "upload" })
public class Upload
{
    @XmlElement(required = true)
    protected List<UploadData> upload;
    
    public List<UploadData> getUpload() {
        if (this.upload == null) {
            this.upload = new ArrayList<UploadData>();
        }
        return this.upload;
    }
}
