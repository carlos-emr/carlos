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

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.carlos.documentManager.data.LabAttachmentReference;
import io.github.carlos_emr.carlos.documentManager.DocumentAttach;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * @author rjonasz
 */
public class EFormAttachLabs {

    private static EFormDocsDao eformDocsDao = SpringUtils.getBean(EFormDocsDao.class);

    public final static boolean ATTACHED = true;
    public final static boolean UNATTACHED = false;
    private String providerNo;
    private String demoNo;
    private String reqId;
    private ArrayList<String> docs;

    /**
     * Creates a new instance of ConsultationAttachLabs
     */
    public EFormAttachLabs(String provNo, String demo, String req, String[] d) {
        providerNo = provNo;
        demoNo = demo;
        reqId = req;
        docs = new ArrayList<>();
        if (d == null || d.length == 0) return;

        if (CarlosProperties.getInstance().isPropertyActive("consultation_indivica_attachment_enabled")) {
            for (int idx = 0; idx < d.length; ++idx) {
                docs.add(d[idx]);
            }
        } else {
            //if dummy entry skip
            if (!"0".equals(d[0])) {
                for (int idx = 0; idx < d.length; ++idx) {
                    if (d[idx] != null && d[idx].length() > 1 && d[idx].charAt(0) == 'L')
                        docs.add(d[idx].substring(1));
                }
            }
        }
    }

    public void attach(LoggedInInfo loggedInInfo) {
        new DocumentAttach(Integer.valueOf(demoNo), false)
                .attachToEForm(docs.toArray(new String[0]),
                        DocumentType.LAB, providerNo, Integer.valueOf(reqId));
    }

    public static void detachLabConsult(String labNo, String formId) {
        changeLabSelection(null, labNo, Integer.valueOf(formId), false);
    }

    public static void attachLabConsult(String providerNo, String labNo, String formId) {
        changeLabSelection(providerNo, labNo, Integer.valueOf(formId), true);
    }

    private static void changeLabSelection(String providerNo, String selection, int formId, boolean add) {
        new TransactionTemplate(
                SpringUtils.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var owner = SpringUtils.getBean(EFormDataDao.class)
                    .lockForAttachmentSync(formId);
            if (owner == null || owner.getDemographicId() == null) throw new IllegalArgumentException("Missing eForm");
            var reference = LabAttachmentReference.resolve(
                    selection, owner.getDemographicId(), SpringUtils.getBean(PatientLabRoutingDao.class));
            java.util.Set<String> selected = new java.util.LinkedHashSet<>();
            for (EFormDocs row : eformDocsDao.findByFdidIdDocTypeForUpdate(formId, "L")) {
                selected.add(LabAttachmentReference
                        .stored(row.getLabType(), row.getDocumentNo()).key());
            }
            if (add) selected.add(reference.key());
            else selected.remove(reference.key());
            new DocumentAttach(owner.getDemographicId(), false)
                    .attachToEForm(selected.toArray(new String[0]),
                            DocumentType.LAB, providerNo, formId);
        });
    }
}
