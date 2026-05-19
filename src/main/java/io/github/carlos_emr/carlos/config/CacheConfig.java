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

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-level cache configuration using Spring Cache abstraction backed by Caffeine.
 *
 * <p>Provides individually tuned caches for high-traffic, read-heavy reference-data DAOs
 * (provider names, active-provider lists, appointment statuses, measurement types, and
 * lookup lists). These data sets change only through admin screens, making them excellent
 * candidates for short-to-medium TTL caching.</p>
 *
 * <p>The {@link CacheManager} returned by {@link #cacheManager()} is wrapped in a
 * {@link TransactionAwareCacheManagerProxy} so that puts and evictions are deferred until
 * the enclosing transaction commits. Without this wrapper a {@code @CacheEvict} inside a
 * transaction that later rolls back would still have evicted, and a {@code @Cacheable}
 * result captured mid-transaction would leak regardless of rollback.</p>
 *
 * <p>Each cache is an individually sized {@link CaffeineCache} with its own
 * {@code maximumSize} and {@code expireAfterWrite} — there is no shared spec.</p>
 *
 * @since 2026-04-20
 */
@Configuration
@EnableCaching(proxyTargetClass = true)  // CGLIB proxies: consistent with proxy-target-class=true on <tx:annotation-driven> and <aop:aspectj-autoproxy> in applicationContext.xml
public class CacheConfig {

    // Cache-name constants referenced from every @Cacheable / @CacheEvict annotation and
    // from programmatic CacheManager.getCache(...) calls. Keeping these in one place means
    // a typo fails at compile time rather than at first cache access.
    public static final String PROVIDER_NAMES = "providerNames";
    public static final String ACTIVE_PROVIDERS = "activeProviders";
    public static final String ACTIVE_PROVIDER_SUMMARIES = "activeProviderSummaries";
    public static final String APPOINTMENT_STATUSES = "appointmentStatuses";
    public static final String MEASUREMENT_TYPES = "measurementTypes";
    public static final String LOOKUP_LISTS = "lookupLists";
    public static final String APPOINTMENT_TYPES = "appointmentTypes";
    public static final String SCHEDULE_TEMPLATE_CODES = "scheduleTemplateCodes";
    public static final String FACILITIES = "facilities";
    public static final String ENCOUNTER_FORMS = "encounterForms";

    /**
     * Creates and configures the application {@link CacheManager}.
     *
     * <p>The delegate {@link SimpleCacheManager} is initialized with individually tuned
     * Caffeine caches, then wrapped in a {@link TransactionAwareCacheManagerProxy} for
     * transactional safety.</p>
     *
     * <h3>Cache catalogue</h3>
     * <table>
     *   <tr><th>Name</th><th>Max Size</th><th>TTL</th><th>Notes</th></tr>
     *   <tr><td>{@code providerNames}</td><td>500</td><td>15 min</td>
     *       <td>Keyed by {@code 'name:' + providerNo} and {@code 'nameLastFirst:' + providerNo}</td></tr>
     *   <tr><td>{@code activeProviders}</td><td>10</td><td>5 min</td>
     *       <td>Keyed by filter variant; small cardinality</td></tr>
     *   <tr><td>{@code activeProviderSummaries}</td><td>1</td><td>5 min</td>
     *       <td>Single summary DTO list; max=1 is deliberate — the caching method takes no
     *       arguments so Spring uses {@code SimpleKey.EMPTY} as the sole key. Do not raise
     *       the size without also giving the method a keyed signature.</td></tr>
     *   <tr><td>{@code appointmentStatuses}</td><td>5</td><td>30 min</td>
     *       <td>{@code 'all'}, {@code 'active'}, {@code 'status:X'}</td></tr>
     *   <tr><td>{@code measurementTypes}</td><td>10</td><td>60 min</td>
     *       <td>{@code 'allByType'}, {@code 'allByName'}, {@code 'allById'}</td></tr>
     *   <tr><td>{@code lookupLists}</td><td>50</td><td>30 min</td>
     *       <td>{@code 'allActive'}, {@code 'name:...'}</td></tr>
     *   <tr><td>{@code appointmentTypes}</td><td>20</td><td>30 min</td>
     *       <td>{@code 'all'}, {@code 'name:...'}</td></tr>
     *   <tr><td>{@code scheduleTemplateCodes}</td><td>50</td><td>30 min</td>
     *       <td>{@code 'all'}, {@code 'templateCodes'}, {@code 'codeChar:...'}</td></tr>
     *   <tr><td>{@code facilities}</td><td>3</td><td>15 min</td>
     *       <td>{@code 'active:true|false|null'}</td></tr>
     *   <tr><td>{@code encounterForms}</td><td>200</td><td>5 min</td>
     *       <td>Rarely changing form-table allowlist lookups keyed by form table</td></tr>
     * </table>
     *
     * @return CacheManager transaction-aware cache manager wrapping the Caffeine caches
     */
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager delegate = new SimpleCacheManager();
        delegate.setCaches(List.of(
                buildCache(PROVIDER_NAMES, 500, Duration.ofMinutes(15)),
                buildCache(ACTIVE_PROVIDERS, 10, Duration.ofMinutes(5)),
                buildCache(ACTIVE_PROVIDER_SUMMARIES, 1, Duration.ofMinutes(5)),
                buildCache(APPOINTMENT_STATUSES, 5, Duration.ofMinutes(30)),
                buildCache(MEASUREMENT_TYPES, 10, Duration.ofMinutes(60)),
                buildCache(LOOKUP_LISTS, 50, Duration.ofMinutes(30)),
                buildCache(APPOINTMENT_TYPES, 20, Duration.ofMinutes(30)),
                buildCache(SCHEDULE_TEMPLATE_CODES, 50, Duration.ofMinutes(30)),
                buildCache(FACILITIES, 3, Duration.ofMinutes(15)),
                buildCache(ENCOUNTER_FORMS, 200, Duration.ofMinutes(5))
        ));
        delegate.afterPropertiesSet();
        return new TransactionAwareCacheManagerProxy(delegate);
    }

    private static CaffeineCache buildCache(String name, long maximumSize, Duration ttl) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maximumSize)
                        .expireAfterWrite(ttl)
                        .build());
    }
}
