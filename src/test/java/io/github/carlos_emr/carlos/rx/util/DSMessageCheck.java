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
 */
package io.github.carlos_emr.carlos.rx.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import io.github.carlos_emr.carlos.commn.dao.DaoTestFixtures;
import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.prescription.util.DrugrefUtil;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxDsMessageTo1;

import io.github.carlos_emr.OscarProperties;

import io.github.carlos_emr.carlos.commn.model.Facility;

public class DSMessageCheck extends DaoTestFixtures {
    protected Logger logger = MiscUtils.getLogger();


    @Before
    public void before() throws Exception {
        SchemaUtils.restoreTable("user_ds_message_prefs", "property", "AppDefinition");
    }

    @Test
    public void tryDSMessages() throws Exception {

        DrugrefUtil drugrefUtil = new DrugrefUtil();


        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Facility currentFacility = new Facility();
        loggedInInfo.setCurrentFacility(currentFacility);

        String provider = "999998";
        int demographicNo = 1;
        List<String> atcCodes = new ArrayList();
        //atcCodes.add("C09AA01");
        atcCodes.add("B01AA03");
        atcCodes.add("M04AA01");

        //ATC:B01AA03
        //ATC:M04AA01
        //Dins:01918346
        //Dins:02396335

        List regionalIdentifiers = new ArrayList();
        //regionalIdentifiers.add("00893625");
        //regionalIdentifiers.add("02242924");
        //regionalIdentifiers.add("02232605");

        regionalIdentifiers.add("01918346");
        regionalIdentifiers.add("02396335");

        OscarProperties.getInstance().setProperty("drugref_url", "http://localhost:9098/drugref2/DrugrefService");

        OscarProperties.getInstance().setProperty("RX_INTERACTION_LOCAL_DRUGREF_REGIONAL_IDENTIFIER", "yes");


        Locale locale = Locale.CANADA;

        List<RxDsMessageTo1> list = drugrefUtil.getMessages(loggedInInfo, provider, demographicNo, atcCodes, regionalIdentifiers, locale);

        logger.error("SIZE list" + list.size());

        for (RxDsMessageTo1 rxDS : list) {
            logger.error(rxDS);
        }
    }

}
