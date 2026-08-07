package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="reportContent", propOrder={"textContent", "media"})
public class ReportContent {
    @XmlElement(name="TextContent")
    protected String textContent;
    @XmlElement(name="Media")
    protected byte[] media;

    public String getTextContent() {
        return this.textContent;
    }

    public void setTextContent(String value) {
        this.textContent = value;
    }

    public byte[] getMedia() {
        return this.media;
    }

    public void setMedia(byte[] value) {
        this.media = value;
    }
}
