/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.regex.Pattern;

/** Validates the existing service-code popup assignment targets without evaluating input. */
final class BillingCodeAttachmentTarget {
    private static final Pattern LEGACY_PATH = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");
    // The bill form uses index 0; correction/display use index 1. Only service-code values are writable.
    private static final Pattern SERVICE_FIELD = Pattern.compile(
            "document\\.forms\\[[01]\\]\\.elements\\['service[Cc]ode[0-9]+'\\]\\.value");

    private BillingCodeAttachmentTarget() {
    }

    /** Returns a supported assignment path, or an empty string for absent/unsupported input. */
    static String validate(String raw) {
        if (raw == null) {
            return "";
        }
        return LEGACY_PATH.matcher(raw).matches() || SERVICE_FIELD.matcher(raw).matches() ? raw : "";
    }
}
