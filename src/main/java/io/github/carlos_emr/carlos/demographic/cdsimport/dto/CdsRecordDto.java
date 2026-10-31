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

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** One source section occurrence, referenced by byte ranges rather than an in-memory XML tree. */
public record CdsRecordDto(String section, long ordinal, String sourceXPath, Path fieldsFile,
        long fieldsOffset, long fieldsLength, Path valuesFile, String sha256, long fieldCount,
        CdsCorrectionSetDto corrections) {
    public CdsRecordDto(String section, long ordinal, String sourceXPath, Path fieldsFile, long fieldsOffset,
            long fieldsLength, Path valuesFile, String sha256, long fieldCount) {
        this(section, ordinal, sourceXPath, fieldsFile, fieldsOffset, fieldsLength, valuesFile, sha256, fieldCount, null);
    }
    public CdsRecordDto {
        if (section == null || sourceXPath == null || fieldsFile == null || valuesFile == null
                || ordinal < 1 || fieldsOffset < 0 || fieldsLength < 0 || fieldCount < 0
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid CDS record descriptor");
        }
    }

    public CdsRecordDto withCorrections(CdsCorrectionSetDto value) {
        return new CdsRecordDto(section, ordinal, sourceXPath, fieldsFile, fieldsOffset, fieldsLength, valuesFile, sha256, fieldCount, value);
    }

    /** Clinical unit identity binds reviewed changes while source metadata verification stays original. */
    public String effectiveSha256() {
        if (corrections==null) return sha256;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("cds-logical-record-v1\0"+sha256+"\0"+corrections.sha256()+"\0"+corrections.acceptedXmlSha256()).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA256 unavailable"); }
    }
}
