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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Per-datasource admission capability; durable row locks remain the authority across processes. */
public final class CdsJdbcAdmissionState {
    public static final String ROW = "clinical-maintenance";
    public record Lease(String token, long generation, long connectionId) { }
    private final String lockName;
    private final AtomicReference<Lease> owner = new AtomicReference<>();
    private final ThreadLocal<Lease> readScope = new ThreadLocal<>();

    public CdsJdbcAdmissionState(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) throw new IllegalArgumentException("Database name required");
        try {
            this.lockName = "carlos.cds.maintenance." + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(databaseName.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public String lockName() { return lockName; }
    public Lease owner() { return owner.get(); }
    public boolean install(Lease lease) { return owner.compareAndSet(null, lease); }
    public void release(Lease lease) { owner.compareAndSet(lease, null); }
    public boolean hasReadScope(Lease lease) { return lease != null && owner.get() == lease && readScope.get() == lease; }
    public <T> T read(Lease lease, Supplier<T> work) {
        if (owner.get() != lease) throw new IllegalStateException("Maintenance read owner is absent or fenced");
        Lease previous = readScope.get();
        readScope.set(lease);
        try { return work.get(); }
        finally { if (previous == null) readScope.remove(); else readScope.set(previous); }
    }
}
