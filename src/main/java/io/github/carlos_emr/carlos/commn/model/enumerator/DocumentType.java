package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Enumeration of various document types supported by the system.
 * Used for categorizing and applying specific processing rules to uploaded or generated documents.
 *
 * @since 2026-07-09
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
        // Return the string value used in legacy database tables
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}