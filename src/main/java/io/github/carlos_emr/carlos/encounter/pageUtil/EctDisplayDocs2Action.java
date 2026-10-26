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


package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.util.plugin.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao.DocumentType;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.EDocUtil.EDocSort;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class EctDisplayDocs2Action extends EctDisplayAction {
    private static Logger logger = MiscUtils.getLogger();

    private static final String cmd = "docs";

    public boolean getInfo(EctSessionBean bean, HttpServletRequest request, NavBarDisplayDAO Dao) {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            return true; // documents link won't show up on new CME screen.
        } else {

            String omitTypeStr = request.getParameter("omit");
            omitTypeStr += ("," + DocumentType.ECONSULT.getName());
            String[] omitTypes = new String[0];
            if (omitTypeStr != null) {
                omitTypes = omitTypeStr.split(",");
            }
            // add for inbox manager
            boolean inboxflag = IsPropertiesOn.propertiesOn("inboxmnger");
            // set lefthand module heading and link
            String winName = "docs" + bean.demographicNo;
            String leftPath;

            Dao.setLeftHeading(getText("encounter.Index.msgDocuments"));
            if (inboxflag) {
                leftPath = request.getContextPath() + "/mod/docmgmtComp/DocList?method=list&&demographic_no=" + bean.demographicNo;
                Dao.setLeftPopup(600, 1024, winName, leftPath);
                Dao.setLeftHeading(getText("encounter.Index.inboxManager"));
            } else {
                leftPath = request.getContextPath() + "/documentManager/ViewDocumentReport?"
                        + "function=demographic&doctype=lab&functionid=" + SafeEncode.forUriComponent(bean.demographicNo);
                Dao.setLeftPopup(500, 1115, winName, leftPath);
            }

            // set the right hand heading link to call addDocument in index jsp
            winName = "addDoc" + bean.demographicNo;

            if (inboxflag) {
                Dao.setRightPopup(300, 600, winName, request.getContextPath() + "/mod/docmgmtComp/FileUpload?method=newupload&demographic_no=" + bean.demographicNo);
            } else {
                Dao.setRightPopup(500, 1115, winName, request.getContextPath() + "/documentManager/ViewDocumentReport?"
                        + "function=demographic&doctype=lab&functionid=" + SafeEncode.forUriComponent(bean.demographicNo)
                        + "&mode=add&parentAjaxId=" + SafeEncode.forUriComponent(cmd));
            }
            Dao.setRightHeadingID(cmd); // no menu so set div id to unique id for this action

            ArrayList<EDoc> docList = EDocUtil.listDocs(loggedInInfo, "demographic", bean.demographicNo, null, EDocUtil.PRIVATE, EDocSort.OBSERVATIONDATE, "active");
            UserProperty grouping = SpringUtils.getBean(UserPropertyDAO.class).getProp(
                    loggedInInfo.getLoggedInProviderNo(), UserProperty.ECHART_GROUP_DOCUMENTS_BY_TYPE);
            boolean fileByType = grouping != null && "true".equals(
                    org.apache.commons.lang3.StringUtils.trimToEmpty(grouping.getValue()));
            Dao.setFiledInFolders(fileByType);
            String untypedFolder = fileByType ? getText("encounter.LeftNavBar.docs.otherFolder") : "";
            String serviceDateStr = "";
            String key;
            String title;
            int hash;
            String BGCOLOUR = request.getParameter("hC");
            String url;
            Date date;

            // sort complete list by date descending
            sortByDate(docList);

            boolean isURLjavaScript;
            for (int i = 0; i < docList.size(); i++) {
                isURLjavaScript = false;
                EDoc curDoc = docList.get(i);
                String dispFilename = org.apache.commons.lang3.StringUtils.trimToEmpty(curDoc.getFileName());
                String dispStatus = String.valueOf(curDoc.getStatus());

                boolean skip = false;
                for (int x = 0; x < omitTypes.length; x++) {
                    if (omitTypes[x].equals(curDoc.getType())) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;

                if (dispStatus.equals("A")) dispStatus = "active";
                else if (dispStatus.equals("H")) dispStatus = "html";

                String dispDocNo = curDoc.getDocId();
                title = StringUtils.maxLenString(curDoc.getDescription(), MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);
                title = SafeEncode.forHtml(title);

                if (EDocUtil.getDocUrgentFlag(dispDocNo))
                    title = StringUtils.maxLenString("!" + curDoc.getDescription(), MAX_LEN_TITLE, CROP_LEN_TITLE, ELLIPSES);

                NavBarDisplayDAO.Item item = NavBarDisplayDAO.Item();
                date = observationDate(curDoc.getObservationDate());
                serviceDateStr = date == null ? "" : DateUtils.formatDate(date, request.getLocale());

                String user = (String) request.getSession().getAttribute("user");
                item.setDate(date);
                hash = Math.abs(winName.hashCode());

                if (inboxflag) {
                    String path = IsPropertiesOn.getProperty("DOCUMENT_DIR");
 		    url = "popupPage(700,800,'" + hash + "', '" + request.getContextPath() + "/mod/docmgmtComp/FillARForm?method=showInboxDocDetails&path=" + SafeEncode.forJavaScript(path) + "&demoNo=" + SafeEncode.forJavaScript(bean.demographicNo) + "&name=" + SafeEncode.forJavaScript(dispFilename) + "'); return false;";
                    isURLjavaScript = true;
                } else if (curDoc.isPDF()) {
                    url = "popupPage(window.screen.width,window.screen.height,'" + hash + "','" + request.getContextPath() + "/documentManager/ViewShowDocument?inWindow=true&segmentID=" + SafeEncode.forJavaScript(dispDocNo) + "'); return false;";
                    isURLjavaScript = true;
                } else {
                    url = "popupPage(700,800,'" + hash + "', '" + request.getContextPath() + "/documentManager/ManageDocument?method=display&doc_no=" + SafeEncode.forJavaScript(dispDocNo) + "&providerNo=" + SafeEncode.forJavaScript(user) + "'); return false;";
                }

                item.setLinkTitle(title + serviceDateStr);
                item.setTitle(title);
                key = StringUtils.maxLenString(curDoc.getDescription(), MAX_LEN_KEY, CROP_LEN_KEY, ELLIPSES) + "(" + serviceDateStr + ")";
                if (inboxflag) {
                    if (!EDocUtil.getDocReviewFlag(dispDocNo)) item.setColour("FF0000");
                }
                Dao.addAutoCompleteItem(key, url, BGCOLOUR);
                item.setURL(url);
                item.setURLJavaScript(true);

                if ("true".equals(curDoc.getAbnormal())) {
                    item.setColour("red");
                }

                if (fileByType) {
                    item.setFolder(folderFor(curDoc.getType(), untypedFolder));
                }
                Dao.addItem(item);

            }
            return true;
        }
    }

    /** Documents without a type share the translated catch-all folder. */
    static String folderFor(String documentType, String untypedFolder) {
        String type = org.apache.commons.lang3.StringUtils.trimToEmpty(documentType);
        return type.isEmpty() ? untypedFolder : type;
    }

    static void sortByDate(List<EDoc> list) {
        list.sort(Comparator.comparing((EDoc doc) -> observationDate(doc.getObservationDate()),
                Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private static Date observationDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            format.setLenient(false);
            return format.parse(value);
        } catch (ParseException invalidDate) {
            return null;
        }
    }


    public String getCmd() {
        return cmd;
    }
}
