package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Defines the standard set of document categorizations used across the EMR (e.g., PDF, Image, Lab).
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