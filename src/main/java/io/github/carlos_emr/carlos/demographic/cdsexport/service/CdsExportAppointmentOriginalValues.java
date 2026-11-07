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
import java.io.*;
import java.util.*;

/** Bounded original provider/purpose values from unchanged, separately applied source-field pages. */
final class CdsExportAppointmentOriginalValues {
    record Result(CdsExportPublicProviderDto provider, String purpose, String notes, List<CdsExportRetainedFieldDto> fields) {
        Result{fields=List.copyOf(fields); }
        @Override public String toString(){return "CdsExportAppointmentOriginalValues.Result[protected]"; }
    }
    Result read(int patient, String address, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> supplied, CdsExportRetainedSectionWriter.Values values, Runnable checkpoint)throws IOException {
        values.requireActive(); var fields=new CdsExportRetainedOccurrenceFields().fieldsFor(patient, CdsCategory.APPOINTMENTS, address, index, checked, supplied, checkpoint);
        var wanted=Map.of("Provider[1]/Name[1]/FirstName[1]", 50, "Provider[1]/Name[1]/LastName[1]", 50, "Provider[1]/OHIPPhysicianId[1]", 6, "AppointmentPurpose[1]", 250, "AppointmentNotes[1]", 32000);
        var result=new HashMap<String, String>(); String root=null; boolean providerSeen=false;
        for (var field:fields) {
            checkpoint.run(); String actual=CdsExportRetainedOccurrencePath.root(field, CdsCategory.APPOINTMENTS); if (root==null)root=actual; else if (!root.equals(actual))scope();
            String relative=field.sourcePath().length()==root.length()?"":field.sourcePath().substring(root.length()+1);
            if (relative.equals("Provider[1]")||relative.startsWith("Provider[1]/"))providerSeen=true;
            var limit=wanted.get(relative); if (limit==null)continue; if (result.containsKey(relative))scope();
            var type=io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema.schemaModel().element("cds", "Appointments");
            io.github.carlos_emr.carlos.demographic.cds.contract.CdsSchemaModel.ExpandedName expected=null;
            for (String segment:relative.split("/")) {
                String local=segment.substring(0, segment.indexOf('[')); var candidates=type.childNames().stream().filter(name-> name.local().equals(local)).toList();
                if (candidates.size()!=1)scope(); expected=candidates.getFirst(); type=type.child(expected.namespace(), expected.local());
            }
            if (expected==null||!expected.namespace().equals(field.namespace())||!field.valueType().equals("{"+type.getTypeNamespace()+"}"+type.getTypeName()))scope();
            var text=new StringBuilder(); char[] block=new char[256];
            try (var input=values.text(field)) {
                int count; while ((count=input.read(block))!=-1){checkpoint.run(); if (count==0||text.length()+count>limit*2L)scope(); text.append(block, 0, count); }
            }finally {Arrays.fill(block, '\0'); }
            String value=text.toString(); if (!CdsExportNativeFieldAssessments.legal(value, limit))scope(); result.put(relative, value);
        }
        var provider=(CdsExportPublicProviderDto) null;
        if (providerSeen) {
            String first=result.get("Provider[1]/Name[1]/FirstName[1]"), last=result.get("Provider[1]/Name[1]/LastName[1]");
            if (first==null||last==null)scope(); try {provider=new CdsExportPublicProviderDto(first, last, result.get("Provider[1]/OHIPPhysicianId[1]")); }catch (IllegalArgumentException failed){scope(); }
        }
        values.requireActive(); checkpoint.run(); return new Result(provider, result.get("AppointmentPurpose[1]"), result.get("AppointmentNotes[1]"), fields);
    }
    private static void scope()throws IOException{throw new IOException("CDS_EXPORT_APPOINTMENT_ORIGINAL_VALUES_REQUIRES_REVIEW"); }
}
