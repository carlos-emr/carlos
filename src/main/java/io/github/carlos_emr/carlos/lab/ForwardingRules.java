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
 * ForwardingRules.java
 *
 * Created on July 16, 2007, 10:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.lab;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Manages lab result forwarding rules that control how incoming lab results
 * are automatically routed to designated providers. Provides lookup of
 * forwarding targets and rule status for a given provider.
 *
 * @since 2007-07-16
 */
public class ForwardingRules {

    Logger logger = MiscUtils.getLogger();

    public ForwardingRules() {
    }

    /**
     * Retrieves the list of providers that the given provider's lab results are forwarded to.
     * Each inner list contains [providerNo, firstName, lastName].
     *
     * @param providerNo String the provider number whose forwarding targets are requested
     * @return ArrayList&lt;ArrayList&lt;String&gt;&gt; list of provider info lists, each containing
     *         provider number, first name, and last name
     */
    public ArrayList<ArrayList<String>> getProviders(String providerNo) {
        ArrayList<ArrayList<String>> ret = new ArrayList<ArrayList<String>>();
        IncomingLabRulesDao dao = SpringUtils.getBean(IncomingLabRulesDao.class);

        for (Object[] i : dao.findRules(providerNo)) {
            Provider p = (Provider) i[1];

            ArrayList<String> info = new ArrayList<String>();
            info.add(p.getProviderNo());
            info.add(p.getFirstName());
            info.add(p.getLastName());
            ret.add(info);
        }
        return ret;
    }

    /**
     * Retrieves the forwarding rule status for the given provider.
     *
     * @param providerNo String the provider number to check
     * @return String the status value from the rule, or "N" if no rule exists
     */
    public String getStatus(String providerNo) {
        String ret = "N";
        IncomingLabRulesDao dao = SpringUtils.getBean(IncomingLabRulesDao.class);
        List<IncomingLabRules> rules = dao.findCurrentByProviderNo(providerNo);
        if (!rules.isEmpty()) {
            IncomingLabRules rule = rules.get(0);
            ret = rule.getStatus();
        }
        return ret;
    }

    /**
     * Checks whether a forwarding rule exists for the given provider.
     *
     * @param providerNo String the provider number to check
     * @return boolean {@code true} if at least one forwarding rule is configured
     */
    public boolean isSet(String providerNo) {
        IncomingLabRulesDao dao = SpringUtils.getBean(IncomingLabRulesDao.class);
        List<IncomingLabRules> rules = dao.findCurrentByProviderNo(providerNo);
        return !rules.isEmpty();
    }

}
