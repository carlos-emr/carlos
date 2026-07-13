package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.math.BigDecimal;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="smokingPacks", propOrder={"perDay", "date"})
public class SmokingPacks {
    @XmlElement(name="PerDay", required=true)
    protected BigDecimal perDay;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public BigDecimal getPerDay() {
        return this.perDay;
    }

    public void setPerDay(BigDecimal value) {
        this.perDay = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
