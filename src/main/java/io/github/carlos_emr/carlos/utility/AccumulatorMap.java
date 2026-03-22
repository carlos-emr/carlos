/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;


import java.util.Iterator;
import java.util.TreeMap;

/**
 * A sorted map that accumulates integer counts for keys.
 *
 * <p>Provides convenience methods for incrementing key counters, computing totals,
 * and counting occurrences of specific values. Useful for tallying frequencies
 * in reporting and statistical contexts.
 *
 * @param <K> the type of keys maintained by this map (must be {@link Comparable})
 * @since 2026-03-17
 */
public class AccumulatorMap<K> extends TreeMap<K, Integer> {

    /**
     * Creates an empty accumulator map.
     */
    public AccumulatorMap() {
    }

    /**
     * Increments the count for the specified key by one.
     *
     * @param key K the key whose count to increment
     */
    public void increment(K key) {
        this.increment(key, 1);
    }

    /**
     * Increments the count for the specified key by the given value.
     * If the key does not exist, it is initialized with the given value.
     *
     * @param key   K the key whose count to increment
     * @param value int the amount to add to the current count
     */
    public void increment(K key, int value) {
        Integer previousValue = (Integer) this.get(key);
        if (previousValue == null) {
            this.put(key, value);
        } else {
            this.put(key, previousValue + value);
        }

    }

    /**
     * Returns the sum of all accumulated values across all keys.
     *
     * @return int the total of all values in the map
     */
    public int getTotalOfAllValues() {
        int total = 0;

        Integer i;
        for (Iterator i$ = this.values().iterator(); i$.hasNext(); total += i) {
            i = (Integer) i$.next();
        }

        return total;
    }

    /**
     * Counts the number of keys that have the specified accumulated value.
     *
     * @param value int the value to search for
     * @return int the number of keys with exactly this value
     */
    public int countInstancesOfValue(int value) {
        int count = 0;
        Iterator i$ = this.values().iterator();

        while (i$.hasNext()) {
            int temp = (Integer) i$.next();
            if (temp == value) {
                ++count;
            }
        }

        return count;
    }
}
