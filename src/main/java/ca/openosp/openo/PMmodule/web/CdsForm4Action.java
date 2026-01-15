//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package ca.openosp.openo.PMmodule.web;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.commn.dao.CdsClientFormDao;
import ca.openosp.openo.commn.dao.CdsClientFormDataDao;
import ca.openosp.openo.commn.model.CdsClientForm;
import ca.openosp.openo.commn.model.CdsClientFormData;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

public class CdsForm4Action {

    private static CdsClientFormDao cdsClientFormDao = (CdsClientFormDao) SpringUtils.getBean(CdsClientFormDao.class);
    private static CdsClientFormDataDao cdsClientFormDataDao = (CdsClientFormDataDao) SpringUtils.getBean(CdsClientFormDataDao.class);

    public static CdsClientForm createCdsClientForm(LoggedInInfo loggedInInfo, Integer admissionId, Integer clientId, Date initialContactDate, Date assessmentDate, boolean signed) {
        CdsClientForm cdsClientForm = new CdsClientForm();
        cdsClientForm.setAdmissionId(admissionId);
        cdsClientForm.setCdsFormVersion("4");
        cdsClientForm.setClientId(clientId);
        cdsClientForm.setFacilityId(loggedInInfo.getCurrentFacility().getId());
        cdsClientForm.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        cdsClientForm.setInitialContactDate(initialContactDate);
        cdsClientForm.setAssessmentDate(assessmentDate);
        cdsClientForm.setSigned(signed);
        cdsClientFormDao.persist(cdsClientForm);

        return (cdsClientForm);
    }

    public static void addCdsClientFormData(Integer cdsClientFormId, String question, String answer) {
        answer = StringUtils.trimToNull(answer);
        if (answer == null) return;

        CdsClientFormData cdsClientFormData = new CdsClientFormData();

        cdsClientFormData.setCdsClientFormId(cdsClientFormId);
        cdsClientFormData.setQuestion(question);
        cdsClientFormData.setAnswer(answer);

        cdsClientFormDataDao.persist(cdsClientFormData);
    }
}
