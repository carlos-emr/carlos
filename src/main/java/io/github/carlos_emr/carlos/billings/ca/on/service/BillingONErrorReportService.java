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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingErrorRepData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingProviderData;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Side-effect service for the {@code billing_on_eareport} table — the
 * MOH error-report ingestion + display surface. Reads
 * ({@link #getErrorRecords}) load rows for the status page; mutations
 * ({@link #addErrorReportRecord}, {@link #deleteErrorReport},
 * {@link #updateErrorReportStatus}) accept and ack remediation.
 *
 * <p>Replaces the legacy {@code JdbcBillingErrorRepImpl} shim that lived
 * in {@code data/}.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingONErrorReportService {

    private BillingONEAReportDao billingONEARReportDao = (BillingONEAReportDao) SpringUtils.getBean(BillingONEAReportDao.class);

    public List<BillingErrorRepData> getErrorRecords(BillingProviderData val, String fromDate, String toDate, String filename) {
        List<BillingErrorRepData> retval = new ArrayList<BillingErrorRepData>();
        BillingONEAReportDao dao = SpringUtils.getBean(BillingONEAReportDao.class);
        for (BillingONEAReport r : dao.findByMagic(val.getOhipNo(), val.getBillingGroupNo(), val.getSpecialtyCode(), ConversionUtils.fromDateString(fromDate), ConversionUtils.fromDateString(toDate), filename)) {
            toReportData(retval, r);
        }
        return retval;
    }

    private void toReportData(List<BillingErrorRepData> retval, BillingONEAReport r) {
        BillingErrorRepData obj = null;
        obj = new BillingErrorRepData();
        obj.setId("" + r.getId());
        obj.setBilling_no("" + r.getBillingNo());
        obj.setProviderohip_no(r.getProviderOHIPNo());
        obj.setGroup_no(r.getGroupNo());
        obj.setSpecialty(r.getSpecialty());
        obj.setProcess_date(ConversionUtils.toDateString(r.getProcessDate()));
        obj.setHin(r.getHin());
        obj.setVer(r.getVersion());
        obj.setDob(ConversionUtils.toDateString(r.getDob()));
        obj.setRef_no(r.getRefNo());
        obj.setFacility(r.getFacility());
        obj.setAdmitted_date(ConversionUtils.toDateString(r.getAdmittedDate()));
        obj.setClaim_error(r.getClaimError());
        obj.setCode(r.getCode());
        obj.setFee(r.getFee());
        obj.setUnit(r.getUnit());
        obj.setCode_date(ConversionUtils.toDateString(r.getCodeDate()));
        obj.setDx(r.getDx());
        obj.setExp(r.getExp());
        obj.setCode_error(r.getCodeError());
        obj.setReport_name(r.getReportName());
        obj.setStatus("" + r.getStatus());
        obj.setComment(r.getComment());
        retval.add(obj);
    }

    public List<BillingErrorRepData> getErrorRecords(List<BillingProviderData> list, String fromDate, String toDate, String filename) {
        List<BillingErrorRepData> retval = new ArrayList<BillingErrorRepData>();
        if (list == null) {
            return retval;
        }

        BillingONEAReportDao dao = SpringUtils.getBean(BillingONEAReportDao.class);
        for (BillingONEAReport r : dao.findByMagic(list, ConversionUtils.fromDateString(fromDate), ConversionUtils.fromDateString(toDate), filename)) {
            toReportData(retval, r);
        }

        return retval;
    }

    public boolean deleteErrorReport(BillingErrorRepData val) {
        List<BillingONEAReport> bs = billingONEARReportDao.findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDateAndBillingNo(val.getProviderohip_no(), val.getGroup_no(), val.getSpecialty(), ConversionUtils.fromDateString(val.getProcess_date()), Integer.parseInt(val.getBilling_no()));
        for (BillingONEAReport b : bs) {
            billingONEARReportDao.remove(b.getId());
        }
        return true;
    }

    public int addErrorReportRecord(BillingErrorRepData val) {
        BillingONEAReport b = new BillingONEAReport();
        b.setProviderOHIPNo(val.getProviderohip_no());
        b.setGroupNo(val.getGroup_no());
        b.setSpecialty(val.getSpecialty());
        b.setProcessDate(ConversionUtils.fromDateString(val.getProcess_date(), "yyyyMMdd"));
        b.setHin(val.getHin());
        b.setVersion(val.getVer());
        b.setDob(ConversionUtils.fromDateString(val.getDob()));
        b.setBillingNo(Integer.parseInt(val.getBilling_no()));
        b.setRefNo(val.getRef_no());
        b.setFacility(val.getFacility());
        b.setAdmittedDate(ConversionUtils.fromDateString(val.getAdmitted_date(), "yyyyMMdd"));
        b.setClaimError(val.getClaim_error());
        b.setCode(val.getCode());
        b.setFee(val.getFee());
        b.setUnit(val.getUnit());
        b.setCodeDate(ConversionUtils.fromDateString(val.getCode_date(), "yyyyMMdd"));
        b.setDx(val.getDx());
        b.setExp(val.getExp());
        b.setCodeError(val.getCode_error());
        b.setReportName(val.getReport_name());
        b.setStatus(val.getStatus().toCharArray()[0]);
        b.setComment(val.getComment());

        billingONEARReportDao.persist(b);

        return b.getId();

    }

    public boolean updateErrorReportStatus(String id, String val) {
        BillingONEAReport b = billingONEARReportDao.find(Integer.valueOf(id));
        if (b != null) {
            b.setStatus(val.toCharArray()[0]);
            billingONEARReportDao.merge(b);
        }
        return true;
    }

}
