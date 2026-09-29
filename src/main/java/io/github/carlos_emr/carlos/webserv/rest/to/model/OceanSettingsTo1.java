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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import jakarta.xml.bind.annotation.XmlRootElement;

import java.io.Serializable;

/**
 * Wire shape for the Ocean Toolbar's {@code getSettings}/{@code saveSettings}
 * REST contract: {@code {"settings": "<opaque string, or null if unset>"}}.
 * Confirmed against Ocean's own live client script (ConfigUtil.js), which reads
 * and writes exactly this single field and owns the format of its value entirely.
 *
 * @since 2026-09-18
 */
@XmlRootElement(name = "oceanSettings")
public class OceanSettingsTo1 implements Serializable {

    private static final long serialVersionUID = 1L;

    private String settings;

    public OceanSettingsTo1() {
    }

    public OceanSettingsTo1(String settings) {
        this.settings = settings;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }
}
