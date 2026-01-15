//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package ca.openosp.openo.drools;

import org.apache.commons.lang3.time.DateUtils;
import org.drools.RuleBase;
import ca.openosp.openo.utility.QueueCache;

/**
 * Factory class for managing Drools RuleBase instances with caching.
 *
 * This factory provides centralized management of Drools rule bases used throughout
 * the OpenO EMR system for clinical decision support and business rule processing.
 * It implements a caching mechanism to optimize performance by avoiding repeated
 * compilation of rule bases.
 *
 * The factory uses a QueueCache to store rule base instances with automatic expiration
 * after 24 hours. This ensures that rule updates can be deployed without requiring
 * system restarts while maintaining good performance through caching.
 *
 * Key features:
 * - Thread-safe singleton pattern for rule base management
 * - Automatic cache expiration after 24 hours
 * - Maximum of 2048 cached rule bases with a queue size of 4
 * - Support for multiple rule bases identified by unique source keys
 *
 * Common use cases in healthcare context:
 * - Clinical guideline enforcement (e.g., drug interaction checking)
 * - Chronic disease management protocols (diabetes, hypertension, CKD)
 * - Preventive care reminders and alerts
 * - Billing validation rules
 * - Quality improvement metrics calculation
 *
 * The source keys typically represent different rule sets such as:
 * - "prevention" for immunization and screening rules
 * - "ckd" for chronic kidney disease management
 * - "diabetes" for diabetes care guidelines
 * - "billing" for claims validation
 *
 * Thread Safety: All methods are synchronized to ensure thread-safe access
 * to the shared cache instance.
 *
 * @since 2001-01-01
 * @see org.drools.RuleBase
 * @see ca.openosp.openo.utility.QueueCache
 */
public final class RuleBaseFactory {

    /**
     * Cache for storing compiled rule base instances.
     *
     * Configuration parameters:
     * - Queue size: 4 (number of queue buckets for load distribution)
     * - Max entries: 2048 (maximum number of rule bases to cache)
     * - Expiry time: 24 hours (DateUtils.MILLIS_PER_DAY)
     * - Expiry handler: null (no custom cleanup on expiration)
     *
     * The cache automatically evicts entries older than 24 hours to ensure
     * rule updates are reflected within a day without manual intervention.
     */
    private static QueueCache<String, RuleBase> ruleBaseInstances = new QueueCache<String, RuleBase>(4, 2048, DateUtils.MILLIS_PER_DAY, null);

    /**
     * Retrieves a cached RuleBase instance by its source key.
     *
     * This method provides thread-safe access to compiled rule bases. If the
     * requested rule base is not in the cache or has expired, null is returned.
     * Callers should check for null and compile/load the rule base if needed.
     *
     * The source key typically identifies the type of rules being accessed,
     * such as "prevention", "ckd", "diabetes", or module-specific identifiers.
     *
     * @param sourceKey String unique identifier for the rule base (e.g., "prevention", "ckd")
     * @return RuleBase the cached rule base instance, or null if not found or expired
     */
    public static synchronized RuleBase getRuleBase(String sourceKey) {
        return (ruleBaseInstances.get(sourceKey));
    }

    /**
     * Stores a RuleBase instance in the cache.
     *
     * Adds or updates a compiled rule base in the cache with the specified key.
     * The rule base will be automatically evicted after 24 hours to ensure
     * updates to rules are reflected within a reasonable timeframe.
     *
     * This method should be called after successfully compiling a rule base
     * from DRL (Drools Rule Language) files or other rule sources.
     *
     * @param sourceKey String unique identifier for the rule base
     * @param ruleBase RuleBase compiled rule base instance to cache
     */
    public static synchronized void putRuleBase(String sourceKey, RuleBase ruleBase) {
        ruleBaseInstances.put(sourceKey, ruleBase);
    }

    /**
     * Removes a specific RuleBase from the cache.
     *
     * Explicitly removes a rule base from the cache before its natural expiration.
     * This is useful when rules have been updated and the cached version needs
     * to be invalidated immediately.
     *
     * Common scenarios for removal:
     * - Rule files have been updated on disk
     * - Administrator manually triggers rule refresh
     * - Error detected in cached rule base
     *
     * @param sourceKey String identifier of the rule base to remove
     */
    public static synchronized void removeRuleBase(String sourceKey) {
        ruleBaseInstances.remove(sourceKey);
    }

    /**
     * Clears all cached RuleBase instances.
     *
     * Completely resets the cache by creating a new QueueCache instance.
     * This forces all rule bases to be recompiled on next access.
     *
     * This method should be used sparingly as it impacts performance by
     * requiring recompilation of all rules. Typical use cases:
     * - Major rule updates across multiple modules
     * - System maintenance or troubleshooting
     * - Memory pressure requiring cache cleanup
     *
     * After calling this method, all subsequent getRuleBase() calls will
     * return null until the rule bases are recompiled and cached again.
     */
    public static synchronized void flushAllCached() {
        // Create new cache instance to clear all cached entries
        ruleBaseInstances = new QueueCache<String, RuleBase>(4, 2048, DateUtils.MILLIS_PER_DAY, null);
    }
}
