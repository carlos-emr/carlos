//CHECKSTYLE:OFF
/**
 * Copyright (C) 2011-2012  PeaceWorks Technology Solutions
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


package ca.openosp.openo.rx.erx.controller;

import java.util.Date;
import java.util.UUID;

import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.DrugDao;
import ca.openosp.openo.commn.model.Drug;
import ca.openosp.openo.utility.SpringUtils;

/**
 * A class to facilitate adding OSCAR prescriptions (imported from elsewhere) to patients' charts.
 */
public class ERxChartUpdater {


    /**
     * Update a patient's chart in OSCAR by adding a new Prescription.
     *
     * @param prescription The prescription to add
     */
    public static void updateChartWithPrescription(Drug prescription) throws IllegalArgumentException {
        // A prescription session bean to perform the loading and saving
        //RxSessionBean bean = new RxSessionBean();
        // The unique ID for the new prescription
        //int rxStashIndex;

        // Throw an error if the ATC code is not set... we need it to store a prescription
        //if (!prescription.isValidAtcCode()) {
        //	throw new IllegalArgumentException("Failed to save prescription because it's ATC code is invalid.");
        //}

        // Save the prescription
        //bean.addAttributeName(prescription.getAtc() + "-" + String.valueOf(bean.getStashIndex()));
        //rxStashIndex = bean.addStashItem(prescription);
        //bean.setStashIndex(rxStashIndex);
        CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);
        ProgramManager programManager = (ProgramManager) SpringUtils.getBean(ProgramManager.class);

        prescription.setPosition(0);
        drugDao.persist(prescription);

        String programId = String.valueOf(programManager.getProgramIdByProgramName("OSCAR"));

        //add an CME entry

        CaseManagementNote cmNote = new CaseManagementNote();
        cmNote.setUpdate_date(new Date());
        cmNote.setObservation_date(new Date());
        cmNote.setDemographic_no(prescription.getDemographicId().toString());
        cmNote.setProviderNo(prescription.getProviderNo());
        cmNote.setSigning_provider_no(prescription.getProviderNo());
        cmNote.setSigned(true);
        cmNote.setHistory("");
        cmNote.setReporter_program_team("0");
        cmNote.setProgram_no(programId);

        cmNote.setUuid(UUID.randomUUID().toString());
        cmNote.setReporter_caisi_role("2");

        cmNote.setNote(prescription.getComment());

        caseManagementManager.saveNoteSimple(cmNote);    //new note id created
        CaseManagementNoteLink cml = new CaseManagementNoteLink();
        cml.setTableName(CaseManagementNoteLink.DRUGS);
        cml.setTableId((long) prescription.getId());
        cml.setNoteId(cmNote.getId()); //new note id
        cml.setOtherId(null);
        caseManagementManager.saveNoteLink(cml);
    }
}
