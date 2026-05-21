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
package io.github.carlos_emr.carlos.test.support;

import jakarta.persistence.EntityManager;

/**
 * Shared seed-data helpers for integration tests that need core provider and
 * demographic rows before exercising higher-level CARLOS EMR services.
 *
 * @since 2026-05-20
 */
public final class IntegrationTestSeedService {

    private IntegrationTestSeedService() {
    }

    /**
     * Ensures an active test provider row exists without flushing the caller's
     * persistence context.
     *
     * @param entityManager EntityManager used by the integration test
     * @param providerNo String provider number to upsert
     */
    public static void ensureProviderExists(EntityManager entityManager, String providerNo) {
        entityManager.createNativeQuery("""
                MERGE INTO provider (provider_no, first_name, last_name, provider_type, sex, specialty, status)
                KEY(provider_no)
                VALUES (:providerNo, 'Test', 'Provider', 'doctor', 'M', 'GP', '1')
                """)
                .setParameter("providerNo", providerNo)
                .executeUpdate();
    }

    /**
     * Ensures an active test demographic row exists without flushing the
     * caller's persistence context.
     *
     * @param entityManager EntityManager used by the integration test
     * @param demographicNo Integer demographic number to upsert
     */
    public static void ensureDemographicExists(EntityManager entityManager, Integer demographicNo) {
        entityManager.createNativeQuery("""
                MERGE INTO demographic (demographic_no, first_name, last_name, sex, patient_status)
                KEY(demographic_no)
                VALUES (:demographicNo, 'Test', 'Patient', 'M', 'AC')
                """)
                .setParameter("demographicNo", demographicNo)
                .executeUpdate();
    }
}
