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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportEngine;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Direction-specific qualification; an absent engine is visibly unavailable, never a partial export fallback. */
@Service
public class CdsExportEngineRegistry {
    private record Qualified(
            CdsExportEngine engine, String digest, Set<CdsLogicalProfile> profiles) {}

    private final Map<CdsLogicalProfile, Qualified> engines;

    @Autowired
    public CdsExportEngineRegistry(ObjectProvider<CdsExportEngine> installed) {
        this(installed.orderedStream().toList());
    }

    public CdsExportEngineRegistry(List<CdsExportEngine> installed) {
        var selected = new EnumMap<CdsLogicalProfile, Qualified>(CdsLogicalProfile.class);
        for (var engine : List.copyOf(installed)) {
            String digest = Objects.requireNonNull(engine).compatibilityDigest();
            if (digest == null || !digest.matches("[0-9a-f]{64}"))
                throw new IllegalStateException("CDS_EXPORT_ENGINE_IDENTITY_INVALID");
            var profiles = Set.copyOf(engine.qualifiedProfiles());
            for (var profile : profiles) {
                if (selected.putIfAbsent(profile, new Qualified(engine, digest, profiles))
                        != null) {
                    throw new IllegalStateException("CDS_EXPORT_ENGINE_AMBIGUOUS");
                }
            }
        }
        engines = Map.copyOf(selected);
    }

    public Set<CdsLogicalProfile> qualifiedProfiles() {
        for (var profile : engines.keySet()) find(profile);
        return engines.keySet();
    }

    /** A null result means this node cannot execute that profile; another compatible durable worker may still exist. */
    public CdsExportEngine find(CdsLogicalProfile profile) {
        var selected = engines.get(Objects.requireNonNull(profile));
        if (selected == null) return null;
        if (!selected.digest().equals(selected.engine().compatibilityDigest())
                || !selected.profiles().equals(Set.copyOf(selected.engine().qualifiedProfiles()))) {
            throw new IllegalStateException("CDS_EXPORT_ENGINE_IDENTITY_CHANGED");
        }
        return selected.engine();
    }

    public CdsExportEngine require(CdsLogicalProfile profile) {
        var selected = find(profile);
        if (selected == null) throw new IllegalStateException("CDS_EXPORT_PROFILE_NOT_QUALIFIED");
        return selected;
    }
}
