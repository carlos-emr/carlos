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

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable provider retirement boundaries in the existing global settings store. This state belongs to
 * CARLOS, not to an adapter. Callers hold the STUB selection mutex and config lock before using this DAO, in the
 * same transaction, so admission cannot cross a retirement's highest committed transaction id.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class SmsProviderRetirementDao {
    private static final String PREFIX = "sms.provider.retiredThrough.";
    private static final String MARKER = "sms.provider.retirement.v1";
    private static final List<String> KEYS;

    static {
        List<String> keys = new ArrayList<>();
        keys.add(MARKER);
        for (SmsProviderType type : SmsProviderType.values()) {
            keys.add(PREFIX + type.name());
        }
        KEYS = List.copyOf(keys);
    }

    @PersistenceContext
    private EntityManager entityManager;

    /** Absent legacy state means no tracked retirement; partial, duplicated or malformed state blocks work. */
    public long retiredThrough(SmsProviderType provider) {
        return cutoffs(rows()).get(provider);
    }

    /** Initialize the complete registry even when no provider changes on this save. */
    public void recordSelection(SmsProviderType previous, SmsProviderType selected, boolean firstSave) {
        Map<String, SystemPreferences> rows = rows();
        Map<SmsProviderType, Long> cutoffs = cutoffs(rows);
        boolean initialize = rows.isEmpty();
        for (SmsProviderType type : SmsProviderType.values()) {
            long cutoff = cutoffs.get(type);
            if (((firstSave || initialize) && type != selected) || (!firstSave && type == previous && type != selected)) {
                // A current locking read, not MAX from a repeatable-read snapshot established before
                // the selection mutex was acquired. Admission commits before we can acquire it.
                List<?> highestRows = entityManager.createNativeQuery("SELECT id FROM sms_transaction "
                                + "WHERE direction = ?1 AND provider_type = ?2 ORDER BY id DESC LIMIT 1 FOR UPDATE")
                        .setParameter(1, SmsDirection.OUTBOUND.name())
                        .setParameter(2, type.name()).getResultList();
                Long highest = highestRows.isEmpty() ? null : ((Number) highestRows.get(0)).longValue();
                cutoff = Math.max(cutoff, highest == null ? 0L : highest);
            }
            put(rows, PREFIX + type.name(), Long.toString(cutoff));
        }
        put(rows, MARKER, "1");
    }

    private Map<String, SystemPreferences> rows() {
        List<SystemPreferences> stored = entityManager.createQuery(
                        "SELECT p FROM SystemPreferences p WHERE p.name IN :keys", SystemPreferences.class)
                .setParameter("keys", KEYS).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        Map<String, SystemPreferences> rows = new HashMap<>();
        for (SystemPreferences row : stored) {
            entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            if (rows.putIfAbsent(row.getName(), row) != null) {
                throw invalidState();
            }
        }
        return rows;
    }

    private static Map<SmsProviderType, Long> cutoffs(Map<String, SystemPreferences> rows) {
        Map<SmsProviderType, Long> result = new EnumMap<>(SmsProviderType.class);
        if (!rows.isEmpty() && (rows.size() != KEYS.size() || !"1".equals(rows.get(MARKER) == null
                ? null : rows.get(MARKER).getValue()))) {
            throw invalidState();
        }
        for (SmsProviderType type : SmsProviderType.values()) {
            SystemPreferences row = rows.get(PREFIX + type.name());
            String value = row == null ? "0" : row.getValue();
            if (!value.matches("[0-9]{1,19}")) {
                throw invalidState();
            }
            try {
                result.put(type, Long.parseLong(value));
            } catch (NumberFormatException e) {
                throw invalidState();
            }
        }
        return result;
    }

    private void put(Map<String, SystemPreferences> rows, String name, String value) {
        SystemPreferences row = rows.get(name);
        if (row == null) {
            row = new SystemPreferences(name, value);
            entityManager.persist(row);
        } else if (!value.equals(row.getValue())) {
            row.setValue(value);
            row.setUpdateDate(new java.util.Date());
        }
    }

    private static IllegalStateException invalidState() {
        return new IllegalStateException("SMS provider retirement state is incomplete or invalid; sending is blocked.");
    }
}
