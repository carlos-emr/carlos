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
 * Mutation gate for {@code provider/preference_action.jsp}. The JSP scriptlet
 * calls {@code ProviderPreferencesUIBean.updateOrCreateProviderPreferences},
 * so the gate enforces {@code _pref} {@code w} privilege and POST-only (GET
 * returns 405). Named {@code View*} for naming-pattern consistency with
 * sibling gates; see the action mapping for the real routing.
 *
 * @since 2026-04-13
 */
public final class ViewPreferenceAction2Action extends BaseProviderViewGate2Action {

    @Override
    protected String getSecurityObject() {
        return "_pref";
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
