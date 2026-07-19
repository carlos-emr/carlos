package io.github.carlos_emr.carlos.webserv.rest.to.model;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ConsultationAttachment implements Serializable {
    private Integer id;
    private String attachmentType;
    private String fileName;
    private byte[] data;

    public ConsultationAttachment(Integer id, String attachmentType, String fileName, byte[] data) {
        this.id = id;
        this.attachmentType = attachmentType;
        this.fileName = fileName;
        this.data = data;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(String attachmentType) {
        this.attachmentType = attachmentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}