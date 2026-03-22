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

package io.github.carlos_emr.carlos.dxresearch.bean;

import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem;

/**
 * Handler that loads and manages diagnosis research entries for a given patient.
 *
 * <p>Queries the {@link DxresearchDAO} across all configured coding systems to retrieve
 * the patient's diagnosis research records and populates them into a collection of
 * {@link dxResearchBean} instances for use by the view layer.</p>
 *
 * @since 2026-03-17
 */
public class dxResearchBeanHandler {

    Vector<dxResearchBean> dxResearchBeanVector = new Vector<dxResearchBean>();

    /**
     * Constructs a handler and immediately loads diagnosis research data for the specified patient.
     *
     * @param demographicNo String the patient demographic number
     */
    public dxResearchBeanHandler(String demographicNo) {
        init(demographicNo);
    }

    /**
     * Initializes the bean collection by querying diagnosis research records across all
     * configured coding systems for the specified patient.
     *
     * @param demographicNo String the patient demographic number
     * @return boolean {@code true} if initialization succeeded, {@code false} on error
     */
    public boolean init(String demographicNo) {

        boolean verdict = true;
        try {

            dxResearchCodingSystem codingSys = new dxResearchCodingSystem();
            String[] codingSystems = codingSys.getCodingSystems();

            DxresearchDAO dao = SpringUtils.getBean(DxresearchDAO.class);
            for (int idx = 0; idx < codingSystems.length; ++idx) {
                String codingSystem = codingSystems[idx];

                for (Object[] o : dao.findResearchAndCodingSystemByDemographicAndCondingSystem(codingSystem, demographicNo)) {
                    String start_date = String.valueOf(o[0]);
                    String update_date = String.valueOf(o[1]);
                    String description = String.valueOf(o[2]);
                    String cds = String.valueOf(o[3]);
                    String dxresearch_no = String.valueOf(o[4]);
                    String status = String.valueOf(o[5]);
                    String providerNo = String.valueOf(o[6]);
                    dxResearchBean bean = new dxResearchBean(description, dxresearch_no, cds, update_date, start_date, status, codingSystem, providerNo);
                    dxResearchBeanVector.add(bean);

                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            verdict = false;
        }
        return verdict;
    }

    /**
     * Returns the collection of diagnosis research beans loaded for the patient.
     *
     * @return Vector&lt;dxResearchBean&gt; the diagnosis research entries
     */
    public Vector<dxResearchBean> getDxResearchBeanVector() {
        return dxResearchBeanVector;
    }

    /**
     * Returns a list of unique diagnosis codes from the loaded research beans.
     *
     * <p>Note: Does not currently filter by active status (see TODO).</p>
     *
     * @return Vector&lt;String&gt; unique diagnosis codes
     */
    public Vector<String> getActiveCodeList() { //TODO: NEED TO CHECK STATUS
        Vector<String> v = new Vector<String>();
        for (int i = 0; i < dxResearchBeanVector.size(); i++) {
            dxResearchBean dx = dxResearchBeanVector.get(i);
            if (!v.contains(dx.getDxSearchCode())) {
                v.add(dx.getDxSearchCode());
            }
        }
        return v;
    }

    /**
     * Returns a list of unique diagnosis codes prefixed with their coding system type.
     *
     * <p>Each entry is formatted as {@code "codingSystem:diagnosisCode"}
     * (e.g. "icd9:250"). Does not currently filter by active status (see TODO).</p>
     *
     * @return Vector&lt;String&gt; unique coding-system-qualified diagnosis codes
     */
    public Vector<String> getActiveCodeListWithCodingSystem() { //TODO: NEED TO CHECK STATUS
        Vector<String> v = new Vector<String>();
        for (int i = 0; i < dxResearchBeanVector.size(); i++) {
            dxResearchBean dx = dxResearchBeanVector.get(i);
            if (!v.contains(dx.getDxSearchCode())) {
                v.add(dx.getType() + ":" + dx.getDxSearchCode());
            }
        }
        return v;
    }

}
