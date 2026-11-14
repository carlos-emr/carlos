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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

/**
 * The resource settings of one import.
 *
 * <p>Nothing here refuses an import for its size. The size, count and ratio ceilings this record
 * once carried (source bytes, expanded bytes, archive entries, decoded binary bytes and the
 * compression ratio) are retired: every byte the import writes is checked against the free-disk
 * reserve ({@link #minimumFreeDiskBytes}) before and while it is written, so an import stops for
 * its size only when its storage would actually run short, and then under its own code
 * ({@code CDS_INPUT_DISK_RESERVE}, {@code PLANNING_DISK_RESERVE} and the like).
 *
 * <p>The XML structure settings are hostile-input guards that a genuine CDS 5.2 file cannot reach
 * (the pinned schema fixes its names, depth and attributes); each is documented with its reason in
 * {@code docs/cds-import-configuration.md}. Their values protect the parser's memory, which they
 * keep from growing with anything but the schema.
 *
 * @param maxXmlDepth element nesting; the schema's deepest path is far shallower
 * @param maxBoundedScalarCharacters characters of one schema-constrained value or markup
 *     construct held for validation; unconstrained text, numbers and binary values stream
 * @param minimumFreeDiskBytes free space every writer leaves on its store
 * @param stallWindowMillis {@code cds.import.maxDurationMillis}: how long the import worker may
 *     reach no checkpoint before it is treated as dead; not a limit on how long an import runs
 * @param maxPathCharacters one archive member path or record-relative XML path
 * @param maxNamespaceAliases distinct namespace prefixes and URIs of one document
 * @param maxUniqueXmlNames distinct qualified names of one document
 * @param maxXmlNameCharacters one qualified name or prefix
 * @param maxNamespaceUriCharacters one namespace URI
 */
public record CdsInputLimitsDto(
        int maxXmlDepth,
        int maxBoundedScalarCharacters,
        long minimumFreeDiskBytes,
        long stallWindowMillis,
        int maxPathCharacters,
        int maxNamespaceAliases,
        int maxUniqueXmlNames,
        int maxXmlNameCharacters,
        int maxNamespaceUriCharacters) {
    public CdsInputLimitsDto {
        if (maxXmlDepth < 4
                || maxBoundedScalarCharacters < 65536
                || minimumFreeDiskBytes < 0
                || stallWindowMillis < 1
                || maxPathCharacters < 256
                || maxNamespaceAliases < 1
                || maxUniqueXmlNames < 1
                || maxXmlNameCharacters < 64
                || maxNamespaceUriCharacters < 128) {
            throw new IllegalArgumentException("Invalid CDS input resource limits");
        }
    }

    /** The default namespace and name guards with the given structure, disk and stall settings. */
    public CdsInputLimitsDto(
            int maxXmlDepth,
            int maxBoundedScalarCharacters,
            long minimumFreeDiskBytes,
            long stallWindowMillis,
            int maxPathCharacters) {
        this(
                maxXmlDepth,
                maxBoundedScalarCharacters,
                minimumFreeDiskBytes,
                stallWindowMillis,
                maxPathCharacters,
                128,
                4096,
                256,
                4096);
    }

    /** Operational defaults, overridable by the deployment's settings. */
    public static CdsInputLimitsDto defaults() {
        return new CdsInputLimitsDto(256, 65536, 512L << 20, 24L * 60 * 60 * 1000, 8192);
    }

    /** XSD identity tables retain values across the document, including dynamic xsi:type values. */
    public int maxSchemaIdentityValues() { return 16_384; }

    /** Aggregate lexical UTF16 units admitted before the validator can retain identity values. */
    public int maxSchemaIdentityCharacters() { return 1 << 20; }
}
