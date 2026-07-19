package io.github.carlos_emr.carlos.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import org.w3._2001.xmlschema.Adapter1;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.XmlElement;
import java.util.Calendar;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "matchingClientParameters", propOrder = { "maxEntriesToReturn", "minScore", "firstName", "lastName", "birthDate", "hin" })
public class MatchingClientParameters implements Serializable
{
    private static final long serialVersionUID = 1L;
    protected int maxEntriesToReturn;
    protected int minScore;
    protected String firstName;
    protected String lastName;
    @XmlElement(type = String.class)
    @XmlJavaTypeAdapter(Adapter1.class)
    @XmlSchemaType(name = "dateTime")
    protected Calendar birthDate;
    protected String hin;
    
    public int getMaxEntriesToReturn() {
        return this.maxEntriesToReturn;
    }
    
    public void setMaxEntriesToReturn(final int maxEntriesToReturn) {
        this.maxEntriesToReturn = maxEntriesToReturn;
    }
    
    public int getMinScore() {
        return this.minScore;
    }
    
    public void setMinScore(final int minScore) {
        this.minScore = minScore;
    }
    
    public String getFirstName() {
        return this.firstName;
    }
    
    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return this.lastName;
    }
    
    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }
    
    public Calendar getBirthDate() {
        return this.birthDate;
    }
    
    public void setBirthDate(final Calendar birthDate) {
        this.birthDate = birthDate;
    }
    
    public String getHin() {
        return this.hin;
    }
    
    public void setHin(final String hin) {
        this.hin = hin;
    }
}
