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

import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import org.springframework.stereotype.Service;

/** Exact native source values; field classification and XML mapping remain separate reviewed steps. */
@Service
public class CdsExportDemographicCustSourceReader extends CdsExportPatientTableSourceReader {
    private final DemographicCustDao source;
    private final boolean alerts;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportDemographicCustSourceReader(DemographicCustDao source) {
        this(source, true);
    }

    private CdsExportDemographicCustSourceReader(DemographicCustDao source, boolean alerts) {
        super(
                source,
                alerts
                        ? CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CUST
                        : CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS,
                alerts
                        ? java.util.Set.of(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .DEMOGRAPHICS,
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .ALERTS_AND_SPECIAL_NEEDS)
                        : java.util.Set.of(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .DEMOGRAPHICS));
        this.source = source;
        this.alerts = alerts;
    }

    @Override
    public CdsExportNativeSourceReader forRequest(
            io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) {
        return new CdsExportDemographicCustSourceReader(
                source,
                command.categories()
                        .contains(
                                io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                        .ALERTS_AND_SPECIAL_NEEDS));
    }

    @Override
    protected java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> page(
            int patient, Long after, int limit) {
        return alerts
                ? source.findCdsExportRowsAfter(patient, after, limit)
                : source.findCdsExportDemographicRowsAfter(patient, after, limit);
    }
}
