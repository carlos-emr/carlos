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
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class OceanSettingDaoImpl extends AbstractDaoImpl<OceanSetting> implements OceanSettingDao {

    public OceanSettingDaoImpl() {
        super(OceanSetting.class);
    }

    @Override
    public OceanSetting getSettings() {
        return entityManager.find(OceanSetting.class, 1);
    }

    @Override
    public OceanSetting saveSettings(String settings, String providerNo) {
        validateProvider(providerNo);
        // The PK conflict serializes concurrent first saves; no read-then-insert race.
        // This DAO transaction retains the row lock until the refreshed result is read.
        entityManager.createNativeQuery("INSERT INTO OceanSetting "
                + "(id, settings, lastUpdateUser, lastUpdateDate) VALUES (1, ?1, ?2, CURRENT_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE settings=VALUES(settings), "
                + "lastUpdateUser=VALUES(lastUpdateUser), lastUpdateDate=VALUES(lastUpdateDate)")
                .setParameter(1, settings)
                .setParameter(2, providerNo)
                .executeUpdate();
        OceanSetting saved = getSettings();
        // Native updates bypass Hibernate's first-level cache, including an earlier GET.
        entityManager.refresh(saved);
        return saved;
    }
    @Override
    public void saveDisplayPreference(boolean enabled, String providerNo) {
        validateProvider(providerNo);
        // This upsert obtains the same singleton-row lock on every app instance.
        // Existing settings/audit data are untouched when the row already exists.
        entityManager.createNativeQuery("INSERT INTO OceanSetting "
                + "(id, settings, lastUpdateUser, lastUpdateDate) VALUES (1, NULL, ?1, CURRENT_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE id=VALUES(id)")
                .setParameter(1, providerNo).executeUpdate();
        // Update every matching row to also make pre-existing duplicate values consistent.
        int count = entityManager.createNativeQuery("UPDATE SystemPreferences SET `value`=?1, "
                + "updateDate=CURRENT_TIMESTAMP WHERE name='echart_show_ocean'")
                .setParameter(1, Boolean.toString(enabled)).executeUpdate();
        if (count == 0) {
            // Some JDBC configurations report changed rows, not matched rows. Count
            // explicitly before inserting so a repeated identical save stays a singleton.
            Number rows = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM SystemPreferences "
                    + "WHERE name='echart_show_ocean'").getSingleResult();
            if (rows.longValue() == 0) {
                entityManager.createNativeQuery("INSERT INTO SystemPreferences (name, `value`, updateDate) "
                        + "VALUES ('echart_show_ocean', ?1, CURRENT_TIMESTAMP)")
                        .setParameter(1, Boolean.toString(enabled)).executeUpdate();
            }
        }
    }

    private static void validateProvider(String providerNo) {
        Objects.requireNonNull(providerNo, "An authenticated provider is required");
        if (providerNo.isBlank() || providerNo.length() > 100) {
            throw new IllegalArgumentException("Invalid audit provider");
        }
    }

}
