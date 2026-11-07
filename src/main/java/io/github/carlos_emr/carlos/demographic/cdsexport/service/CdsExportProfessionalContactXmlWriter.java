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

import java.io.IOException;

/** Writes the two fixed physician relationships without inventing identifiers, authors or missing name parts. */
public final class CdsExportProfessionalContactXmlWriter {
    public void write(CdsExportProfessionalContactMapper.Result result, CdsExportXmlComposer.ContentWriter xml, Runnable checkpoint)throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded-> writeValue(result, guarded, checkpoint));
    }
    private void writeValue(CdsExportProfessionalContactMapper.Result result, CdsExportXmlComposer.ContentWriter xml, Runnable checkpoint)throws IOException {
        java.util.Objects.requireNonNull(result); java.util.Objects.requireNonNull(checkpoint); checkpoint.run();
        if (!result.representable())throw new IOException("CDS_EXPORT_PROFESSIONAL_CONTACT_REQUIRES_REVIEW");
        for (var physician:result.physicians()){checkpoint.run(); xml.start("cds", physician.role().wireName()); writeNameContent(physician, xml); xml.end(); }
    }
    void writeNameContent(CdsExportProfessionalContactMapper.Physician physician, CdsExportXmlComposer.ContentWriter xml)throws IOException {xml.element("cds_dt", "FirstName", physician.name().firstName()); xml.element("cds_dt", "LastName", physician.name().lastName()); }
}
