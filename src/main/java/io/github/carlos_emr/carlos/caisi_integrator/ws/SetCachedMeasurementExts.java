package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "setCachedMeasurementExts", propOrder = { "arg0" })
public class SetCachedMeasurementExts implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected List<CachedMeasurementExt> arg0;
    
    public List<CachedMeasurementExt> getArg0() {
        if (this.arg0 == null) {
            this.arg0 = new ArrayList<CachedMeasurementExt>();
        }
        return this.arg0;
    }
}
