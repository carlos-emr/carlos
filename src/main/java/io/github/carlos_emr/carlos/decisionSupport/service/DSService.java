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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.decisionSupport.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DSGuidelineDao;
import io.github.carlos_emr.carlos.commn.dao.DSGuidelineProviderMappingDao;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.decisionSupport.model.DSGuideline;
import io.github.carlos_emr.carlos.decisionSupport.model.DecisionSupportException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abstract base class for clinical decision support services in the CARLOS EMR system.
 * <p>
 * DSService provides the core functionality for evaluating clinical decision support
 * guidelines against patient data and retrieving applicable consequences (warnings and
 * recommendations). It manages access to guideline definitions stored in the database
 * and coordinates their evaluation for specific patients and providers.
 * </p>
 * <p>
 * Concrete implementations may provide additional guideline fetching mechanisms,
 * such as loading from external sources or remote services.
 * </p>
 *
 * @since 2009-07-06
 * @see DSServiceImpl for the default implementation
 * @see DSGuideline for guideline evaluation logic
 * @see DSConsequence for evaluation results
 */
public abstract class DSService {
    private static final Logger logger = MiscUtils.getLogger();
    @Autowired
    protected DSGuidelineDao dSGuidelineDao;
    @Autowired
    protected DSGuidelineProviderMappingDao dSGuidelineProviderMappingDao;

    /**
     * Default constructor for DSService.
     */
    public DSService() {

    }

    /**
     * Evaluates all guidelines assigned to the specified provider against a patient and returns all triggered consequences.
     * <p>
     * Iterates over all guidelines mapped to the provider, evaluates each one against the patient's
     * data, and collects all resulting consequences. Guidelines that fail evaluation are logged and
     * skipped without affecting other guideline evaluations.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo session information for the evaluating provider
     * @param demographicNo String patient identifier for data retrieval
     * @param providerNo String provider identifier to determine which guidelines to evaluate
     * @return List of DSConsequence objects representing all triggered warnings and recommendations
     */
    public List<DSConsequence> evaluateAndGetConsequences(LoggedInInfo loggedInInfo, String demographicNo, String providerNo) {
        logger.debug("passed in providers: " + providerNo + " demographicNo" + demographicNo);
        List<DSGuideline> dsGuidelines = this.dSGuidelineDao.getDSGuidelinesByProvider(providerNo);
        logger.info("Decision Support 'evaluateAndGetConsequences' has been called, reading " + dsGuidelines.size() + " for this providers");
        ArrayList<DSConsequence> allResultingConsequences = new ArrayList<DSConsequence>();
        for (DSGuideline dsGuideline : dsGuidelines) {
            try {
                List<DSConsequence> newConsequences = dsGuideline.evaluate(loggedInInfo, demographicNo);
                if (newConsequences != null) {
                    allResultingConsequences.addAll(newConsequences);
                }
            } catch (DecisionSupportException dse) {
                logger.error("Failed to evaluate the patient against guideline, skipping guideline uuid: " + dsGuideline.getUuid(), dse);
            }
        }
        logger.info("Decision Support 'evaluateAndGetConsequences' finished, returing " + allResultingConsequences.size() + " consequences");
        return allResultingConsequences;
    }

    /**
     * Fetches guidelines from external sources in a background thread.
     *
     * @param loggedInInfo LoggedInInfo session information for the requesting provider
     */
    public void fetchGuidelinesFromServiceInBackground(LoggedInInfo loggedInInfo) {
        DSServiceThread dsServiceThread = new DSServiceThread(this, loggedInInfo);
        dsServiceThread.start();
    }

    /**
     * Fetches guidelines from external sources. Implemented by subclasses to provide
     * specific fetching mechanisms.
     *
     * @param loggedInInfo LoggedInInfo session information for the requesting provider
     */
    public abstract void fetchGuidelinesFromService(LoggedInInfo loggedInInfo);

    /**
     * Retrieves all guidelines assigned to the specified provider.
     *
     * @param provider String provider number identifying the healthcare provider
     * @return List of DSGuideline objects mapped to the provider
     */
    public List<DSGuideline> getDsGuidelinesByProvider(String provider) {
        return dSGuidelineDao.getDSGuidelinesByProvider(provider);
    }

    /**
     * Finds a guideline by its database identifier.
     *
     * @param guidelineId Integer the primary key of the guideline to retrieve
     * @return DSGuideline the guideline entity, or null if not found
     */
    public DSGuideline findGuideline(Integer guidelineId) {
        return dSGuidelineDao.find(guidelineId);
    }
}
