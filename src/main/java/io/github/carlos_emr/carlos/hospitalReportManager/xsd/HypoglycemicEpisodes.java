package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="hypoglycemicEpisodes", propOrder={"numOfReportedEpisodes", "date"})
public class HypoglycemicEpisodes {
    @XmlElement(name="NumOfReportedEpisodes", required=true)
    protected BigInteger numOfReportedEpisodes;
    @XmlElement(name="Date", required=true)
    protected XMLGregorianCalendar date;

    public BigInteger getNumOfReportedEpisodes() {
        return this.numOfReportedEpisodes;
    }

    public void setNumOfReportedEpisodes(BigInteger value) {
        this.numOfReportedEpisodes = value;
    }

    public XMLGregorianCalendar getDate() {
        return this.date;
    }

    public void setDate(XMLGregorianCalendar value) {
        this.date = value;
    }
}
