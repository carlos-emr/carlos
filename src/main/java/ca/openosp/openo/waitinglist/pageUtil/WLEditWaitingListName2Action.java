//CHECKSTYLE:OFF
/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

package ca.openosp.openo.waitinglist.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.model.ProviderPreference;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SessionConstants;
import ca.openosp.openo.waitinglist.bean.WLWaitingListNameBeanHandler;
import ca.openosp.openo.waitinglist.util.WLWaitingListNameUtil;
import ca.openosp.openo.util.UtilDateUtilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Date;

public final class WLEditWaitingListName2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private String message = "";

    public String execute()
            throws Exception {


        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): just entering.");
        HttpSession session = request.getSession();
        //LazyValidatorForm wlnForm = (LazyValidatorForm) form;


        String edit = request.getParameter("edit");
        String actionChosen = request.getParameter("actionChosen");
        String providerNo = (String) session.getAttribute("user");
        String groupNo = "";
        /*String wlNewName = (String) wlnForm.get("wlNewName");
        String wlChangedName = (String) wlnForm.get("wlChangedName");
        String selectedWL = (String) wlnForm.get("selectedWL");
        String selectedWL2 = (String) wlnForm.get("selectedWL2");*/
        int successCode = 0;
        request.setAttribute("message", "");
        setMessage("");

        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): edit = " + edit);
        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): actionChosen = " + actionChosen);
        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): selectedWL = " + selectedWL);
        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): selectedWL2 = " + selectedWL2);

        if (edit != null && !edit.equals("")) {

            if (wlNewName == null || wlNewName.length() <= 0) {
                wlNewName = wlChangedName;
            }

            ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
            if (providerPreference.getMyGroupNo() != null) {
                groupNo = providerPreference.getMyGroupNo();
            }


            try {
                if (actionChosen != null && actionChosen.length() > 0 &&
                        providerNo != null && providerNo.length() > 0) {

                    if (actionChosen.equalsIgnoreCase("create")) {
                        if (wlNewName != null && wlNewName.length() > 0) {
                            try {
                                WLWaitingListNameUtil.createWaitingListName(wlNewName, groupNo, providerNo);
                                successCode = 1;
                            } catch (Exception ex1) {
                                if (ex1.getMessage().equals("wlNameExists")) {
                                    setMessage("oscar.waitinglistname.wlNameExists");
                                    //msgs.add(ActionMessages.GLOBAL_MESSAGE,
                                    //		 new ActionMessage("oscar.waitinglistname.wlNameExists"));
                                } else {
                                    setMessage("oscar.waitinglistname.error");
                                    //msgs.add(ActionMessages.GLOBAL_MESSAGE,
                                    //		 new ActionMessage("oscar.waitinglistname.error"));
                                }
                            }
                        }
                    } else if (actionChosen.equalsIgnoreCase("change")) {
                        if (selectedWL != null && selectedWL.length() > 0 &&
                                wlNewName != null && wlNewName.length() > 0) {
                            try {
                                WLWaitingListNameUtil.updateWaitingListName(selectedWL, wlNewName, groupNo, providerNo);
                                successCode = 2;
                            } catch (Exception ex2) {
                                if (ex2.getMessage().equals("wlNameExists")) {
                                    setMessage("oscar.waitinglistname.wlNameExists");
                                    //msgs.add(ActionMessages.GLOBAL_MESSAGE,
                                    //		 new ActionMessage("oscar.waitinglistname.noSuchWL"));
                                } else {
                                    setMessage("oscar.waitinglistname.error");
                                    //msgs.add(ActionMessages.GLOBAL_MESSAGE,
                                    //		 new ActionMessage("oscar.waitinglistname.error"));
                                }
                            }
                        }
                    } else if (actionChosen.equalsIgnoreCase("remove")) {
                        if (selectedWL2 != null && selectedWL2.length() > 0) {
                            MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): selectedWL2 = " + selectedWL2);
                            try {
                                WLWaitingListNameUtil.removeFromWaitingListName(selectedWL2, groupNo);
                                successCode = 3;
                            } catch (Exception ex3) {
                                if (ex3.getMessage().equals("wlNameUsed")) {
                                    setMessage("oscar.waitinglistname.wlNameUsed");
                                    //msgs.add(ActionMessages.GLOBAL_MESSAGE,
                                    //		 new ActionMessage("oscar.waitinglistname.wlNameUsed"));
                                } else {
                                    setMessage("oscar.waitinglistname.error");
                                    //msgs.add(ActionMessages.GLOBAL_MESSAGE,
                                    //		 new ActionMessage("oscar.waitinglistname.error"));
                                }
                            }
                        }
                    } else {
                        successCode = 0;
                    }
                }

            } catch (Exception ex) {
                MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): Exception: " + ex);
                setMessage("oscar.waitinglistname.error");
                //msgs.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("oscar.waitinglistname.error"));
                return "failure";
            }

        }

        if (successCode == 1) {
            setMessage("oscar.waitinglistname.createSuccess");
            //msgs.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("oscar.waitinglistname.createSuccess"));
        } else if (successCode == 2) {
            setMessage("oscar.waitinglistname.editSuccess");
            //msgs.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("oscar.waitinglistname.editSuccess"));
        } else if (successCode == 3) {
            setMessage("oscar.waitinglistname.removeSuccess");
            //msgs.add(ActionMessages.GLOBAL_MESSAGE, new ActionMessage("oscar.waitinglistname.removeSuccess"));
        } else {
            // no idea if this is good or bad, original author didn't document
        }

        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): groupNo = " + groupNo);
        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): providerNo = " + providerNo);
        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): wlNewName = " + wlNewName);


        WLWaitingListNameBeanHandler wlNameHd = new WLWaitingListNameBeanHandler(groupNo, providerNo);

        String today = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");

        session.setAttribute("waitingListNames", wlNameHd.getWaitingListNameList());

        session.setAttribute("today", today);

        MiscUtils.getLogger().debug("WLEditWaitingListName2Action/execute(): getMessage() = " + getMessage());
        request.setAttribute("message", getMessage());
        //saveMessages(request, msgs); //<-- since only one message is needed each time, this function is not needed

        return "continue";
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private String wlNewName;
    private String wlChangedName;
    private String selectedWL;
    private String selectedWL2;

    public String getWlNewName() {
        return wlNewName;
    }

    public void setWlNewName(String wlNewName) {
        this.wlNewName = wlNewName;
    }

    public String getWlChangedName() {
        return wlChangedName;
    }

    public void setWlChangedName(String wlChangedName) {
        this.wlChangedName = wlChangedName;
    }

    public String getSelectedWL() {
        return selectedWL;
    }

    public void setSelectedWL(String selectedWL) {
        this.selectedWL = selectedWL;
    }

    public String getSelectedWL2() {
        return selectedWL2;
    }

    public void setSelectedWL2(String selectedWL2) {
        this.selectedWL2 = selectedWL2;
    }
}
