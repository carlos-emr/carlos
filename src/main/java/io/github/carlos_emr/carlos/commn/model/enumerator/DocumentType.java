package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration of supported clinical document types.
 *
 * <p>Defines the acceptable categories of files that can be uploaded,
 * attached, or generated within the EMR.</p>
 */

public enum DocumentType {
    // Restrict executable file types to prevent malicious uploads
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