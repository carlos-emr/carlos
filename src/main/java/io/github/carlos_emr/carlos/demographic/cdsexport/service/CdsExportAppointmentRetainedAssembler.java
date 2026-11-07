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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.*;

/** Every retained appointment field is kept or replaced under its verified original occurrence. */
public final class CdsExportAppointmentRetainedAssembler {
    public CdsExportRetainedSectionWriter.Prepared prepare(int patient, CdsExportAppointmentFieldReconciler.Result decision,
            CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked, List<CdsExportRetainedFieldDto> supplied,
            CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException {
        values.requireActive(); if (!decision.mapping().nativeRowRepresentable())throw new IOException("CDS_EXPORT_APPOINTMENT_MAPPING_REQUIRES_REVIEW");
        if (!index.sourceManifestSha256().equals(decision.sourceManifestSha256())||!index.sourceManifestSha256().equals(checked.sourceManifestSha256())||!index.appliedUnits().contains(decision.unitAddress()))scope();
        CdsExportAppointmentXmlWriter.validate(decision.mapping().value());
        var fields=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.APPOINTMENTS, decision.unitAddress(), index, checked, supplied, checkpoint);
        String root=null; var relative=new LinkedHashMap<String, CdsExportRetainedFieldDto>();
        for (var field:fields) {
            String actual=CdsExportRetainedOccurrencePath.root(field, CdsCategory.APPOINTMENTS); if (root==null)root=actual; else if (!root.equals(actual))scope();
            String path=field.sourcePath().length()==root.length()?"":field.sourcePath().substring(root.length()+1); if (relative.putIfAbsent(path, field)!=null)scope();
        }
        var value=decision.mapping().value(); var parts=new LinkedHashMap<String, CdsExportXmlComposer.Content>();
        text(parts, "AppointmentTime", value.timing().wireTime()); text(parts, "Duration", Integer.toString(value.timing().durationMinutes())); text(parts, "AppointmentStatus", value.status());
        parts.put("AppointmentDate", xml-> xml.element("cds_dt", "FullDate", value.timing().date().lexical()));
        parts.put("Provider", value.provider()==null?null:xml-> new CdsExportPublicProviderXmlWriter().write(value.provider(), xml, false)); text(parts, "AppointmentPurpose", value.purpose()); text(parts, "AppointmentNotes", value.notes());
        var changes=new ArrayList<CdsExportRetainedSectionWriter.Change>();
        for (var part:parts.entrySet()) {
            checkpoint.run(); String path=part.getKey()+"[1]"; var prior=new HashSet<String>();
            for (var field:relative.entrySet())if (field.getKey().equals(path)||field.getKey().startsWith(path+"/"))prior.add(field.getValue().id());
            if (!prior.isEmpty()||part.getValue()!=null)changes.add(new CdsExportRetainedSectionWriter.Change(path, prior, part.getValue()));
        }
        values.requireActive(); checkpoint.run(); return new CdsExportRetainedSectionWriter().prepareChanges(fields, values, changes, checkpoint);
    }
    private static void text(Map<String, CdsExportXmlComposer.Content> parts, String name, String value){parts.put(name, value==null?null:xml-> xml.text(value)); }
    private static void scope()throws IOException{throw new IOException("CDS_EXPORT_APPOINTMENT_RETAINED_ASSEMBLY_SCOPE_INVALID"); }
}
