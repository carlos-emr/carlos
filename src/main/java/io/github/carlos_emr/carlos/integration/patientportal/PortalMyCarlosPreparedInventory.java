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
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Operation-local exact preparation cache. Never shared across requests or used before locked CAS. */
final class PortalMyCarlosPreparedInventory {
    private String binding;
    private List<PatientPortalClinicKeysPublishRequest.Key> prepared;

    List<PatientPortalClinicKeysPublishRequest.Key> get(String clinic, PortalMyCarlosKeyInventory previous,
            Supplier<List<PatientPortalClinicKeysPublishRequest.Key>> preparation) {
        String current = clinic + "\n" + (previous == null ? "first" : previous.toStoredJson());
        if (prepared != null) {
            if (!Objects.equals(binding, current)) throw new IllegalStateException("prepared inventory context changed");
            return prepared;
        }
        try {
            List<PatientPortalClinicKeysPublishRequest.Key> result = List.copyOf(preparation.get());
            binding = current;
            prepared = result;
            return prepared;
        } catch (RuntimeException | Error failure) {
            PortalMyCarlosInventoryTransactions.preparationFailed();
            throw failure;
        }
    }
}
