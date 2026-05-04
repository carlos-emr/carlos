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
 * View gate for {@code prevention/AddPreventionData.jsp}. This popup is a
 * write-entry workflow, so the gate preserves the historical
 * {@code _prevention w} requirement while allowing GET/HEAD to render the form
 * under a dedicated view route. The form itself still posts to
 * {@code /prevention/AddPrevention} for the actual save/update/delete.
 *
 * @see io.github.carlos_emr.carlos.prevention.pageUtil.AddPrevention2Action
 * @since 2026-04-21
 */
public final class ViewAddPreventionData2Action extends AbstractPreventionGate2Action {

    @Override
    protected String privilegeType() {
        return "w";
    }
}
