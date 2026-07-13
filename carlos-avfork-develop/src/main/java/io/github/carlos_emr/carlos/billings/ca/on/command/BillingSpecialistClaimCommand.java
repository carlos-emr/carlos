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
package io.github.carlos_emr.carlos.billings.ca.on.command;

/**
 * Typed input for building a BillingSpec ON claim.
 */
public record BillingSpecialistClaimCommand(String demoHin,
                                      String demoDob,
                                      String demoHctype,
                                      String xmlBilltype,
                                      String payMethod,
                                      String clinicRefCode,
                                      String clinicNo,
                                      String functionId,
                                      String providers,
                                      String appointmentNo,
                                      String demoName,
                                      String demoSex,
                                      String apptDate,
                                      String svcCode,
                                      String xmlVisittype,
                                      String dxCode,
                                      String apptProvider,
                                      String creator) {
    public BillingSpecialistClaimCommand {
        demoHin = Commands.nullToEmpty(demoHin);
        demoDob = Commands.nullToEmpty(demoDob);
        demoHctype = Commands.nullToEmpty(demoHctype);
        xmlBilltype = Commands.nullToEmpty(xmlBilltype);
        payMethod = Commands.nullToEmpty(payMethod);
        clinicRefCode = Commands.nullToEmpty(clinicRefCode);
        clinicNo = Commands.nullToEmpty(clinicNo);
        functionId = Commands.nullToEmpty(functionId);
        providers = Commands.nullToEmpty(providers);
        appointmentNo = Commands.nullToEmpty(appointmentNo);
        demoName = Commands.nullToEmpty(demoName);
        demoSex = Commands.nullToEmpty(demoSex);
        apptDate = Commands.nullToEmpty(apptDate);
        svcCode = Commands.nullToEmpty(svcCode);
        xmlVisittype = Commands.nullToEmpty(xmlVisittype);
        dxCode = Commands.nullToEmpty(dxCode);
        apptProvider = Commands.nullToEmpty(apptProvider);
        creator = Commands.nullToEmpty(creator);
    }
}
