package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DateFullOrPartial;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportClass;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportContent;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportFormat;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportMedia;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="", propOrder={"media", "format", "fileExtensionAndVersion", "content", "clazz", "subClass", "eventDateTime", "receivedDateTime", "reviewedDateTime", "authorPhysician", "reviewingOHIPPhysicianId", "sendingFacility", "sendingFacilityReportNumber", "obrContent", "resultStatus"})
@XmlRootElement(name="ReportsReceived", namespace="cds")
public class ReportsReceived {
    @XmlElement(name="Media", namespace="cds")
    protected ReportMedia media;
    @XmlElement(name="Format", namespace="cds")
    protected ReportFormat format;
    @XmlElement(name="FileExtensionAndVersion", namespace="cds", required=true)
    protected String fileExtensionAndVersion;
    @XmlElement(name="Content", namespace="cds")
    protected ReportContent content;
    @XmlElement(name="Class", namespace="cds")
    protected ReportClass clazz;
    @XmlElement(name="SubClass", namespace="cds")
    protected String subClass;
    @XmlElement(name="EventDateTime", namespace="cds")
    protected DateFullOrPartial eventDateTime;
    @XmlElement(name="ReceivedDateTime", namespace="cds")
    protected DateFullOrPartial receivedDateTime;
    @XmlElement(name="ReviewedDateTime", namespace="cds")
    protected DateFullOrPartial reviewedDateTime;
    @XmlElement(name="AuthorPhysician", namespace="cds")
    protected PersonNameSimple authorPhysician;
    @XmlElement(name="ReviewingOHIPPhysicianId", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String reviewingOHIPPhysicianId;
    @XmlElement(name="SendingFacility", namespace="cds")
    @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
    protected String sendingFacility;
    @XmlElement(name="SendingFacilityReportNumber", namespace="cds")
    protected String sendingFacilityReportNumber;
    @XmlElement(name="OBRContent", namespace="cds")
    protected List<OBRContent> obrContent;
    @XmlElement(name="ResultStatus", namespace="cds")
    protected String resultStatus;

    public ReportMedia getMedia() {
        return this.media;
    }

    public void setMedia(ReportMedia value) {
        this.media = value;
    }

    public ReportFormat getFormat() {
        return this.format;
    }

    public void setFormat(ReportFormat value) {
        this.format = value;
    }

    public String getFileExtensionAndVersion() {
        return this.fileExtensionAndVersion;
    }

    public void setFileExtensionAndVersion(String value) {
        this.fileExtensionAndVersion = value;
    }

    public ReportContent getContent() {
        return this.content;
    }

    public void setContent(ReportContent value) {
        this.content = value;
    }

    public ReportClass getClazz() {
        return this.clazz;
    }

    public void setClazz(ReportClass value) {
        this.clazz = value;
    }

    public String getSubClass() {
        return this.subClass;
    }

    public void setSubClass(String value) {
        this.subClass = value;
    }

    public DateFullOrPartial getEventDateTime() {
        return this.eventDateTime;
    }

    public void setEventDateTime(DateFullOrPartial value) {
        this.eventDateTime = value;
    }

    public DateFullOrPartial getReceivedDateTime() {
        return this.receivedDateTime;
    }

    public void setReceivedDateTime(DateFullOrPartial value) {
        this.receivedDateTime = value;
    }

    public DateFullOrPartial getReviewedDateTime() {
        return this.reviewedDateTime;
    }

    public void setReviewedDateTime(DateFullOrPartial value) {
        this.reviewedDateTime = value;
    }

    public PersonNameSimple getAuthorPhysician() {
        return this.authorPhysician;
    }

    public void setAuthorPhysician(PersonNameSimple value) {
        this.authorPhysician = value;
    }

    public String getReviewingOHIPPhysicianId() {
        return this.reviewingOHIPPhysicianId;
    }

    public void setReviewingOHIPPhysicianId(String value) {
        this.reviewingOHIPPhysicianId = value;
    }

    public String getSendingFacility() {
        return this.sendingFacility;
    }

    public void setSendingFacility(String value) {
        this.sendingFacility = value;
    }

    public String getSendingFacilityReportNumber() {
        return this.sendingFacilityReportNumber;
    }

    public void setSendingFacilityReportNumber(String value) {
        this.sendingFacilityReportNumber = value;
    }

    public List<OBRContent> getOBRContent() {
        if (this.obrContent == null) {
            this.obrContent = new ArrayList<OBRContent>();
        }
        return this.obrContent;
    }

    public String getResultStatus() {
        return this.resultStatus;
    }

    public void setResultStatus(String value) {
        this.resultStatus = value;
    }

    @XmlAccessorType(value=XmlAccessType.FIELD)
    @XmlType(name="", propOrder={"accompanyingSubClass", "accompanyingMnemonic", "accompanyingDescription", "observationDateTime"})
    public static class OBRContent {
        @XmlElement(name="AccompanyingSubClass", namespace="cds")
        protected String accompanyingSubClass;
        @XmlElement(name="AccompanyingMnemonic", namespace="cds")
        protected String accompanyingMnemonic;
        @XmlElement(name="AccompanyingDescription", namespace="cds")
        protected String accompanyingDescription;
        @XmlElement(name="ObservationDateTime", namespace="cds")
        protected DateFullOrPartial observationDateTime;

        public String getAccompanyingSubClass() {
            return this.accompanyingSubClass;
        }

        public void setAccompanyingSubClass(String value) {
            this.accompanyingSubClass = value;
        }

        public String getAccompanyingMnemonic() {
            return this.accompanyingMnemonic;
        }

        public void setAccompanyingMnemonic(String value) {
            this.accompanyingMnemonic = value;
        }

        public String getAccompanyingDescription() {
            return this.accompanyingDescription;
        }

        public void setAccompanyingDescription(String value) {
            this.accompanyingDescription = value;
        }

        public DateFullOrPartial getObservationDateTime() {
            return this.observationDateTime;
        }

        public void setObservationDateTime(DateFullOrPartial value) {
            this.observationDateTime = value;
        }
    }
}
