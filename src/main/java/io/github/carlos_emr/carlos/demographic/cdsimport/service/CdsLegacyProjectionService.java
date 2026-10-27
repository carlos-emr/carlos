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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsLegacyProjectionException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Existing native-only exports cannot claim completeness for the new CDS source representation.
 * This boundary refuses that projection; it does not grant chart or sensitive-result access.
 * Call only after the consumer's existing patient authorization, before committing a response.
 */
@Service
public class CdsLegacyProjectionService {
    private final CdsLegacyProjectionDao records;
    public CdsLegacyProjectionService(CdsLegacyProjectionDao records) { this.records=records; }

    @Transactional(readOnly=true)
    public void requireWholeChartSupported(int demographicNo) { requireSupported(demographicNo, Set.of()); }

    /** An empty category set denotes a complete-chart projection. */
    @Transactional(readOnly=true)
    public void requireSupported(int demographicNo, Set<String> categories) {
        if (demographicNo<1 || categories==null) throw new IllegalArgumentException("Invalid projection context");
        if (records.hasSourceFields(demographicNo, categories)
                || (categories.isEmpty() || categories.contains("LaboratoryResults"))
                    && records.hasTypedLaboratoryResults(demographicNo)) {
            throw new CdsLegacyProjectionException();
        }
    }

    @Transactional(readOnly=true)
    public void requireSoapSupported(int demographicNo, Set<String> categories) {
        try { requireSupported(demographicNo, categories); }
        catch (CdsLegacyProjectionException unsupported) { throw soapFault(unsupported); }
    }

    /** Unscoped incremental native feeds cannot prove absence of source-only imported records. */
    @Transactional(readOnly=true)
    public void requireSoapFeedSupported(Set<String> categories) {
        if (categories==null || categories.isEmpty()) throw new IllegalArgumentException("Invalid feed projection context");
        if (records.hasAnySourceFields(categories)) throw soapFault(new CdsLegacyProjectionException());
    }
    private static org.apache.cxf.binding.soap.SoapFault soapFault(CdsLegacyProjectionException unsupported) {
        return new org.apache.cxf.binding.soap.SoapFault(unsupported.getMessage(),
                org.apache.cxf.binding.soap.SoapFault.FAULT_CODE_CLIENT);
    }

    /** Existing REST resources use a fixed plain-text 409 rather than serializing a native subset. */
    @Transactional(readOnly=true)
    public void requireRestSupported(int demographicNo, Set<String> categories) {
        try { requireSupported(demographicNo, categories); }
        catch (CdsLegacyProjectionException unsupported) {
            throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                    .type(MediaType.TEXT_PLAIN_TYPE).entity(unsupported.getMessage())
                    .header("Cache-Control", "no-store").build());
        }
    }
}
