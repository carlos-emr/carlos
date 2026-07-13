package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="dateFullOrPartial", propOrder={"yearOnly", "yearMonth", "fullDate", "dateTime"})
public class DateFullOrPartial {
    @XmlElement(name="YearOnly")
    protected XMLGregorianCalendar yearOnly;
    @XmlElement(name="YearMonth")
    protected XMLGregorianCalendar yearMonth;
    @XmlElement(name="FullDate")
    @XmlSchemaType(name="date")
    protected XMLGregorianCalendar fullDate;
    @XmlElement(name="DateTime")
    @XmlSchemaType(name="dateTime")
    protected XMLGregorianCalendar dateTime;

    public XMLGregorianCalendar getYearOnly() {
        return this.yearOnly;
    }

    public void setYearOnly(XMLGregorianCalendar value) {
        this.yearOnly = value;
    }

    public XMLGregorianCalendar getYearMonth() {
        return this.yearMonth;
    }

    public void setYearMonth(XMLGregorianCalendar value) {
        this.yearMonth = value;
    }

    public XMLGregorianCalendar getFullDate() {
        return this.fullDate;
    }

    public void setFullDate(XMLGregorianCalendar value) {
        this.fullDate = value;
    }

    public XMLGregorianCalendar getDateTime() {
        return this.dateTime;
    }

    public void setDateTime(XMLGregorianCalendar value) {
        this.dateTime = value;
    }
}
