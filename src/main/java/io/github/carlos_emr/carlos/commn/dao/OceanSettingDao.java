/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.OceanSetting;

/**
 * Data access for the single-row {@link OceanSetting} opaque settings blob
 * that backs the Ocean Toolbar's {@code getSettings}/{@code saveSettings}
 * REST contract.
 *
 * @since 2026-09-18
 */
public interface OceanSettingDao extends AbstractDao<OceanSetting> {

    /**
     * @return the single stored {@link OceanSetting} row, or {@code null} if the
     * Ocean Toolbar has never saved its settings on this instance.
     */
    OceanSetting getSettings();

    /**
     * Upserts the single {@link OceanSetting} row with the given opaque blob.
     *
     * @param settings the raw settings blob as sent by the Ocean Toolbar client script
     * @return the persisted {@link OceanSetting}
     */
    OceanSetting saveSettings(String settings);
}
