package io.github.carlos_emr.carlos.commn.model.enumerator;

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

    /**
     * Resolves a type from its single-letter storage code ({@code D}, {@code L}, {@code E},
     * {@code F}, {@code H}), as persisted in the {@code consultdocs}, {@code EFormDocs} and
     * {@code ticklerdocs} tables.
     *
     * @param type String the storage code
     * @return DocumentType the matching type, or {@code null} when the code is unknown
     */
    public static DocumentType fromType(String type) {
        for (DocumentType documentType : values()) {
            if (documentType.type.equals(type)) {
                return documentType;
            }
        }
        return null;
    }
}