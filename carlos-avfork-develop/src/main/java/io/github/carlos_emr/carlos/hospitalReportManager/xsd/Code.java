package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="code", propOrder={})
public class Code {
    @XmlElement(name="CodingSystem", required=true)
    protected String codingSystem;
    @XmlElement(required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String value;
    @XmlElement(name="Description")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String description;

    public String getCodingSystem() {
        return this.codingSystem;
    }

    public void setCodingSystem(String value) {
        this.codingSystem = value;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String value) {
        this.description = value;
    }
}
