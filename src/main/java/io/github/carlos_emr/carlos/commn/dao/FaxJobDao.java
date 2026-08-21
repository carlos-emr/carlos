/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxJob;

public interface FaxJobDao extends AbstractDao<FaxJob> {

    public List<FaxJob> getFaxStatusByDateDemographicProviderStatusTeam(String demographic_no, String provider_no,
                                                                        String status, String team, Date beginDate, Date endDate);

    public List<FaxJob> getReadyToSendFaxes(String number);

    public List<FaxJob> getInprogressFaxesByJobId();

    /**
     * Finds fax rows recorded for a provider-assigned job id (e.g. the SRFax FaxDetailsID).
     * Used by the inbound importer for duplicate-import prevention: a remote fax whose id was
     * already imported must not be downloaded and filed a second time.
     *
     * @param jobId provider-assigned job id; never null
     * @return all rows carrying that provider job id, newest state included; empty when none
     */
    public List<FaxJob> findByProviderJobId(Long jobId);

    /**
     * Like {@link #findByProviderJobId}, but scoped to a single receiving
     * account (its fax line). Inbound duplicate detection must not match a
     * fax imported by a DIFFERENT account/backend that happens to reuse the
     * same numeric provider job id.
     */
    public List<FaxJob> findByProviderJobIdAndFaxLine(Long jobId, String faxLine);

    /**
     * Finds fax rows by their stored file name.
     *
     * Used by the inbound importer's pending-file retry to resolve the original
     * "Downloaded but import failed" row (persisted under the quarantined file's name) once
     * the retry import succeeds, so the queue view does not keep advertising a retry that
     * already happened.
     *
     * @param fileName exact stored file name; never null
     * @return all rows carrying that file name; empty when none
     */
    public List<FaxJob> findByFileName(String fileName);

}
