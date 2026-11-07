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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaModel.ExpandedName;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaModel.Particle;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaModel.ParticleKind;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orders one element's captured children using the pinned grammar, including nested
 * choices. A flattened name map is insufficient: both telephone branches contain an
 * extension, but its valid position depends on the selected branch. Final XML validation
 * remains independent and mandatory; this helper neither validates values nor source provenance.
 */
public final class CdsSchemaContentOrder {
    public record Occurrence(ExpandedName name, long ordinal) {
        public Occurrence { if (name == null || ordinal < 1) throw new IllegalArgumentException("CDS_SCHEMA_CHILD_INVALID"); }
    }
    public List<Occurrence> order(CdsSchemaModel.Type type, List<Occurrence> supplied) throws IOException {
        if (type == null || supplied == null || supplied.size() > 131_072) invalid();
        var values = List.copyOf(supplied); var unique = new HashSet<Occurrence>();
        var byName = new HashMap<ExpandedName, List<Occurrence>>();
        for (var child : values) {
            if (!unique.add(child)) invalid(); byName.computeIfAbsent(child.name(), ignored -> new ArrayList<>()).add(child);
        }
        for (var group : byName.values()) {
            group.sort(Comparator.comparingLong(Occurrence::ordinal)); long expected = 1;
            for (var child : group) if (child.ordinal() != expected++) invalid();
        }
        if (type.content() == null) { if (!values.isEmpty()) invalid(); return List.of(); }
        if (!names(type.content()).containsAll(byName.keySet())) invalid();
        var ordered = arrange(type.content(), values);
        if (ordered.size() != values.size() || !Set.copyOf(ordered).equals(unique)) invalid(); return List.copyOf(ordered);
    }
    private static List<Occurrence> arrange(Particle particle, List<Occurrence> values) throws IOException {
        if (particle.kind() == ParticleKind.ELEMENT) {
            var name = new ExpandedName(particle.namespace(), particle.local());
            if (values.stream().anyMatch(value -> !value.name().equals(name)) || values.size() < particle.minimum() || values.size() > particle.maximum()) invalid();
            return values.stream().sorted(Comparator.comparingLong(Occurrence::ordinal)).toList();
        }
        if (particle.maximum() == 0 && !values.isEmpty()) invalid();
        if (particle.maximum() > 1) throw new IOException("CDS_SCHEMA_REPEATING_GROUP_REQUIRES_REVIEW");
        if (values.isEmpty() && particle.minimum() == 0) return List.of();
        if (particle.kind() == ParticleKind.CHOICE) {
            List<Occurrence> selected = null;
            for (var branch : particle.children()) {
                if (!names(branch).containsAll(values.stream().map(Occurrence::name).toList())) continue;
                List<Occurrence> candidate;
                try { candidate = arrange(branch, values); }
                catch (IOException mismatch) {
                    if (!"CDS_SCHEMA_CONTENT_ORDER_INVALID".equals(mismatch.getMessage())) throw mismatch;
                    continue;
                }
                if (selected != null && !selected.equals(candidate)) throw new IOException("CDS_SCHEMA_CONTENT_ORDER_AMBIGUOUS");
                selected = candidate;
            }
            if (selected == null) invalid(); return selected;
        }
        var result = new ArrayList<Occurrence>(); var seen = new HashSet<Occurrence>();
        for (var member : particle.children()) {
            Set<ExpandedName> names = names(member); var matching = values.stream().filter(value -> names.contains(value.name())).toList();
            for (var child : arrange(member, matching)) { if (!seen.add(child)) invalid(); result.add(child); }
        }
        if (result.size() != values.size()) invalid(); return result;
    }
    private static Set<ExpandedName> names(Particle particle) {
        var names = new HashSet<ExpandedName>(); collect(particle, names); return Set.copyOf(names);
    }
    private static void collect(Particle particle, Set<ExpandedName> names) {
        if (particle.kind() == ParticleKind.ELEMENT) names.add(new ExpandedName(particle.namespace(), particle.local()));
        else for (var child : particle.children()) collect(child, names);
    }
    private static void invalid() throws IOException { throw new IOException("CDS_SCHEMA_CONTENT_ORDER_INVALID"); }
}
