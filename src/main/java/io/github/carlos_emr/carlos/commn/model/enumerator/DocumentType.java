package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration defining the allowable states and type categories for DocumentType within the system.
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


    // Gettype is exposed here to satisfy the external component interface contract without exposing internal state.
    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}