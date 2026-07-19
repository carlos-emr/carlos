package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cachedProvider", propOrder = { "facilityIdStringCompositePk", "firstName", "lastName", "specialty", "workPhone" })
public class CachedProvider extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdStringCompositePk facilityIdStringCompositePk;
    protected String firstName;
    protected String lastName;
    protected String specialty;
    protected String workPhone;
    
    public FacilityIdStringCompositePk getFacilityIdStringCompositePk() {
        return this.facilityIdStringCompositePk;
    }
    
    public void setFacilityIdStringCompositePk(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        this.facilityIdStringCompositePk = facilityIdStringCompositePk;
    }
    
    public String getFirstName() {
        return this.firstName;
    }
    
    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return this.lastName;
    }
    
    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }
    
    public String getSpecialty() {
        return this.specialty;
    }
    
    public void setSpecialty(final String specialty) {
        this.specialty = specialty;
    }
    
    public String getWorkPhone() {
        return this.workPhone;
    }
    
    public void setWorkPhone(final String workPhone) {
        this.workPhone = workPhone;
    }
}
