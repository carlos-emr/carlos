package ca.ontario.health.hcv;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "feeServiceDetails", propOrder = { "feeServiceCode", "feeServiceDate", "feeServiceResponseCode", "feeServiceResponseDescription" })
public class FeeServiceDetails
{
    @XmlElement(required = true)
    protected String feeServiceCode;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar feeServiceDate;
    @XmlElement(required = true)
    protected String feeServiceResponseCode;
    @XmlElement(required = true)
    protected String feeServiceResponseDescription;
    
    public String getFeeServiceCode() {
        return this.feeServiceCode;
    }
    
    public void setFeeServiceCode(final String value) {
        this.feeServiceCode = value;
    }
    
    public XMLGregorianCalendar getFeeServiceDate() {
        return this.feeServiceDate;
    }
    
    public void setFeeServiceDate(final XMLGregorianCalendar value) {
        this.feeServiceDate = value;
    }
    
    public String getFeeServiceResponseCode() {
        return this.feeServiceResponseCode;
    }
    
    public void setFeeServiceResponseCode(final String value) {
        this.feeServiceResponseCode = value;
    }
    
    public String getFeeServiceResponseDescription() {
        return this.feeServiceResponseDescription;
    }
    
    public void setFeeServiceResponseDescription(final String value) {
        this.feeServiceResponseDescription = value;
    }
}
