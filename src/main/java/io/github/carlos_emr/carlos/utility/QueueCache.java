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


import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public final class QueueCache<K, V> {
    private static Logger logger = MiscUtils.getLogger();
    private static Timer timer = null;
    private HashMap<K, V>[] data;
    private int maxPoolSize;
    private QueueCacheValueCloner<V> cloner;

    public QueueCache(int pools, int objectsToCache, long maxTimeToCache, QueueCacheValueCloner<V> cloner) {
        this(pools, objectsToCache, cloner);
        Class var6 = QueueCache.class;
        synchronized (QueueCache.class) {
            if (timer == null) {
                timer = new Timer(QueueCache.class.getName(), true);
            }
        }

        timer.schedule(new QueueCache.ShiftTimerTask(), maxTimeToCache / (long) pools, maxTimeToCache / (long) pools);
    }

    public QueueCache(int pools, int objectsToCache, QueueCacheValueCloner<V> cloner) {
        this.cloner = null;
        this.cloner = cloner;
        this.data = new HashMap[pools];
        this.maxPoolSize = Math.max(10, objectsToCache / pools);

        for (int i = 0; i < pools; ++i) {
            this.data[i] = new HashMap((int) ((double) this.maxPoolSize * 1.5D));
        }

    }

    public void put(K key, V value) {
        Map<K, V> pool = this.data[0];
        if (this.cloner == null) {
            synchronized (pool) {
                pool.put(key, value);
            }
        } else {
            V duplicate = this.cloner.cloneBean(value);
            synchronized (pool) {
                pool.put(key, duplicate);
            }
        }

        int poolSize;
        synchronized (pool) {
            poolSize = pool.size();
        }

        if (poolSize > this.maxPoolSize) {
            this.shiftPools();
        }

    }

    private void shiftPools() {
        synchronized (this.data) {
            for (int i = this.data.length - 1; i > 0; --i) {
                this.data[i] = this.data[i - 1];
            }

            this.data[0] = new HashMap((int) ((double) this.maxPoolSize * 1.5D));
        }
    }

    public int[] getPoolSizes() {
        int[] sizes = new int[this.data.length];
        synchronized (this.data) {
            for (int i = 0; i < this.data.length; ++i) {
                sizes[i] = this.data[i].size();
            }

            return sizes;
        }
    }

    public void remove(K key) {
        for (int i = 0; i < this.data.length; ++i) {
            Map<K, V> pool = this.data[i];
            synchronized (pool) {
                pool.remove(key);
            }
        }

    }

    public V get(K key) {
        for (int i = 0; i < this.data.length; ++i) {
            Map<K, V> pool = this.data[i];
            synchronized (pool) {
                V result = pool.get(key);
                if (result != null) {
                    if (this.cloner == null) {
                        return result;
                    }

                    V duplicate = this.cloner.cloneBean(result);
                    return duplicate;
                }
            }
        }

        return null;
    }

    public int size() {
        int count = 0;

        for (int i = 0; i < this.data.length; ++i) {
            Map<K, V> pool = this.data[i];
            synchronized (pool) {
                count += pool.size();
            }
        }

        return count;
    }

    private class ShiftTimerTask extends TimerTask {
        private ShiftTimerTask() {
        }

        public void run() {
            try {
                QueueCache.this.shiftPools();
            } catch (Exception var2) {
                QueueCache.logger.error("Error", var2);
            }

        }
    }
}