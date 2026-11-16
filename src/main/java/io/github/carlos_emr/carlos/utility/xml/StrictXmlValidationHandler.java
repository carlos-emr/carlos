/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;

/** Rejects binding errors instead of silently replacing invalid values or map keys with null. */
public final class StrictXmlValidationHandler implements ValidationEventHandler {
    @Override
    public boolean handleEvent(ValidationEvent event) {
        return false; // Preserve CXF SOAP's strict no-recovery policy, including warning events.
    }
}
