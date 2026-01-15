//CHECKSTYLE:OFF
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


/*
 * ForwardingRules2Action.java
 *
 * Created on July 16, 2007, 10:48 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.IncomingLabRulesDao;
import ca.openosp.openo.commn.model.IncomingLabRules;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.ForwardingRules;

/**
 * @author wrighd
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ForwardingRules2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    Logger logger = MiscUtils.getLogger();
    private IncomingLabRulesDao dao = SpringUtils.getBean(IncomingLabRulesDao.class);


    public ForwardingRules2Action() {
    }

    public String execute()
            throws ServletException, IOException {

        String providerNo = request.getParameter("providerNo");
        String operation = request.getParameter("operation");
        if (operation == null) {
            operation = "";
        }

        logger.info("ForwardingRules2Action performing: " + operation + " for providers: " + providerNo);
        if (operation.equals("update")) {
            String[] providerNums = request.getParameterValues("providerNums");
            if (providerNums == null) {
                providerNums = new String[0];
            }
            String status = request.getParameter("status");

            logger.info("Updating Rules for providers " + Arrays.toString(providerNums) + "; Status is " + status);
            try {
                // insert forwarding rules
                if (providerNums != null && providerNums.length > 0) {
                    for (IncomingLabRules result : dao.findCurrentByProviderNoAndFrwdProvider(providerNo, "0")) {
                        result.setArchive("1");
                        dao.merge(result);
                        logger.info("Archived rule: " + result);
                    }

                    for (int i = 0; i < providerNums.length; i++) {
                        IncomingLabRules r = new IncomingLabRules();
                        r.setProviderNo(providerNo);
                        r.setFrwdProviderNo(providerNums[i]);
                        dao.persist(r);

                        logger.info("Added rule: " + r);
                    }
                }

                ForwardingRules fr = new ForwardingRules();
                ArrayList<ArrayList<String>> temp = fr.getProviders(providerNo);
                logger.info("Found providers for frwd rules: " + temp);

                // check if there rules are set to forward the labs
                if (temp == null || temp.size() <= 0) {
                    // insert a new rule setting the status to final without forwarding
                    if (status.equals("F")) {
                        IncomingLabRules r = new IncomingLabRules();
                        r.setProviderNo(providerNo);
                        r.setStatus(status);
                        r.setFrwdProviderNo("0");
                        dao.persist(r);

                        logger.info("Inserted a new rule: " + r);

                        // clear the rules if there is no forwarding and the user sets the
                        // status to New... since this is the default
                    } else if (!clearRules(providerNo)) {

                        logger.info("Unable to clear rules...");
                        return "failure";
                    }
                    // if the rules are set to forward the labs update the status of those rules
                } else {
                    for (IncomingLabRules result : dao.findCurrentByProviderNo(providerNo)) {
                        result.setStatus(status);
                        dao.merge(result);

                        logger.info("Set status to " + status + " for " + result);
                    }
                }
            } catch (Exception e) {
                logger.error("Could not update forwarding rules", e);
                return "failure";
            }

        } else if (operation.equals("clear")) {
            if (!clearRules(providerNo))
                return "failure";
        } else if (operation.equals("remove")) {
            String remProviderNum = request.getParameter("remProviderNum");
            if (!removeRule(providerNo, remProviderNum))
                return "failure";
        }

        return SUCCESS;
    }

    private boolean clearRules(String providerNo) {
        for (IncomingLabRules result : dao.findByProviderNo(providerNo)) {
            result.setArchive("1");
            dao.merge(result);
        }
        return true;
    }

    private boolean removeRule(String providerNo, String remProviderNum) {
        try {
            OscarProperties props = OscarProperties.getInstance();
            String autoFileLabs = props.getProperty("AUTO_FILE_LABS");

            ForwardingRules fr = new ForwardingRules();
            String status = fr.getStatus(providerNo);


            if (autoFileLabs != null && autoFileLabs.equalsIgnoreCase("yes") && status.equals("F")) {
                for (IncomingLabRules result : dao.findByProviderNoAndFrwdProvider(providerNo, remProviderNum)) {
                    result.setArchive("1");
                    dao.merge(result);
                }
                IncomingLabRules r = new IncomingLabRules();
                r.setProviderNo(providerNo);
                r.setStatus("F");
                dao.persist(r);
            } else {
                for (IncomingLabRules result : dao.findByProviderNoAndFrwdProvider(providerNo, remProviderNum)) {
                    result.setArchive("1");
                    dao.merge(result);
                }
            }
        } catch (Exception e) {
            logger.error("Could not remove providers '" + remProviderNum + "' from the forwarding rules", e);
            return false;
        }
        return true;
    }
}
