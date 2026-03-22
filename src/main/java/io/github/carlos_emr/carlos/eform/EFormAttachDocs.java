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

package io.github.carlos_emr.carlos.eform;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Manages the attachment and detachment of clinical documents (eDocs) to eForm
 * data records. Reconciles currently attached documents with newly selected ones
 * using {@link EDocUtil}.
 *
 * @see EFormAttachLabs
 * @see EFormAttachEForms
 * @see EFormAttachHRMReports
 * @since 2006-05-25
 */
public class EFormAttachDocs {

    private String reqId; //consultation id
    private String demoNo;
    private String providerNo;
    private ArrayList<String> docs; //document ids

    /**
     * Constructs an instance with the given eForm data ID and empty document list.
     *
     * @param req String the eForm data ID
     */
    public EFormAttachDocs(String req) {
        reqId = req;
        demoNo = "";
        docs = new ArrayList<String>();
    }

    /**
     * Constructs an instance with the given provider, demographic, eForm data ID,
     * and array of document identifiers. When the {@code consultation_indivica_attachment_enabled}
     * property is inactive, only IDs prefixed with 'D' are extracted.
     *
     * @param prov String the provider number performing the attachment
     * @param demo String the demographic number of the patient
     * @param req String the eForm data ID to attach documents to
     * @param d String[] array of document identifiers
     */
    public EFormAttachDocs(String prov, String demo, String req, String[] d) {
        providerNo = prov;
        demoNo = demo;
        reqId = req;
        docs = new ArrayList<String>(d.length);

        if (CarlosProperties.getInstance().isPropertyActive("consultation_indivica_attachment_enabled")) {
            for (int idx = 0; idx < d.length; ++idx) {
                docs.add(d[idx]);
            }
        } else {
            //if dummy entry skip

            if (!d[0].equals("0")) {
                for (int idx = 0; idx < d.length; ++idx) {
                    if (d[idx].charAt(0) == 'D') docs.add(d[idx].substring(1));
                }
            }
        }
    }

    /**
     * Returns the demographic number for this attachment context, looking it up
     * from the eForm data record if not already set.
     *
     * @return String the demographic number, or an empty string if not found
     */
    public String getDemoNo() {
        String demo;
        if (!demoNo.equals("")) demo = demoNo;
        else {
            EFormDataDao dao = SpringUtils.getBean(EFormDataDao.class);
            EFormData req = dao.find(ConversionUtils.fromIntString(reqId));
            if (req != null) {
                demo = req.getId().toString();
                demoNo = demo;
            } else {
                demo = "";
            }
        }

        return demo;
    }

    /**
     * Reconciles document attachments by comparing currently attached documents
     * with the selected list, detaching removed ones and attaching new ones.
     *
     * @param loggedInInfo LoggedInInfo the current session context
     */
    public void attach(LoggedInInfo loggedInInfo) {

        //first we get a list of currently attached docs
        ArrayList<EDoc> oldlist = EDocUtil.listDocsAttachedToEForm(loggedInInfo, demoNo, reqId, EDocUtil.ATTACHED);
        ArrayList<String> newlist = new ArrayList<String>();
        ArrayList<EDoc> keeplist = new ArrayList<EDoc>();
        boolean alreadyAttached;
        //add new documents to list and get ids of docs to keep attached
        for (int i = 0; i < docs.size(); ++i) {
            alreadyAttached = false;
            for (int j = 0; j < oldlist.size(); ++j) {
                if ((oldlist.get(j)).getDocId().equals(docs.get(i))) {
                    alreadyAttached = true;
                    keeplist.add(oldlist.get(j));
                    break;
                }
            }
            if (!alreadyAttached) newlist.add(docs.get(i));
        }

        //now compare what we need to keep with what we have and remove association
        for (int i = 0; i < oldlist.size(); ++i) {
            if (keeplist.contains(oldlist.get(i))) continue;

            EDocUtil.detachDocEForm((oldlist.get(i)).getDocId(), reqId);
        }

        //now we can add association to new list
        for (int i = 0; i < newlist.size(); ++i)
            EDocUtil.attachDocEForm(providerNo, newlist.get(i), reqId);

    } //end attach
}
