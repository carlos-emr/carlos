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
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record FeeScheduleAppliedChange(String code, BigDecimal value) {

    public Map<String, Object> toViewMap() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", code);
        view.put("value", BillingMoney.format(value));
        return view;
    }
}
