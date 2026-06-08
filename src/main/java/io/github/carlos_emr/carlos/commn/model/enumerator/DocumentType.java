package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration defining the specific constants for DocumentType within the CARLOS system.
 * These values represent strictly allowed options for DocumentType in the domain model.
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
