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
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;

/**
 * SOAP web service transfer object for program-provider assignment data in inter-EMR communication.
 *
 * @since 2012-08-13
 */
public final class ProgramProviderTransfer {

    private Long id;
    private Integer programId;
    private String providerNo;
    private Long roleId;

    public Long getId() {
        return (id);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getProgramId() {
        return (programId);
    }

    public void setProgramId(Integer programId) {
        this.programId = programId;
    }

    public String getProviderNo() {
        return (providerNo);
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Long getRoleId() {
        return (roleId);
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public static ProgramProviderTransfer toTransfer(ProgramProvider programProvider) {
        if (programProvider == null) return (null);

        ProgramProviderTransfer programTransfer = new ProgramProviderTransfer();

        programTransfer.setId(programProvider.getId());
        programTransfer.setProgramId(programProvider.getProgramId().intValue());
        programTransfer.setProviderNo(programProvider.getProviderNo());
        programTransfer.setRoleId(programProvider.getRoleId());

        return (programTransfer);
    }

    public static ProgramProviderTransfer[] toTransfers(List<ProgramProvider> programProviders) {
        ArrayList<ProgramProviderTransfer> results = new ArrayList<ProgramProviderTransfer>();

        for (ProgramProvider programProvider : programProviders) {
            results.add(toTransfer(programProvider));
        }

        return (results.toArray(new ProgramProviderTransfer[0]));
    }

    @Override
    public String toString() {
        return (ReflectionToStringBuilder.toString(this));
    }
}
