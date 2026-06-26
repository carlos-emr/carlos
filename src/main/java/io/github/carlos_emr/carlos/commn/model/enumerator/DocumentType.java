package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration DocumentType defining specific constants used across the domain model.
 */
public enum DocumentType {
    // Provides type-safe enum constants for DocumentType

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