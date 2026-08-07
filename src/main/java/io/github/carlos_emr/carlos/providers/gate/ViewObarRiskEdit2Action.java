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
package io.github.carlos_emr.carlos.providers.gate;

/**
 * View gate for {@code provider/obarriskedit_99_12.jsp}. Enforces
 * {@code _appointment} {@code w} privilege before forwarding to the JSP. POST-only (GET returns 405).
 *
 * @since 2026-04-13
 */
public final class ViewObarRiskEdit2Action extends BaseProviderViewGate2Action {

    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "w";
    }

    @Override
    protected boolean requirePost() {
        return true;
    }
}
