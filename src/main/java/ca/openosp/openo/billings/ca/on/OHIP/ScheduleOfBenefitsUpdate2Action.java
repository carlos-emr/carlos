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


package ca.openosp.openo.billings.ca.on.OHIP;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.billings.ca.on.data.BillingCodeData;

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ScheduleOfBenefitsUpdate2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();



    public String execute() {

        boolean forceUpdate = request.getAttribute("forceUpdate") == null ? "true".equals(request.getParameter("forceUpdate")) : (Boolean) request.getAttribute("forceUpdate");

        if (forceUpdate) {
            List codes = (List) request.getAttribute("warnings");
            BillingCodeData bc = new BillingCodeData();
            ArrayList list = new ArrayList();
            for (int i = 0; i < codes.size(); i++) {
                Map code = (Map) (codes.get(i));

                String effDate;
                if (((String) code.get("effectiveDate")).equalsIgnoreCase("null")) {
                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat dfmt = new SimpleDateFormat();
                    dfmt.applyPattern("yyyy-MM-dd");
                    Date d = c.getTime();
                    effDate = dfmt.format(d);
                } else {
                    effDate = ((String) code.get("effectiveDate")).substring(0, 4) + "-" + ((String) code.get("effectiveDate")).substring(4, 6) + "-"
                            + ((String) code.get("effectiveDate")).substring(6, 8);
                }
                String termDate;
                if (((String) code.get("terminactionDate")).equals("99999999")) {
                    termDate = "9999-12-31";
                } else {
                    termDate = ((String) code.get("terminactionDate")).substring(0, 4) + "-" + ((String) code.get("terminactionDate")).substring(4, 6) + "-";
                    if (((String) code.get("terminactionDate")).substring(6, 8).equals("00")) {
                        termDate += "01";
                    } else {
                        termDate += ((String) code.get("terminactionDate")).substring(6, 8);
                    }
                }
                try {
                    bc.insertBillingCode(code.get("newprice").toString(), (String) code.get("feeCode"), effDate, (String) code.get("description"), termDate);
                    Hashtable h = new Hashtable();
                    h.put("code", code.get("feeCode"));
                    h.put("value", code.get("newprice").toString());
                    list.add(h);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }


            }
            request.setAttribute("changes", list);
            request.setAttribute("warnings", null);
        } else {
            String[] changes = request.getParameterValues("change");
            if (changes != null) {
                BillingCodeData bc = new BillingCodeData();
                ArrayList list = new ArrayList();
                MiscUtils.getLogger().debug("changes #" + changes.length);

                for (int i = 0; i < changes.length; i++) {
                    MiscUtils.getLogger().debug(changes[i]);
                    String[] change = changes[i].split("\\|");
                    if (change != null && change.length == 5) {
                        //change[0] // billing code
                        //change[1] // value
                        //change[2] //effectiveDate
                        //change[3] //terminactionDate
                        //change[4] //description

                        String effDate;
                        if (change[2].equalsIgnoreCase("null")) {
                            Calendar c = Calendar.getInstance();
                            SimpleDateFormat dfmt = new SimpleDateFormat();
                            dfmt.applyPattern("yyyy-MM-dd");
                            Date d = c.getTime();
                            effDate = dfmt.format(d);
                        } else {
                            effDate = change[2].substring(0, 4) + "-" + change[2].substring(4, 6) + "-" + change[2].substring(6, 8);
                        }

                        String termDate;
                        if (change[3].equals("99999999")) {
                            termDate = "9999-12-31";
                        } else {
                            termDate = change[3].substring(0, 4) + "-" + change[3].substring(4, 6) + "-";
                            if (change[3].substring(6, 8).equals("00")) {
                                termDate += "01";
                            } else {
                                termDate += change[3].substring(6, 8);
                            }
                        }
                        try {
                            bc.insertBillingCode(change[1], change[0], effDate, change[4], termDate);
                            Hashtable h = new Hashtable();
                            h.put("code", change[0]);
                            h.put("value", change[1]);
                            list.add(h);
                        } catch (Exception e) {
                            MiscUtils.getLogger().error("Error", e);
                        }

                        request.setAttribute("changes", list);
                    } else {
                        MiscUtils.getLogger().debug("test was null");
                    }

                }
            }
        }
        return SUCCESS;
    }

    public ScheduleOfBenefitsUpdate2Action() {
    }

}
