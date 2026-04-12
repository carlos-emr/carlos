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
package io.github.carlos_emr.carlos.config;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Spring Cache abstraction backed by Caffeine for reference data caching.
 *
 * <p>Each cache has individually tuned TTL and max-size settings appropriate for its
 * data volatility. Provider names change only via admin screens (15-min TTL), while
 * active provider lists are slightly more dynamic (5-min TTL). Appointment statuses
 * and lookup lists are admin-level configuration (30-min TTL). Measurement types
 * are essentially static reference data (60-min TTL).</p>
 *
 * <p>Discovered automatically by the broad component scan in {@code spring_managers.xml}
 * ({@code io.github.carlos_emr.carlos} base package).</p>
 *
 * @since 2026-04-12
 * @see io.github.carlos_emr.carlos.PMmodule.dao.ProviderDaoImpl
 * @see io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDaoImpl
 * @see io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDaoImpl
 * @see io.github.carlos_emr.carlos.commn.dao.LookupListDaoImpl
 */
@Configuration
@EnableCaching(proxyTargetClass = true)
public class CacheConfig {

    /**
     * Creates a {@link SimpleCacheManager} with individually configured Caffeine caches.
     *
     * <p>Using {@code SimpleCacheManager} rather than {@code CaffeineCacheManager} allows
     * per-cache TTL and max-size tuning. Each cache is backed by a Caffeine instance with
     * {@code expireAfterWrite} for time-based eviction and {@code maximumSize} for
     * memory-bounded eviction.</p>
     *
     * @return CacheManager the configured cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache("providerNames", 500, 15, TimeUnit.MINUTES),
                buildCache("activeProviders", 10, 5, TimeUnit.MINUTES),
                buildCache("activeProviderSummaries", 5, 5, TimeUnit.MINUTES),
                buildCache("appointmentStatuses", 5, 30, TimeUnit.MINUTES),
                buildCache("measurementTypes", 10, 60, TimeUnit.MINUTES),
                buildCache("lookupLists", 50, 30, TimeUnit.MINUTES)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, long maxSize, long duration, TimeUnit unit) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(duration, unit)
                        .build());
    }
}
