/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

/**
 * View model for {@code admin/gstControl.jsp}. Carries the persisted
 * GST percent so the JSP can render the input's initial value via EL.
 *
 * @since 2026-04-27
 */
public record GstControlViewModel(String gstPercent) {

    public String getGstPercent() {
        return gstPercent;
    }
}
