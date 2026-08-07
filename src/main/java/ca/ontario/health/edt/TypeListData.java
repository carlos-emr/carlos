package ca.ontario.health.edt;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "typeListData", propOrder = { "access", "descriptionEn", "descriptionFr", "groupRequired", "resourceType", "result", "csns" })
public class TypeListData
{
    @XmlElement(required = true)
    protected ResourceAccess access;
    @XmlElement(required = true)
    protected String descriptionEn;
    @XmlElement(required = true)
    protected String descriptionFr;
    protected boolean groupRequired;
    @XmlElement(required = true)
    protected String resourceType;
    @XmlElement(required = true)
    protected CommonResult result;
    protected List<CsnData> csns;
    
    public ResourceAccess getAccess() {
        return this.access;
    }
    
    public void setAccess(final ResourceAccess value) {
        this.access = value;
    }
    
    public String getDescriptionEn() {
        return this.descriptionEn;
    }
    
    public void setDescriptionEn(final String value) {
        this.descriptionEn = value;
    }
    
    public String getDescriptionFr() {
        return this.descriptionFr;
    }
    
    public void setDescriptionFr(final String value) {
        this.descriptionFr = value;
    }
    
    public boolean isGroupRequired() {
        return this.groupRequired;
    }
    
    public void setGroupRequired(final boolean value) {
        this.groupRequired = value;
    }
    
    public String getResourceType() {
        return this.resourceType;
    }
    
    public void setResourceType(final String value) {
        this.resourceType = value;
    }
    
    public CommonResult getResult() {
        return this.result;
    }
    
    public void setResult(final CommonResult value) {
        this.result = value;
    }
    
    public List<CsnData> getCsns() {
        if (this.csns == null) {
            this.csns = new ArrayList<CsnData>();
        }
        return this.csns;
    }
}
