/** Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

/** A known unrepresentable clinical mapping, distinct from storage or integrity failure. */
public final class CdsExportMappingReviewException extends java.io.IOException {
    private static final long serialVersionUID = 1L;

    public CdsExportMappingReviewException(String code) {
        super(safe(code));
    }

    private static String safe(String code) {
        if (code == null || !code.matches("CDS_EXPORT_[A-Z0-9_]{1,85}"))
            throw new IllegalArgumentException("CDS_EXPORT_REVIEW_CODE_INVALID");
        return code;
    }
}
