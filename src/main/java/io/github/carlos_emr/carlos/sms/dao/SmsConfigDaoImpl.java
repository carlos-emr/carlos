/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class SmsConfigDaoImpl extends AbstractDaoImpl<SmsConfig> implements SmsConfigDao {
    public SmsConfigDaoImpl() {
        super(SmsConfig.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SmsConfig> findCurrent() {
        return Optional.ofNullable(entityManager.find(SmsConfig.class, SmsConfig.SINGLETON_ID));
    }

    @Override
    @Transactional
    public Optional<SmsConfig> findCurrentForUpdate() {
        // The caller first holds the materialized STUB limiter row as a selection mutex. A locking
        // read of the absent config row alone cannot serialize first-save and admission gap locks.
        // Lock a scalar first: locking an entity query upgrades a cached entity's lock and
        // compares its stale version before refresh can load the current committed state.
        var ids = entityManager.createNativeQuery("SELECT id FROM sms_config WHERE id = ?1 FOR UPDATE")
                .setParameter(1, SmsConfig.SINGLETON_ID)
                .getResultList();
        if (ids.isEmpty()) return Optional.empty();
        SmsConfig current = entityManager.find(SmsConfig.class, SmsConfig.SINGLETON_ID);
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(current);
    }
}
