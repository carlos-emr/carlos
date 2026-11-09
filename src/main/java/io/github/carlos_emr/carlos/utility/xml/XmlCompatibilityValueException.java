/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

/** A rejected compatibility value must not be silently replaced with null by a lenient JAXB reader. */
public final class XmlCompatibilityValueException extends IllegalArgumentException {
    XmlCompatibilityValueException(String message) {
        super(message);
    }
}
