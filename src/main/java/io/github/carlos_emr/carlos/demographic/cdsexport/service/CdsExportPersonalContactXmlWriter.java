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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;

/** Typed personal contacts in their schema position. Other native relation types, history and source conflicts refuse output. */
public final class CdsExportPersonalContactXmlWriter {
    public void write(CdsExportPersonalContactMapper.Result mapping, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> {
            if (mapping == null || !mapping.nativeRepresentable()) throw new IOException("CDS_EXPORT_CONTACT_MAPPING_REQUIRES_REVIEW");
            for (var contact : mapping.contacts()) {
                guarded.start("cds", "Contact");
                content(contact, guarded); guarded.end();
            }
        });
    }
    /** Interior of one typed native contact; the caller owns its schema-positioned Contact boundary. */
    static void content(CdsExportPersonalContactDto contact, CdsExportXmlComposer.ContentWriter xml)throws IOException {
        for (var purpose:contact.purposes()) {
            xml.start("cds", "ContactPurpose"); xml.element("cds_dt", purpose.coded()?"PurposeAsEnum":"PurposeAsPlainText", purpose.value()); xml.end();
        }
        xml.start("cds", "Name"); optional(xml, "cds_dt", "FirstName", contact.firstName()); optional(xml, "cds_dt", "LastName", contact.lastName()); xml.end();
        for (var phone:contact.phones()) {
            xml.start("cds", "PhoneNumber"); xml.attribute("", "phoneNumberType", phone.type()); xml.element("cds_dt", "phoneNumber", phone.number()); optional(xml, "cds_dt", "extension", phone.extension()); xml.end();
        }
        optional(xml, "cds", "EmailAddress", contact.email()); optional(xml, "cds", "Note", contact.note());
    }
    private static void optional(CdsExportXmlComposer.ContentWriter xml, String namespace, String element, String value) throws IOException {
        if (value != null) xml.element(namespace, element, value);
    }
}
