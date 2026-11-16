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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.io.IOException;

/** One pinned date-choice encoding; each standard destination explicitly permits or forbids clock precision. */
final class CdsExportDateXmlWriter {
    private CdsExportDateXmlWriter() {}

    static void write(
            CdsExportXmlComposer.ContentWriter xml,
            String name,
            CdsExportDateDto date,
            boolean permitsClock)
            throws IOException {
        if (date.precision() == CdsExportDateDto.Precision.ABSENT) return;
        xml.start("cds", name);
        content(xml, date, permitsClock);
        xml.end();
    }

    static void content(
            CdsExportXmlComposer.ContentWriter xml, CdsExportDateDto date, boolean permitsClock)
            throws IOException {
        String child =
                switch (date.precision()) {
                    case YEAR -> "YearOnly";
                    case YEAR_MONTH -> "YearMonth";
                    case DATE -> "FullDate";
                    case DATE_TIME -> {
                        if (!permitsClock)
                            throw new IOException("CDS_EXPORT_DATE_CLOCK_NOT_ALLOWED");
                        yield "FullDateTime";
                    }
                    case ABSENT ->
                            throw new IllegalStateException("CDS_EXPORT_NATIVE_DATE_INVALID");
                };
        xml.element("cds_dt", child, date.lexical());
    }
}
