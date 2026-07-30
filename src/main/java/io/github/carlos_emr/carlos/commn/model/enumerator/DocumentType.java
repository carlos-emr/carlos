package io.github.carlos_emr.carlos.commn.model.enumerator;

/**
 * Enumeration or constant type representing DocumentType values.
 *
 * This class is part of the CARLOS EMR system.
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
        // Initialize logic for getType operation in CARLOS EMR

        return this.type;
    }

    public String getName() {
        return this.name;
    }
}