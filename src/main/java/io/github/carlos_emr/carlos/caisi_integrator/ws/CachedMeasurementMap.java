package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedMeasurementMap", propOrder = { "facilityIdIntegerCompositePk", "identCode", "labType", "loincCode", "name" })
public class CachedMeasurementMap extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String identCode;
    protected String labType;
    protected String loincCode;
    protected String name;
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public String getIdentCode() {
        return this.identCode;
    }
    
    public void setIdentCode(final String identCode) {
        this.identCode = identCode;
    }
    
    public String getLabType() {
        return this.labType;
    }
    
    public void setLabType(final String labType) {
        this.labType = labType;
    }
    
    public String getLoincCode() {
        return this.loincCode;
    }
    
    public void setLoincCode(final String loincCode) {
        this.loincCode = loincCode;
    }
    
    public String getName() {
        return this.name;
    }
    
    public void setName(final String name) {
        this.name = name;
    }
}
