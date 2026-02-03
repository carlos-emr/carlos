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
 */
package io.github.carlos_emr.carlos.utility;


import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;

public class AccumulatorMap<K> extends TreeMap<K, Integer> {
    public AccumulatorMap() {
    }

    public void increment(K key) {
        this.increment(key, 1);
    }

    public void increment(K key, int value) {
        Integer previousValue = (Integer) this.get(key);
        if (previousValue == null) {
            this.put(key, value);
        } else {
            this.put(key, previousValue + value);
        }

    }

    public int getTotalOfAllValues() {
        int total = 0;

        Integer i;
        for (Iterator i$ = this.values().iterator(); i$.hasNext(); total += i) {
            i = (Integer) i$.next();
        }

        return total;
    }

    public void addAccumulator(AccumulatorMap<K> accumulatorMap) {
        Iterator i$ = accumulatorMap.entrySet().iterator();

        while (i$.hasNext()) {
            Entry<K, Integer> entry = (Entry) i$.next();
            this.increment(entry.getKey(), (Integer) entry.getValue());
        }

    }

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
