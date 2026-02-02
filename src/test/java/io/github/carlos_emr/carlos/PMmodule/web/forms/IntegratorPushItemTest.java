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
package io.github.carlos_emr.carlos.PMmodule.web.forms;

import java.util.Calendar;

import io.github.carlos_emr.carlos.PMmodule.web.forms.IntegratorPushItem;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public class IntegratorPushItemTest {

    Logger logger = MiscUtils.getLogger();

    @Test
    public void testEstimatedTimeOfCompletion() {
        IntegratorPushItem item = new IntegratorPushItem();
        item.setTotalDemographics(100);
        item.setTotalOutstanding(50);

        Calendar c = Calendar.getInstance();

        logger.info("now it is " + c.getTime());


        c.add(Calendar.HOUR_OF_DAY, -1);
        item.setDateCreated(c.getTime());

        logger.info("push started " + item.getDateCreated());

        logger.info("progress is " + item.getProgressAsPercentageString());

        logger.info("push will finish at ~" + item.getEstimatedDateOfCompletion());


    }
}
