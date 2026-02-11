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
package io.github.carlos_emr.carlos.fax.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory for resolving provider clients by {@link FaxConfig.ProviderType}.
 *
 * <p>All provider implementations are auto-discovered from Spring context and indexed once,
 * avoiding repetitive conditional branching in orchestration code.</p>
 *
 * @since 2026-02-11
 */
@Component
public class FaxProviderClientFactory {

    private final Map<FaxConfig.ProviderType, FaxProviderClient> providersByType;

    /**
     * Builds provider map from injected clients.
     *
     * @param providerClients provider beans discovered from Spring context
     * @throws IllegalStateException when duplicate beans target the same provider type
     */
    @Autowired
    public FaxProviderClientFactory(List<FaxProviderClient> providerClients) {
        providersByType = new EnumMap<>(FaxConfig.ProviderType.class);
        for (FaxProviderClient providerClient : providerClients) {
            FaxConfig.ProviderType providerType = providerClient.getProviderType();
            // Fail-fast on duplicate provider beans to avoid ambiguous runtime routing.
            if (providersByType.containsKey(providerType)) {
                throw new IllegalStateException("Duplicate FaxProviderClient beans configured for provider type: " + providerType);
            }
            providersByType.put(providerType, providerClient);
        }
    }

    /**
     * Resolves provider client for a fax configuration.
     *
     * @param faxConfig fax account configuration used to choose provider implementation
     * @return provider client matching {@link FaxConfig#getProviderType()}
     * @throws FaxProviderException when input is null or no provider client is registered for the requested type
     */
    public FaxProviderClient getClient(FaxConfig faxConfig) throws FaxProviderException {
        if (faxConfig == null) {
            throw new FaxProviderException("Fax configuration is required to resolve provider client");
        }

        FaxConfig.ProviderType providerType = faxConfig.getProviderType();
        FaxProviderClient providerClient = providersByType.get(providerType);
        if (providerClient == null) {
            throw new FaxProviderException("No fax provider client configured for provider type: " + providerType);
        }
        return providerClient;
    }
}
