package ca.ontario.health.hcv;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "person", propOrder = { "dateOfBirth", "expiryDate", "firstName", "gender", "healthNumber", "lastName", "responseAction", "responseCode", "responseDescription", "responseID", "secondName", "versionCode", "feeServiceDetails" })
public class Person
{
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar dateOfBirth;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar expiryDate;
    protected String firstName;
    protected String gender;
    @XmlElement(required = true)
    protected String healthNumber;
    protected String lastName;
    @XmlElement(required = true)
    protected String responseAction;
    @XmlElement(required = true)
    protected String responseCode;
    @XmlElement(required = true)
    protected String responseDescription;
    @XmlElement(required = true)
    protected ResponseID responseID;
    protected String secondName;
    @XmlElement(required = true)
    protected String versionCode;
    protected List<FeeServiceDetails> feeServiceDetails;
    
    public XMLGregorianCalendar getDateOfBirth() {
        return this.dateOfBirth;
    }
    
    public void setDateOfBirth(final XMLGregorianCalendar value) {
        this.dateOfBirth = value;
    }
    
    public XMLGregorianCalendar getExpiryDate() {
        return this.expiryDate;
    }
    
    public void setExpiryDate(final XMLGregorianCalendar value) {
        this.expiryDate = value;
    }
    
    public String getFirstName() {
        return this.firstName;
    }
    
    public void setFirstName(final String value) {
        this.firstName = value;
    }
    
    public String getGender() {
        return this.gender;
    }
    
    public void setGender(final String value) {
        this.gender = value;
    }
    
    public String getHealthNumber() {
        return this.healthNumber;
    }
    
    public void setHealthNumber(final String value) {
        this.healthNumber = value;
    }
    
    public String getLastName() {
        return this.lastName;
    }
    
    public void setLastName(final String value) {
        this.lastName = value;
    }
    
    public String getResponseAction() {
        return this.responseAction;
    }
    
    public void setResponseAction(final String value) {
        this.responseAction = value;
    }
    
    public String getResponseCode() {
        return this.responseCode;
    }
    
    public void setResponseCode(final String value) {
        this.responseCode = value;
    }
    
    public String getResponseDescription() {
        return this.responseDescription;
    }
    
    public void setResponseDescription(final String value) {
        this.responseDescription = value;
    }
    
    public ResponseID getResponseID() {
        return this.responseID;
    }
    
    public void setResponseID(final ResponseID value) {
        this.responseID = value;
    }
    
    public String getSecondName() {
        return this.secondName;
    }
    
    public void setSecondName(final String value) {
        this.secondName = value;
    }
    
    public String getVersionCode() {
        return this.versionCode;
    }
    
    public void setVersionCode(final String value) {
        this.versionCode = value;
    }
    
    public List<FeeServiceDetails> getFeeServiceDetails() {
        if (this.feeServiceDetails == null) {
            this.feeServiceDetails = new ArrayList<FeeServiceDetails>();
        }
        return this.feeServiceDetails;
    }
}
