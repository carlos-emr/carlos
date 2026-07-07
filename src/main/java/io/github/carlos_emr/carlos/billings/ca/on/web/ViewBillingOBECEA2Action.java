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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.providers.gate.BaseProviderViewGate2Action;

/**
 * View gate for {@code billing/CA/ON/billingOBECEA.jsp}. Enforces
 * {@code _admin.billing} {@code w} privilege before forwarding to the JSP.
 *
 * @since 2026-05-05
 */
public class ViewBillingOBECEA2Action extends BaseProviderViewGate2Action {
    @Override
    protected String getSecurityObject() {
        return "_admin.billing";
    }

    @Override
    protected String getAccessRight() {
        return "w";
    }
}
