package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="ynIndicatorAndBlank", propOrder={"ynIndicatorsimple", "_boolean", "blank"})
public class YnIndicatorAndBlank {
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String ynIndicatorsimple;
    @XmlElement(name="boolean")
    protected Boolean _boolean;
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String blank;

    public String getYnIndicatorsimple() {
        return this.ynIndicatorsimple;
    }

    public void setYnIndicatorsimple(String value) {
        this.ynIndicatorsimple = value;
    }

    public Boolean isBoolean() {
        return this._boolean;
    }

    public void setBoolean(Boolean value) {
        this._boolean = value;
    }

    public String getBlank() {
        return this.blank;
    }

    public void setBlank(String value) {
        this.blank = value;
    }
}
