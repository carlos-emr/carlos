//CHECKSTYLE:OFF
/*
 * DoctorList.java
 *
 * Created on August 27, 2007, 4:23 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.report.data;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.providers.bean.ProviderNameBean;

public class DoctorList {

    public ArrayList<ProviderNameBean> getDoctorNameList() {

        ArrayList<ProviderNameBean> dnl = new ArrayList<ProviderNameBean>();

        ProviderDao dao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> docs = dao.getProvidersByType("doctor");

        for (Provider doc : docs) {
            ProviderNameBean pb = new ProviderNameBean();
            pb.setProviderID(doc.getProviderNo());
            pb.setProviderName(doc.getFullName());
            dnl.add(pb);
        }
        return dnl;
    }
}
