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


package io.github.carlos_emr.carlos.prescript.data;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;

/**
 * Background worker thread that checks for allergy warnings against a specific drug's ATC code.
 * <p>
 * Queries the DrugRef service asynchronously to determine which of a patient's existing
 * allergies are triggered by a given drug. Results are stored in the {@link RxSessionBean}
 * for display in the prescription interface. Database connections are properly released
 * in the finally block to prevent resource leaks.
 *
 * @since 2026-03-17
 */
public class RxAllergyWarningWorker extends Thread {
    RxSessionBean sessionBean = null;
    String atcCode = null;
    Allergy[] allergies = null;

    public RxAllergyWarningWorker() {
    }

    public RxAllergyWarningWorker(RxSessionBean sessionBean, String actCode, Allergy[] allergies) {
        //drugData.getAllergyWarnings(prescript.getAtcCode(),allergies)

        this.atcCode = actCode;
        this.sessionBean = sessionBean;
        this.allergies = allergies;
    }

    public void run() {
        MiscUtils.getLogger().debug("STARTING THREAD - RxAllergyWarningWorker ");

        long start = System.currentTimeMillis();

        Allergy[] allergyWarnings = null;
        try {
            if (atcCode != null && sessionBean != null && allergies != null) {
                RxDrugData drugData = new RxDrugData();
                List<Allergy> missing = new ArrayList<Allergy>();
                allergyWarnings = drugData.getAllergyWarnings(atcCode, allergies, missing);
                if (allergyWarnings != null) {

                    sessionBean.addAllergyWarnings(atcCode, allergyWarnings);
                    sessionBean.addMissingAllergyWarnings(atcCode, missing.toArray(new Allergy[missing.size()]));
                    sessionBean.removeFromWorkingAllergyWarnings(atcCode);
                } else {
                    MiscUtils.getLogger().debug("What to do will allergies atc codes " + atcCode);
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            DbConnectionFilter.releaseAllThreadDbResources();
        }
        long end = System.currentTimeMillis() - start;
        MiscUtils.getLogger().debug("THREAD ENDING -RxAllergyWarningWorker " + end);
    }

}
