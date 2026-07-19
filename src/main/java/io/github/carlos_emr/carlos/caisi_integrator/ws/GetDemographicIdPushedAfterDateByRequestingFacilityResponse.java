package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "getDemographicIdPushedAfterDateByRequestingFacilityResponse", propOrder = { "_return" })
public class GetDemographicIdPushedAfterDateByRequestingFacilityResponse implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlElement(name = "return", type = Integer.class)
    protected List<Integer> _return;
    
    public List<Integer> getReturn() {
        if (this._return == null) {
            this._return = new ArrayList<Integer>();
        }
        return this._return;
    }
}
