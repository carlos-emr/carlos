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
 * <p>This factory provides centralized management of Drools knowledge bases used throughout
 * the CARLOS EMR system for clinical decision support and business rule processing.
 * It implements a caching mechanism to optimize performance by avoiding repeated
 * compilation of rule bases.</p>
 *
 * <p>The factory uses a QueueCache to store KieBase instances with automatic expiration
 * after 24 hours. This ensures that rule updates can be deployed without requiring
 * system restarts while maintaining good performance through caching.</p>
 *
 * <p>Migrated from legacy {@code org.drools.RuleBase} to {@link org.kie.api.KieBase}
 * as part of the Drools 2.0 to 7.74.1.Final upgrade.</p>
 *
 * <p>Thread Safety: All methods are synchronized to ensure thread-safe access
 * to the shared cache instance.</p>
 *
 * @since 2001-01-01
 * @see org.kie.api.KieBase
 * @see io.github.carlos_emr.carlos.utility.QueueCache
 */
public final class RuleBaseFactory {

    /**
     * Cache for storing compiled KieBase instances.
     *
     * <p>Configuration parameters:</p>
     * <ul>
     *   <li>Queue size: 4 (number of queue buckets for load distribution)</li>
     *   <li>Max entries: 2048 (maximum number of knowledge bases to cache)</li>
     *   <li>Expiry time: 24 hours (DateUtils.MILLIS_PER_DAY)</li>
     *   <li>Expiry handler: null (no custom cleanup on expiration)</li>
     * </ul>
     */
    private static QueueCache<String, KieBase> kieBaseInstances = new QueueCache<String, KieBase>(4, 2048, DateUtils.MILLIS_PER_DAY, null);

    /**
     * Retrieves a cached KieBase instance by its source key.
     *
     * @param sourceKey String unique identifier for the knowledge base
     * @return KieBase the cached knowledge base instance, or null if not found or expired
     */
    public static synchronized KieBase getRuleBase(String sourceKey) {
        return (kieBaseInstances.get(sourceKey));
    }

    /**
     * Stores a KieBase instance in the cache.
     *
     * @param sourceKey String unique identifier for the knowledge base
     * @param kieBase KieBase compiled knowledge base instance to cache
     */
    public static synchronized void putRuleBase(String sourceKey, KieBase kieBase) {
        kieBaseInstances.put(sourceKey, kieBase);
    }

    /**
     * Removes a specific KieBase from the cache.
     *
     * @param sourceKey String identifier of the knowledge base to remove
     */
    public static synchronized void removeRuleBase(String sourceKey) {
        kieBaseInstances.remove(sourceKey);
    }

    /**
     * Clears all cached KieBase instances.
     *
     * <p>Completely resets the cache by creating a new QueueCache instance.
     * This forces all knowledge bases to be recompiled on next access.</p>
     */
    public static synchronized void flushAllCached() {
        kieBaseInstances = new QueueCache<String, KieBase>(4, 2048, DateUtils.MILLIS_PER_DAY, null);
    }
}
