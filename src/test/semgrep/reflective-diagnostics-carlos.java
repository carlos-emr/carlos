// Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
class DiagnosticExamples {
    String unsafe(Object value) {
        // ruleid: carlos-no-reflective-object-methods
        return org.apache.commons.lang3.builder.ReflectionToStringBuilder.toString(value);
    }

    String unsafeStyle(Object value) {
        // ruleid: carlos-no-reflective-object-methods
        return ToStringBuilder.reflectionToString(value, ToStringStyle.MULTI_LINE_STYLE);
    }

    boolean unsafeEquality(Object left, Object right) {
        // ruleid: carlos-no-reflective-object-methods
        return EqualsBuilder.reflectionEquals(left, right);
    }

    int unsafeHash(Object value) {
        // ruleid: carlos-no-reflective-object-methods
        return HashCodeBuilder.reflectionHashCode(value);
    }

    void unsafeAppend(Object left, Object right) {
        // ruleid: carlos-no-reflective-object-methods
        new EqualsBuilder().reflectionAppend(left, right);
    }

    int unsafeCompare(Object left, Object right) {
        // ruleid: carlos-no-reflective-object-methods
        return CompareToBuilder.reflectionCompare(left, right);
    }

    Object unsafeDiff(Object left, Object right) {
        // ruleid: carlos-no-reflective-object-methods
        return ReflectionDiffBuilder.builder().setLeft(left).setRight(right).build();
    }

    String safe(Integer id) {
        // ok: carlos-no-reflective-object-methods
        return "Security[id=" + id + "]";
    }
}
