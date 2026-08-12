package io.github.carlos_emr.carlos.commn.model.enumerator;
/**
 * Domain model representing DocumentType data structures within the CARLOS EMR system, including state and relationships.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
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
        // Internal logic boundary for DocumentType state management
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}