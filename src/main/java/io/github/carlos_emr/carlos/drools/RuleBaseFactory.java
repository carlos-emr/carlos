/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.drools;

import org.apache.commons.lang3.time.DateUtils;
import org.kie.api.KieBase;
import io.github.carlos_emr.carlos.utility.QueueCache;

/**
 * Factory class for managing Drools KieBase instances with caching.
 *
 * <p>This factory provides centralized management of compiled Drools knowledge bases
 * used throughout the CARLOS EMR system for clinical decision support, prevention
 * schedules, measurement flowsheets, and workflow automation. It implements a caching
 * mechanism using {@link QueueCache} to avoid expensive repeated compilation of
 * DRL rules.</p>
 *
 * <h3>Caching Strategy</h3>
 * <p>The factory uses a {@link QueueCache} with the following configuration:</p>
 * <ul>
 *   <li><strong>Queue buckets:</strong> 4 (distributes entries across buckets for performance)</li>
 *   <li><strong>Max entries:</strong> 2048 (maximum number of knowledge bases to cache)</li>
 *   <li><strong>TTL:</strong> 24 hours (entries expire after {@link DateUtils#MILLIS_PER_DAY})</li>
 *   <li><strong>Expiry handler:</strong> null (no custom cleanup on expiration)</li>
 * </ul>
 *
 * <h3>Cache Key Conventions</h3>
 * <p>Callers use different key strategies depending on the subsystem:</p>
 * <ul>
 *   <li>{@code RuleBaseCreator}: Key is {@code "RuleBaseCreator:" + fullDrlString} (guarantees
 *       uniqueness but verbose; the entire DRL text serves as its own cache key)</li>
 *   <li>{@code DSGuidelineDrools}: Key is the guideline's {@code ruleBaseFactoryKey}
 *       (a compact identifier derived from the guideline's title and UUID)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>All methods are {@code synchronized} to ensure thread-safe access to the shared
 * cache instance. Multiple web requests may simultaneously evaluate clinical rules
 * for different patients.</p>
 *
 * <p>Migrated from legacy {@code org.drools.RuleBase} to {@link org.kie.api.KieBase}
 * as part of the Drools 2.0 to 7.74.1.Final upgrade.</p>
 *
 * @since 2001-01-01
 * @see DroolsHelper
 * @see org.kie.api.KieBase
 * @see io.github.carlos_emr.carlos.utility.QueueCache
 */
public final class RuleBaseFactory {

    /**
     * Cache for storing compiled KieBase instances, keyed by a source identifier string.
     *
     * <p>Configuration: 4 queue buckets, 2048 max entries, 24-hour TTL, no expiry callback.</p>
     */
    private static QueueCache<String, KieBase> ruleBaseInstances = new QueueCache<String, KieBase>(4, 2048, DateUtils.MILLIS_PER_DAY, null);

    /**
     * Retrieves a cached KieBase instance by its source key.
     *
     * <p>Provides thread-safe access to compiled rule bases. If the requested rule base
     * is not in the cache or has expired (after 24 hours), {@code null} is returned.
     * Callers should check for null and compile/load the rule base if needed, then
     * call {@link #putRuleBase(String, KieBase)} to cache the result.</p>
     *
     * @param sourceKey String unique identifier for the rule base
     *                  (e.g., "prevention", "RuleBaseCreator:...", guideline key)
     * @return KieBase the cached rule base instance, or null if not found or expired
     */
    public static synchronized KieBase getRuleBase(String sourceKey) {
        return (ruleBaseInstances.get(sourceKey));
    }

    /**
     * Stores a KieBase instance in the cache.
     *
     * <p>Adds or updates a compiled rule base in the cache with the specified key.
     * The rule base will be automatically evicted after 24 hours to ensure
     * updates to rules are reflected without requiring system restarts.</p>
     *
     * @param sourceKey String unique identifier for the rule base
     * @param kieBase KieBase compiled rule base instance to cache
     */
    public static synchronized void putRuleBase(String sourceKey, KieBase kieBase) {
        ruleBaseInstances.put(sourceKey, kieBase);
    }

    /**
     * Removes a specific KieBase from the cache.
     *
     * <p>Called when a rule base needs to be invalidated, for example when
     * a {@code DSGuidelineDrools} entity is updated via its {@code @PostUpdate}
     * callback, forcing recompilation on next access.</p>
     *
     * @param sourceKey String identifier of the knowledge base to remove
     */
    public static synchronized void removeRuleBase(String sourceKey) {
        ruleBaseInstances.remove(sourceKey);
    }

    /**
     * Clears all cached KieBase instances.
     *
     * <p>Completely resets the cache by creating a new {@link QueueCache} instance
     * with the same configuration. This forces all knowledge bases to be recompiled
     * on next access. Useful for administrative cache clearing or after bulk rule updates.</p>
     */
    public static synchronized void flushAllCached() {
        // Create new cache instance to clear all cached entries
        ruleBaseInstances = new QueueCache<String, KieBase>(4, 2048, DateUtils.MILLIS_PER_DAY, null);
    }
}
