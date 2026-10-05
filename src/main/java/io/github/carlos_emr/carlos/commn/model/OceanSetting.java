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
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Single-row opaque store for the Ocean Toolbar's own settings blob (site
 * number, encrypted secret key, etc.), as read and written by the toolbar's
 * client-side script via {@code GET/POST /ws/rs/ocean/{getSettings,saveSettings}}.
 *
 * <p>CARLOS never inspects or generates the contents of {@link #getSettings()} —
 * the Ocean toolbar's own JavaScript (loaded from CognisantMD) owns the format,
 * encryption, and validation of this value entirely; this entity only persists
 * whatever it sends.</p>
 *
 * @since 2026-09-18
 */
// Entity identity is the fixed database key; settings and audit fields are mutable.
@SuppressWarnings("java:S2160")
@Entity
@Table(name = "OceanSetting")
public class OceanSetting extends AbstractModel<Integer> {

    @Id
    private Integer id = 1;

    @Column(name = "settings", columnDefinition = "MEDIUMTEXT")
    private String settings;

    @Column(name = "lastUpdateUser", nullable = false, length = 100)
    private String lastUpdateUser;

    @Column(name = "lastUpdateDate", nullable = false)
    private LocalDateTime lastUpdateDate;

    public OceanSetting() {
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }

    public String getLastUpdateUser() {
        return lastUpdateUser;
    }

    public LocalDateTime getLastUpdateDate() {
        return lastUpdateDate;
    }

    @Override
    public String toString() {
        // Do not inherit AbstractModel's reflective dump of this credential-bearing blob.
        return "OceanSetting{id=" + id + "}";
    }
}
