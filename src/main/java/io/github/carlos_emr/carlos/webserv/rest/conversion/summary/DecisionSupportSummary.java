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
package io.github.carlos_emr.carlos.webserv.rest.conversion.summary;


import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline;
import io.github.carlos_emr.carlos.decisionSupport.service.DSService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryItemTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.carlos_emr.CarlosProperties;


@Component
public class DecisionSupportSummary implements Summary {
    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    private DSService dsService = null;

    //protected static final String ELLIPSES = "...";
    //protected static final int MAX_LEN_TITLE = 48;
    //protected static final int CROP_LEN_TITLE = 45;
    //protected static final int MAX_LEN_KEY = 12;
    //protected static final int CROP_LEN_KEY = 9;

    public SummaryTo1 getSummary(LoggedInInfo loggedInInfo, Integer demographicNo, String summaryCode) {

        SummaryTo1 summary = new SummaryTo1("Descion Support", 0, SummaryTo1.DECISIONSUPPORT_CODE);

        List<SummaryItemTo1> list = summary.getSummaryItem();
        int count = 0;

        fillDSGuidelines(loggedInInfo, list, demographicNo, count);
        return summary;
    }

    private void fillDSGuidelines(LoggedInInfo loggedInInfo, List<SummaryItemTo1> list, Integer demographicNo, int count) {
        List<DSGuideline> dsGuidelines = dsService.getDsGuidelinesByProvider(loggedInInfo.getLoggedInProviderNo());
        for (DSGuideline dsGuideline : dsGuidelines) {
            if (CarlosProperties.getInstance().getProperty("dsa.skip." + dsGuideline.getTitle().replaceAll(" ", "_"), "false").equals("true")) {
                continue;
            }
            try {
                List<DSConsequence> dsConsequences = dsGuideline.evaluate(loggedInInfo, "" + demographicNo);
                if (dsConsequences == null) continue;
                for (DSConsequence dsConsequence : dsConsequences) {
                    if (dsConsequence.getConsequenceType() != DSConsequence.ConsequenceType.warning)
                        continue;

                    SummaryItemTo1 summaryItem = new SummaryItemTo1(dsGuideline.getId(), dsGuideline.getTitle(), "action", "dsguideline");

                    String url = "../encounter/decisionSupport/guidelineAction.do?method=detail&guidelineId=" + dsGuideline.getId() + "&provider_no=" + loggedInInfo.getLoggedInProviderNo() + "&demographic_no=" + demographicNo + "&parentAjaxId='); return false;";

                    summaryItem.setDate(dsGuideline.getDateStart());
                    summaryItem.setAction(url);
                    list.add(summaryItem);
                    //if (dsConsequence.getConsequenceStrength() == DSConsequence.ConsequenceStrength.warning) {
                    //   item.setColour("#ff5409;");
                    //}

                }
            } catch (Exception e) {
                logger.error("Unable to evaluate patient against a DS guideline '" + dsGuideline.getTitle() + "' of UUID '" + dsGuideline.getUuid() + "'", e);
            }
        }

    }

}
