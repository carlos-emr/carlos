package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="", propOrder={"messageUniqueID", "deliverToUserID", "providers"})
@XmlRootElement(name="TransactionInformation", namespace="cds")
public class TransactionInformation {
    @XmlElement(name="MessageUniqueID", namespace="cds", required=true)
    protected String messageUniqueID;
    @XmlElement(name="DeliverToUserID", namespace="cds", required=true)
    protected String deliverToUserID;
    @XmlElement(name="Provider", namespace="cds", required=true)
    protected PersonNameSimple provider;

    public String getMessageUniqueID() {
        return this.messageUniqueID;
    }

    public void setMessageUniqueID(String value) {
        this.messageUniqueID = value;
    }

    public String getDeliverToUserID() {
        return this.deliverToUserID;
    }

    public void setDeliverToUserID(String value) {
        this.deliverToUserID = value;
    }

    public PersonNameSimple getProvider() {
        return this.provider;
    }

    public void setProvider(PersonNameSimple value) {
        this.provider = value;
    }
}
