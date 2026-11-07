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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Deterministic original-write candidates only. The reconciler must compare the candidate
 * against the sealed native baseline and all date/precision dependencies. A calendar's
 * midnight candidate does not authorize a different historical gap-adjusted anchor. Offset
 * instants cannot be projected into a historical native civil clock using today's JVM zone.
 */
final class CdsExportImmunizationDateProjection {
    enum Kind{ABSENT, CALENDAR_MIDNIGHT_CANDIDATE, LOCAL_CLOCK, HISTORIC_ZONE_REQUIRED}
    record Expected(Kind kind, String nativeAnchor, String originalHint, String precisionHint, String partialFormat){
        @Override public String toString(){return "CdsExportImmunizationDateProjection.Expected[protected]"; }
    }
    private CdsExportImmunizationDateProjection(){}
    static Expected expected(CdsTemporalDto source)throws IOException{
        new CdsExportPreparedTemporalMapper().map(source, true);
        Kind kind; String anchor;
        switch (source.precision()){
            case UNKNOWN-> {kind=Kind.ABSENT; anchor=null; }
            case YEAR, MONTH, FULL_DATE-> {kind=Kind.CALENDAR_MIDNIGHT_CANDIDATE; anchor=source.earliest()+" 00:00:00"; }
            case LOCAL_DATE_TIME-> {kind=Kind.LOCAL_CLOCK; anchor=source.localDateTime().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")); }
            case OFFSET_DATE_TIME-> {kind=Kind.HISTORIC_ZONE_REQUIRED; anchor=null; }
            default-> throw new IOException("CDS_EXPORT_IMMUNIZATION_DATE_PROJECTION_UNSUPPORTED");
        }
        return new Expected(kind, anchor, source.original(), source.precision().name(), source.legacyPartialFormat());
    }
}
