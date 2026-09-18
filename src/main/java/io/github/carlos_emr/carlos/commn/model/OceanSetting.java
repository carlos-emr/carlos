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
import java.util.Date;

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
@Entity
@Table(name = "OceanSetting")
public class OceanSetting extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "settings", columnDefinition = "TEXT")
    private String settings;

    @Column(name = "updateDate")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateDate;

    public OceanSetting() {
    }

    public OceanSetting(String settings) {
        this.settings = settings;
        this.updateDate = new Date();
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

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }
}
