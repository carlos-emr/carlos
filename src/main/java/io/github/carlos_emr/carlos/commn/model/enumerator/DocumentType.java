package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration of supported document types within the system.
 * <p>
 * Defines the categories of documents (e.g., PDF, image, text) processed and
 * managed by the CARLOS EMR document management module.
 * </p>
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
    // Ensure document type mappings match supported content types in the file upload handler.
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}