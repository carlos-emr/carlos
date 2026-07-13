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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.CaseManagementIssueTo1;

public class CaseManagementIssueConverter extends AbstractConverter<CaseManagementIssue, CaseManagementIssueTo1> {

    @Override
    public CaseManagementIssue getAsDomainObject(LoggedInInfo loggedInInfo, CaseManagementIssueTo1 t) throws ConversionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CaseManagementIssueTo1 getAsTransferObject(LoggedInInfo loggedInInfo, CaseManagementIssue d) throws ConversionException {
        CaseManagementIssueTo1 t = new CaseManagementIssueTo1();
        IssueConverter issueConverter = new IssueConverter();
        t.setAcute(d.isAcute());
        t.setCertain(d.isCertain());
        t.setDemographic_no("" + d.getDemographic_no());
        t.setId(d.getId());
        t.setIssue(issueConverter.getAsTransferObject(loggedInInfo, d.getIssue()));
        t.setIssue_id(d.getIssue_id());
        t.setMajor(d.isMajor());
        t.setProgram_id(d.getProgram_id());
        t.setResolved(d.isResolved());
        t.setType(d.getType());
        // Not sure what this one links to t.setUnchecked(d.);
        t.setUpdate_date(d.getUpdate_date());
        return t;
    }


}
