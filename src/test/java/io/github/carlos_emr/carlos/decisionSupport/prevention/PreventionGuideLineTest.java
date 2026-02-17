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
package io.github.carlos_emr.carlos.decisionSupport.prevention;

import java.io.InputStream;

import io.github.carlos_emr.carlos.decisionSupport.prevention.DSPreventionDrools;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;

import org.junit.Test;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import static org.junit.Assert.assertNotNull;

public class PreventionGuideLineTest {
    private static final Logger logger = MiscUtils.getLogger();

    byte[] getRuleSet() throws Exception {
        InputStream is = this.getClass().getResourceAsStream("/io/github/carlos_emr/carlos/decisionSupport/prevention/guidelineExample.xml");
        byte[] bytes = IOUtils.toByteArray(is);
        return bytes;
    }

    @Test
    public void testLoading() throws Exception {
        KieBase kieBase = DSPreventionDrools.createRuleBase(getRuleSet());
        assertNotNull(kieBase);
    }

}
