package ca.ontario.health.edt;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

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
