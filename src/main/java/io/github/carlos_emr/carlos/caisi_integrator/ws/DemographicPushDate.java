package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "demographicPushDate", propOrder = { "id", "lastPushDate" })
public class DemographicPushDate extends AbstractModel implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected FacilityIdIntegerCompositePk id;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar lastPushDate;
    
    public FacilityIdIntegerCompositePk getId() {
        return this.id;
    }
    
    public void setId(final FacilityIdIntegerCompositePk id) {
        this.id = id;
    }
    
    public Calendar getLastPushDate() {
        return this.lastPushDate;
    }
    
    public void setLastPushDate(final Calendar lastPushDate) {
        this.lastPushDate = lastPushDate;
    }
}
