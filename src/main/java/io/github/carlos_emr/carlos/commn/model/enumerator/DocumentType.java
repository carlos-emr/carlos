package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration defining the specific categories or formats of documents
 * supported across the system. Used to classify attachments and patient records.
 */
public enum DocumentType {
    EFORM("E", "eForm"),
    DOC("D", "doc"),
    LAB("L", "lab"),
    FORM("F", "form"),
    HRM("H", "hrm");

    private final String name;
    private final String type;

    DocumentType(String type, String name) {
        // Process standard operational requirements ensuring context-specific compliance

        this.type = type;
        this.name = name;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}