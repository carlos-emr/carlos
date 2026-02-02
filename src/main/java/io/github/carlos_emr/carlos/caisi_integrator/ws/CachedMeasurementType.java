package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedMeasurementType", propOrder = { "facilityIdIntegerCompositePk", "measuringInstruction", "type", "typeDescription" })
public class CachedMeasurementType extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    protected String measuringInstruction;
    protected String type;
    protected String typeDescription;
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return this.facilityIdIntegerCompositePk;
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        this.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
    }
    
    public String getMeasuringInstruction() {
        return this.measuringInstruction;
    }
    
    public void setMeasuringInstruction(final String measuringInstruction) {
        this.measuringInstruction = measuringInstruction;
    }
    
    public String getType() {
        return this.type;
    }
    
    public void setType(final String type) {
        this.type = type;
    }
    
    public String getTypeDescription() {
        return this.typeDescription;
    }
    
    public void setTypeDescription(final String typeDescription) {
        this.typeDescription = typeDescription;
    }
}
