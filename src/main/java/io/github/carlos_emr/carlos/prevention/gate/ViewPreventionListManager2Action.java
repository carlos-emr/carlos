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
 * Conditional-POST gate for {@code oscarPrevention/PreventionListManager.jsp}. The JSP
 * is self-posting: initial GET renders the admin list; form submits to itself
 * with {@code formAction=update} which triggers scriptlet mutations. Requires
 * POST when {@code formAction} is present so the mutation path cannot be
 * triggered via GET (CSRF hardening). Pattern mirrors ViewSettleBG2Action
 * from #1632.
 *
 * @since 2026-04-13
 */
public final class ViewPreventionListManager2Action extends AbstractPreventionGate2Action {

    @Override
    protected String privilegeType() {
        return "w";
    }

    @Override
    protected boolean requireConditionalPost() {
        return true;
    }
}
