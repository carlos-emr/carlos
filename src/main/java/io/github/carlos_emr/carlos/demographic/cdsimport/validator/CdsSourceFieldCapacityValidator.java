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
package io.github.carlos_emr.carlos.demographic.cdsimport.validator;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

/** Checks the preservation contract before any native chart work can be sealed. */
public final class CdsSourceFieldCapacityValidator {
    private CdsSourceFieldCapacityValidator() { }

    public static void validate(CdsRecordDto record, CdsFieldDto field) throws CdsInputException {
        String path=record.sourceXPath()+(field.path().isEmpty()?"":"/"+field.path());
        capacity(path, 2048, "SOURCE_FIELD_PATH_CAPACITY_EXCEEDED", path);
        capacity(field.namespace(), 250, "SOURCE_FIELD_NAMESPACE_CAPACITY_EXCEEDED", path);
        capacity(field.schemaType().isBlank()?field.kind():field.schemaType(), 4608, "SOURCE_FIELD_TYPE_CAPACITY_EXCEEDED", path);
    }

    private static void capacity(String value, int maximum, String code, String path) throws CdsInputException {
        if (value.codePointCount(0, value.length())>maximum) throw new CdsInputException(code, path, "");
    }
}
