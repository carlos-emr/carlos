package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedMeasurementExt", propOrder = { "facilityIdIntegerCompositePk", "keyval", "measurementId", "val" })
public class CachedMeasurementExt extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String keyval;
    protected Integer measurementId;
    protected String val;
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public String getKeyval() {
        return this.keyval;
    }
    
    public void setKeyval(final String keyval) {
        this.keyval = keyval;
    }
    
    public Integer getMeasurementId() {
        return this.measurementId;
    }
    
    public void setMeasurementId(final Integer measurementId) {
        this.measurementId = measurementId;
    }
    
    public String getVal() {
        return this.val;
    }
    
    public void setVal(final String val) {
        this.val = val;
    }
}
