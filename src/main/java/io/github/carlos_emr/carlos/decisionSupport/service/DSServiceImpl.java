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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Default decision support service implementation for the CARLOS EMR system.
 * <p>
 * Provides core decision support functionality using guidelines stored in the local
 * database. External guideline fetching has been disabled; all guidelines are managed
 * locally through the administration interface.
 * </p>
 *
 * @since 2009-07-06
 * @see DSService for the abstract base class
 * @see DSGuideline for guideline evaluation logic
 */
public class DSServiceImpl extends DSService {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Default constructor for DSServiceImpl.
     */
    public DSServiceImpl() {
    }

    /**
     * No-op implementation of external guideline fetching.
     * <p>
     * External guideline fetching is disabled. Decision support uses local
     * database guidelines only.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo session information for the requesting provider
     */
    @Override
    public void fetchGuidelinesFromService(LoggedInInfo loggedInInfo) {
        logger.info("External guideline fetching is disabled. Decision support uses local database guidelines only.");
    }
}
