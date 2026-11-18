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
 *
 */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Group;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Structure;

/**
 * The names HAPI 1.x gave the segments of a generic (structure-less) HL7 message, rebuilt on top
 * of HAPI 2.x so the handlers that depend on them keep returning what they returned.
 *
 * <p>{@link DefaultGenericHandler} and {@link IHAHandler} parse messages whose structure HAPI does
 * not know, which HAPI represents as a flat {@code GenericMessage}. They find the nth request as
 * {@code "OBR" + n} and walk the names to collect the observations and comments that follow it.
 * Both HAPI lines name a segment after its type, but they disagree on repeats:
 * <ul>
 *   <li>HAPI 1.x folded back-to-back segments of one type into a single name only the first time
 *   that type appeared ({@code OBR}), and gave every later segment of the type a name of its own
 *   ({@code OBR2}, {@code OBR3}), even when two were adjacent.</li>
 *   <li>HAPI 2.x folds every run of adjacent same-type segments, so {@code OBR20} can stand for
 *   what 1.x called {@code OBR20} and {@code OBR21}, and "the 21st request" is no longer
 *   {@code "OBR21"}.</li>
 * </ul>
 * Looking requests up by the 2.x names drops results without any error, so this class lists the
 * segments in message order (a repetition is expanded through {@link Group#getAll(String)}) and
 * assigns the 1.x names again, with the same {@code getNames}, {@code getAll} and {@code get}
 * contract the handlers already used on {@link Group}.
 *
 * <p>Only segments are listed. Below the root of a structured message (for example
 * {@code ORU_R01}) everything but {@code MSH} is a group, which the handlers never matched by
 * name either.
 *
 * @since 2026-10-29
 */
final class GenericSegmentNames {

    private final List<String> names = new ArrayList<>();
    private final Map<String, List<Segment>> segmentsByName = new HashMap<>();

    private GenericSegmentNames() {
    }

    /**
     * Lists the segments directly under {@code root} and names them as HAPI 1.x did.
     *
     * @param root the root of a parsed message, as returned by {@code Terser.getFinder().getRoot()}
     * @return the 1.x names of the root's segments
     * @throws HL7Exception if HAPI cannot enumerate the root's children
     */
    static GenericSegmentNames of(Group root) throws HL7Exception {
        List<Segment> ordered = new ArrayList<>();
        for (String name : root.getNames()) {
            for (Structure structure : root.getAll(name)) {
                if (structure instanceof Segment segment) {
                    ordered.add(segment);
                }
            }
        }

        GenericSegmentNames result = new GenericSegmentNames();
        // entries already named per segment type, and the type of the entry that is still open
        Map<String, Integer> entriesPerType = new HashMap<>();
        String openFirstRunType = null;
        for (Segment segment : ordered) {
            String type = segment.getName();
            int entries = entriesPerType.getOrDefault(type, 0);
            if (entries == 0) {
                result.addEntry(type, segment);
                entriesPerType.put(type, 1);
                openFirstRunType = type;
            } else if (type.equals(openFirstRunType)) {
                result.segmentsByName.get(type).add(segment);
            } else {
                entries++;
                entriesPerType.put(type, entries);
                result.addEntry(type + entries, segment);
                openFirstRunType = null;
            }
        }
        return result;
    }

    private void addEntry(String name, Segment segment) {
        names.add(name);
        List<Segment> segments = new ArrayList<>();
        segments.add(segment);
        segmentsByName.put(name, segments);
    }

    /**
     * @return the names in message order, like {@link Group#getNames()}
     */
    String[] getNames() {
        return names.toArray(new String[0]);
    }

    /**
     * @param name one of {@link #getNames()}
     * @return every repetition under that name, like {@link Group#getAll(String)}
     * @throws HL7Exception if there is no such name
     */
    Segment[] getAll(String name) throws HL7Exception {
        List<Segment> segments = segmentsByName.get(name);
        if (segments == null) {
            throw new HL7Exception(name + " does not exist in the message");
        }
        return segments.toArray(new Segment[0]);
    }

    /**
     * @param name one of {@link #getNames()}
     * @return the first repetition under that name, like {@link Group#get(String)}
     * @throws HL7Exception if there is no such name
     */
    Segment get(String name) throws HL7Exception {
        return getAll(name)[0];
    }
}
