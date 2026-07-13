package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="drugMeasure", propOrder={})
public class DrugMeasure {
    @XmlElement(name="Amount", required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    @XmlSchemaType(name="token")
    protected String amount;
    @XmlElement(name="UnitOfMeasure", required=true)
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    @XmlSchemaType(name="token")
    protected String unitOfMeasure;

    public String getAmount() {
        return this.amount;
    }

    public void setAmount(String value) {
        this.amount = value;
    }

    public String getUnitOfMeasure() {
        return this.unitOfMeasure;
    }

    public void setUnitOfMeasure(String value) {
        this.unitOfMeasure = value;
    }
}
