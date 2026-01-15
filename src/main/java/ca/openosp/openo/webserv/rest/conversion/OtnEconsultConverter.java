//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package ca.openosp.openo.webserv.rest.conversion;

import org.apache.commons.text.StringEscapeUtils;
import ca.openosp.openo.commn.model.Document;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.webserv.rest.to.model.OtnEconsult;
import org.springframework.stereotype.Component;

@Component
public class OtnEconsultConverter extends AbstractConverter<Document, OtnEconsult> {

    @Override
    public Document getAsDomainObject(LoggedInInfo loggedInInfo, OtnEconsult t) throws ConversionException {
        Document document = new Document();

        String description = StringEscapeUtils.escapeHtml4(t.getDocDescription());
        document.setDocdesc(description);
        document.setContentdatetime(t.getImportDate());
        document.setContenttype(t.getContentType());
        document.setDoccreator(loggedInInfo.getLoggedInProviderNo());
        document.setDocfilename(t.getFileName());
        document.setDoctype(OtnEconsult.getDoctype().getName());
        document.setUpdatedatetime(t.getImportDate());
        document.setBase64Binary(t.getContents());
        document.setResponsible(loggedInInfo.getLoggedInProviderNo());
        document.setObservationdate(t.getImportDate());

        // expected defaults. These can be adapted as needed.
        document.setStatus('A');
        document.setPublic1(0);
        document.setDocClass("");
        document.setDocSubClass("");
        document.setDocxml("");
        document.setSource("");
        document.setSourceFacility("");
        document.setProgramId(-1);
        document.setNumberofpages(0);
        document.setAppointmentNo(-1);

        return document;
    }

    @Override
    public OtnEconsult getAsTransferObject(LoggedInInfo loggedInInfo, Document d) throws ConversionException {
        // Endpoint is not 2-way. Import only.
        return null;
    }

}
