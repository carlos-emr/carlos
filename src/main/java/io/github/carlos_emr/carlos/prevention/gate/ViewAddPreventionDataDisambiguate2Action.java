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
package io.github.carlos_emr.carlos.prevention.gate;

/**
 * View gate for {@code oscarPrevention/AddPreventionDataDisambiguate.jsp}. Enforces
 * {@code _prevention r} before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location. Part of the oscarPrevention security-
 * hardening migration (2Action gate pattern from #1109, #1629, #1632, etc.).
 *
 * @since 2026-04-13
 */
public final class ViewAddPreventionDataDisambiguate2Action extends AbstractPreventionGate2Action {

    @Override
    protected String privilegeType() {
        return "r";
    }
}
