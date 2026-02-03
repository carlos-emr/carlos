/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Deprecated
 * <p>
 * To be removed in favor of a generic class that manages and saves all types of
 * attachments such as attachments to eForms, Forms or Documents.
 */
@Deprecated
public class ConsultationAttach {

    private ConsultDocsDao consultDocsDao = SpringUtils.getBean(ConsultDocsDao.class);
    private ConsultationRequestDao consultationRequestDao = SpringUtils.getBean(ConsultationRequestDao.class);

    private String reqId; //consultation id
    private String demoNo;
    private String providerNo;
    private ArrayList<String> docs; //document ids

    public ConsultationAttach(String prov, String demo, String req, String[] d) {
        providerNo = prov;
        demoNo = demo;
        reqId = req;
        docs = new ArrayList<>(Arrays.asList(d));
    }

    protected void attach(LoggedInInfo loggedInInfo, String docType) {

        //first we get a list of currently attached labs
        List<ConsultDocs> oldlist = consultDocsDao.findByRequestIdDocType(Integer.parseInt(getReqId()), docType);
        List<String> newlist = new ArrayList<>();
        List<ConsultDocs> keeplist = new ArrayList<>();
        List<String> currentList = getDocs();

        boolean alreadyAttached;
        //add new documents to list and get ids of docs to keep attached
        for (int i = 0; i < currentList.size(); ++i) {
            alreadyAttached = false;
            for (int j = 0; j < oldlist.size(); ++j) {
                if (((oldlist.get(j)).getDocumentNo() + "").equals(currentList.get(i))) {
                    alreadyAttached = true;
                    keeplist.add(oldlist.get(j));
                    break;
                }
            }
            if (!alreadyAttached) {
                newlist.add(currentList.get(i));
            }
        }

        //now compare what we need to keep with what we have and remove association
        for (ConsultDocs old : oldlist) {
            if (keeplist.contains(old)) {
                continue;
            }
            detach(loggedInInfo, old.getDocumentNo() + "", docType);
        }

        attach(loggedInInfo, newlist, docType);

    }

    protected void attach(LoggedInInfo loggedInInfo, String docId, String docType) {
        ConsultDocs consultDoc = new ConsultDocs();
        consultDoc.setRequestId(Integer.parseInt(getReqId()));
        consultDoc.setDocumentNo(Integer.parseInt(docId));
        consultDoc.setDocType(docType);
        consultDoc.setAttachDate(new Date());
        consultDoc.setProviderNo(getProviderNo());
        consultDocsDao.persist(consultDoc);
    }

    protected void attach(LoggedInInfo loggedInInfo, List<String> docIdList, String docType) {
        if (docIdList != null) {
            for (String docId : docIdList) {
                attach(loggedInInfo, docId, docType);
            }
        }
    }

    protected void detach(LoggedInInfo loggedInInfo, String docId, String docType) {
        List<ConsultDocs> consultDocs = consultDocsDao.findByRequestIdDocNoDocType(Integer.parseInt(getReqId()), Integer.parseInt(docId), docType);
        if (consultDocs != null) {
            for (ConsultDocs consultDoc : consultDocs) {
                consultDoc.setDeleted("Y");
                consultDocsDao.merge(consultDoc);
            }
        }
    }

    protected void detach(LoggedInInfo loggedInInfo, List<String> docIdList, String docType) {
        if (docIdList != null) {
            for (String docId : docIdList) {
                detach(loggedInInfo, docId, docType);
            }
        }
    }

    protected String getDemoNo() {
        if (this.demoNo == null) {
            demoNo = "";
        }

        if (demoNo.isEmpty()) {
            ConsultationRequest consultationRequest = consultationRequestDao.find(ConversionUtils.fromIntString(reqId));
            if (consultationRequest != null) {
                demoNo = consultationRequest.getId() + "";
            }
        }
        return demoNo;
    }

    public String getReqId() {
        return reqId;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public ArrayList<String> getDocs() {
        return docs;
    }
}
