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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * One patient's occurrence index: every occurrence the patient's document must hold and every
 * retained field it must account for, with what the document did with each, kept until the
 * document is proven.
 *
 * <p>The index is held in memory for one patient, so it is packed. An occurrence is its category
 * and the 32 bytes of its key (33 bytes), one state byte and, once the composer has disposed of
 * it, the 32 bytes of its output digest: 66 bytes. A retained field is the 32 bytes of its id
 * and one state byte: 33 bytes. Both are sorted once, when the inventory is complete, and looked
 * up by binary search. No count refuses a chart; a million occurrences take 66 MiB. The keys of
 * each kind share one Java array, which holds about 65 million of them.
 *
 * <p>The order of the occurrences is the order of {@link CdsExportIdentityDto#compareTo}: the
 * category's wire name, then the occurrence key. Identities and dispositions are rebuilt from the
 * packed form when they are read; none is kept as an object.
 *
 * @since 2026-11-14
 */
final class CdsExportOccurrenceIndex {
    private static final int DISPOSED = 1, RECEIVED = 2, BLOCKED = 4, BLOCKER_SHIFT = 3;

    /** The categories in the order of their wire names: an occurrence's first key byte. */
    private static final CdsCategory[] BY_WIRE_NAME = byWireName();

    private static final int[] RANK = ranks();

    private final String patientKey;
    private final Keys occurrences = new Keys(33), fields = new Keys(32);
    private byte[] digests;
    private long disposed, received, claimed;
    private boolean sealed;

    CdsExportOccurrenceIndex(String patientKey) {
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        this.patientKey = patientKey;
    }

    private static CdsCategory[] byWireName() {
        var categories = CdsCategory.values().clone();
        Arrays.sort(categories, Comparator.comparing(CdsCategory::wireName));
        return categories;
    }

    private static int[] ranks() {
        int[] ranks = new int[CdsCategory.values().length];
        for (int rank = 0; rank < BY_WIRE_NAME.length; rank++)
            ranks[BY_WIRE_NAME[rank].ordinal()] = rank;
        return ranks;
    }

    private byte[] key(CdsExportIdentityDto identity) {
        Objects.requireNonNull(identity);
        if (!patientKey.equals(identity.patientKey())) return null;
        byte[] key = new byte[33];
        key[0] = (byte) RANK[identity.category().ordinal()];
        System.arraycopy(HexFormat.of().parseHex(identity.occurrenceKey()), 0, key, 1, 32);
        return key;
    }

    private static byte[] field(String id) {
        return id != null && id.matches("[0-9a-f]{64}") ? HexFormat.of().parseHex(id) : null;
    }

    /** Adds one occurrence the document must hold; a repeat is reported by {@link #seal}. */
    void expect(CdsExportIdentityDto identity) {
        byte[] key = key(identity);
        if (sealed || key == null)
            throw new IllegalStateException("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        occurrences.add(key);
    }

    /** Adds one retained field the document must account for; a repeat is reported by {@link #seal}. */
    void expectField(String id) {
        byte[] key = field(id);
        if (sealed || key == null)
            throw new IllegalStateException("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        fields.add(key);
    }

    /**
     * Completes the inventory: sorts both kinds once.
     *
     * @return false when an occurrence or a field was expected twice
     */
    boolean seal() {
        if (sealed) throw new IllegalStateException("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        sealed = true;
        boolean distinct = occurrences.seal();
        distinct &= fields.seal();
        digests = new byte[Math.multiplyExact(occurrences.size(), 32)];
        return distinct;
    }

    private void requireSealed() {
        if (!sealed) throw new IllegalStateException("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
    }

    int occurrences() {
        return occurrences.size();
    }

    /** How many occurrences of {@code category} are expected. */
    long occurrences(CdsCategory category) {
        requireSealed();
        long count = 0;
        for (int i = 0; i < occurrences.size(); i++)
            if (occurrences.first(i) == RANK[category.ordinal()]) count++;
        return count;
    }

    int fields() {
        return fields.size();
    }

    private int find(CdsExportIdentityDto identity) {
        requireSealed();
        byte[] key = key(identity);
        return key == null ? -1 : occurrences.find(key);
    }

    boolean expects(CdsExportIdentityDto identity) {
        return find(identity) >= 0;
    }

    /**
     * Records what the composer did with one expected occurrence.
     *
     * @return false when the occurrence is not expected or already has a disposition
     */
    boolean dispose(CdsExportDispositionDto disposition) {
        int at = find(disposition.identity());
        if (at < 0 || (occurrences.state(at) & DISPOSED) != 0) return false;
        int state = DISPOSED;
        if (disposition.disposition() == CdsExportDispositionDto.Disposition.BLOCKED)
            state |= BLOCKED | disposition.blocker().ordinal() << BLOCKER_SHIFT;
        else
            System.arraycopy(
                    HexFormat.of().parseHex(disposition.outputDigest()), 0, digests, at * 32, 32);
        occurrences.state(at, (byte) state);
        disposed++;
        return true;
    }

    /**
     * Records the receipt of one occurrence the composer has disposed of.
     *
     * @return false when the occurrence is not expected, has no disposition or already has a
     *     receipt
     */
    boolean receive(CdsExportIdentityDto identity) {
        int at = find(identity);
        if (at < 0) return false;
        int state = occurrences.state(at);
        if ((state & DISPOSED) == 0 || (state & RECEIVED) != 0) return false;
        occurrences.state(at, (byte) (state | RECEIVED));
        received++;
        return true;
    }

    boolean received(CdsExportIdentityDto identity) {
        int at = find(identity);
        return at >= 0 && (occurrences.state(at) & RECEIVED) != 0;
    }

    /**
     * Claims one expected retained field for a receipt.
     *
     * @return false when the field is not expected or was already claimed
     */
    boolean claimField(String id) {
        requireSealed();
        byte[] key = field(id);
        int at = key == null ? -1 : fields.find(key);
        if (at < 0 || fields.state(at) != 0) return false;
        fields.state(at, (byte) 1);
        claimed++;
        return true;
    }

    /** Whether every disposed occurrence has its receipt and every expected field was claimed. */
    boolean reconciled() {
        requireSealed();
        return received == disposed && claimed == fields.size();
    }

    long disposed() {
        return disposed;
    }

    private CdsExportIdentityDto identity(int at) {
        return new CdsExportIdentityDto(
                patientKey,
                BY_WIRE_NAME[occurrences.first(at)],
                HexFormat.of().formatHex(occurrences.keys, at * 33 + 1, at * 33 + 33));
    }

    /** The expected occurrences in identity order; each identity is built when it is read. */
    Set<CdsExportIdentityDto> identities() {
        requireSealed();
        return new AbstractSet<>() {
            @Override
            public Iterator<CdsExportIdentityDto> iterator() {
                return new Iterator<>() {
                    private int next;

                    @Override
                    public boolean hasNext() {
                        return next < occurrences.size();
                    }

                    @Override
                    public CdsExportIdentityDto next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        return identity(next++);
                    }
                };
            }

            @Override
            public int size() {
                return occurrences.size();
            }

            @Override
            public boolean contains(Object value) {
                return value instanceof CdsExportIdentityDto identity && expects(identity);
            }
        };
    }

    /** The expected retained field ids in order; each id is built when it is read. */
    Set<String> fieldIds() {
        requireSealed();
        return new AbstractSet<>() {
            @Override
            public Iterator<String> iterator() {
                return new Iterator<>() {
                    private int next;

                    @Override
                    public boolean hasNext() {
                        return next < fields.size();
                    }

                    @Override
                    public String next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        int at = next++;
                        return HexFormat.of().formatHex(fields.keys, at * 32, at * 32 + 32);
                    }
                };
            }

            @Override
            public int size() {
                return fields.size();
            }

            @Override
            public boolean contains(Object value) {
                if (!(value instanceof String id)) return false;
                byte[] key = field(id);
                return key != null && fields.find(key) >= 0;
            }
        };
    }

    /**
     * The dispositions recorded so far, in identity order, each rebuilt as it is read.
     *
     * @param captureDigest the capture every recorded disposition was checked to name
     */
    Iterator<CdsExportDispositionDto> dispositions(String captureDigest) {
        requireSealed();
        return new Iterator<>() {
            private int next = advance(0);

            private int advance(int from) {
                int at = from;
                while (at < occurrences.size() && (occurrences.state(at) & DISPOSED) == 0) at++;
                return at;
            }

            @Override
            public boolean hasNext() {
                return next < occurrences.size();
            }

            @Override
            public CdsExportDispositionDto next() {
                if (!hasNext()) throw new NoSuchElementException();
                int at = next;
                next = advance(at + 1);
                int state = occurrences.state(at);
                boolean blocked = (state & BLOCKED) != 0;
                return new CdsExportDispositionDto(
                        captureDigest,
                        identity(at),
                        blocked
                                ? CdsExportDispositionDto.Disposition.BLOCKED
                                : CdsExportDispositionDto.Disposition.EMITTED,
                        blocked ? null : HexFormat.of().formatHex(digests, at * 32, at * 32 + 32),
                        blocked
                                ? CdsExportDispositionDto.Blocker.values()[
                                        (state & 0xff) >>> BLOCKER_SHIFT]
                                : null);
            }
        };
    }

    @Override
    public String toString() {
        return "CdsExportOccurrenceIndex[protected]";
    }

    /** Fixed-width keys in one array, sorted once, with one state byte for each. */
    private static final class Keys {
        private final int width;
        private byte[] keys;
        private byte[] state;
        private int count;

        Keys(int width) {
            this.width = width;
            keys = new byte[width * 64];
        }

        void add(byte[] key) {
            long needed = Math.multiplyExact((long) count + 1, width);
            if (needed > keys.length)
                keys =
                        Arrays.copyOf(
                                keys,
                                Math.toIntExact(
                                        Math.max(
                                                needed,
                                                Math.min(
                                                        (long) keys.length + (keys.length >> 1),
                                                        Integer.MAX_VALUE - 8L - (Integer.MAX_VALUE - 8L) % width))));
            System.arraycopy(key, 0, keys, count * width, width);
            count++;
        }

        /** Sorts the keys as unsigned bytes; false when one repeats. */
        boolean seal() {
            var order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            byte[] unsorted = keys;
            Arrays.sort(
                    order,
                    (left, right) ->
                            Arrays.compareUnsigned(
                                    unsorted,
                                    left * width,
                                    left * width + width,
                                    unsorted,
                                    right * width,
                                    right * width + width));
            byte[] sorted = new byte[count * width];
            boolean distinct = true;
            for (int i = 0; i < count; i++) {
                System.arraycopy(unsorted, order[i] * width, sorted, i * width, width);
                if (i > 0
                        && Arrays.equals(
                                sorted,
                                (i - 1) * width,
                                i * width,
                                sorted,
                                i * width,
                                (i + 1) * width)) distinct = false;
            }
            keys = sorted;
            state = new byte[count];
            return distinct;
        }

        int find(byte[] key) {
            int low = 0, high = count - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int order =
                        Arrays.compareUnsigned(
                                keys, middle * width, middle * width + width, key, 0, width);
                if (order == 0) return middle;
                if (order < 0) low = middle + 1;
                else high = middle - 1;
            }
            return -1;
        }

        int size() {
            return count;
        }

        int first(int at) {
            return keys[at * width] & 0xff;
        }

        int state(int at) {
            return state[at];
        }

        void state(int at, byte value) {
            state[at] = value;
        }
    }
}
