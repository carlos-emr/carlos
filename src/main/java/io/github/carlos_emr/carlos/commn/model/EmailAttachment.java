package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.*;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;

@Entity
@Table(name = "emailAttachment")
public class EmailAttachment extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String fileName;

    private String filePath;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private int documentId;

    @Transient
    private long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logId")
    private EmailLog emailLog;

    public EmailAttachment() {
    }

    public EmailAttachment(String fileName, String filePath, DocumentType documentType, int documentId) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.documentType = documentType;
        this.documentId = documentId;
    }

    public EmailAttachment(String fileName, String filePath, DocumentType documentType, int documentId, long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.documentType = documentType;
        this.documentId = documentId;
        this.fileSize = fileSize;
    }

    public EmailAttachment(EmailLog emailLog, String fileName, String filePath, DocumentType documentType, Integer documentId) {
        this.emailLog = emailLog;
        this.fileName = fileName;
        this.filePath = filePath;
        this.documentType = documentType;
        this.documentId = documentId;
    }

    public Integer getId() {
        return id;
    }

    public EmailLog getEmailLog() {
        return emailLog;
    }

    public void setEmailLog(EmailLog emailLog) {
        this.emailLog = emailLog;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public int getDocumentId() {
        return documentId;
    }

    public void setDocumentId(int documentId) {
        this.documentId = documentId;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
