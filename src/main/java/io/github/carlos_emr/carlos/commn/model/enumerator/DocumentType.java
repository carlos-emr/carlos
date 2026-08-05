package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration of supported clinical document types.
 * Used for categorizing scanned documents, lab results, and generated PDFs in the EMR.
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