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
package io.github.carlos_emr.carlos.login.gate;

/**
 * Gate for migrated post-login transitional pages that require an authenticated
 * session before rendering.
 *
 * @since 2026-04-15
 */
public final class ViewAuthenticatedPage2Action extends BaseLoginPageView2Action {

    @Override
    protected String requiredSessionAttribute() {
        return "user";
    }
}
