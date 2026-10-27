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

/** A bounded field descriptor. Value bytes live in the record's opaque, immutable values spool. */
public record CdsFieldDto(String path, String namespace, String kind, long offset, long length,
        String sha256, String schemaType, String whitespace, String schemaDefault) {
    public CdsFieldDto {
        if (schemaType == null) schemaType = "";
        if (whitespace == null) whitespace = "preserve";
        if (schemaDefault==null) schemaDefault="";
        if (path == null || namespace == null || kind == null || offset < 0 || length < 0
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid CDS field descriptor");
        }
        if (schemaDefault.length()>64 || kind.equals("BINARY") && !schemaDefault.isEmpty()) throw new IllegalArgumentException("Invalid CDS schema default");
        if (!kind.equals("TEXT") && !kind.equals("ATTRIBUTE") && !kind.equals("BINARY")) {
            throw new IllegalArgumentException("Invalid CDS field kind");
        }
        if (!whitespace.equals("preserve") && !whitespace.equals("replace") && !whitespace.equals("collapse")) {
            throw new IllegalArgumentException("Invalid CDS whitespace policy");
        }
    }

    public CdsFieldDto(String path, String namespace, String kind, long offset, long length, String sha256, String schemaType, String whitespace) {
        this(path, namespace, kind, offset, length, sha256, schemaType, whitespace, "");
    }

    public CdsFieldDto(String path, String namespace, String kind, long offset, long length, String sha256) {
        this(path, namespace, kind, offset, length, sha256, "", "preserve");
    }
}
