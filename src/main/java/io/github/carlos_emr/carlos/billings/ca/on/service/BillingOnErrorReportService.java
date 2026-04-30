/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingErrorReportDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.model.BillingONEAReport;

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
// NOTE: this service is read+write — methods around lines 111/141/162 call
// remove/persist/merge. Class-level MUST NOT be readOnly=true; Hibernate
// would skip the flush on those writes (or throw on commit). Same fix as
// BillingOnRaService.
@org.springframework.transaction.annotation.Transactional
public class BillingOnErrorReportService {

    private final BillingONEAReportDao billingONEARReportDao;

    /** Test-friendly constructor — package-private, takes DAO mocks directly. */
    BillingOnErrorReportService(BillingONEAReportDao billingONEARReportDao) {
        this.billingONEARReportDao = billingONEARReportDao;
    }

    public List<BillingErrorReportDto> getErrorRecords(BillingProviderDto val, String fromDate, String toDate, String filename) {
        List<BillingErrorReportDto> retval = new ArrayList<BillingErrorReportDto>();
        for (BillingONEAReport r : billingONEARReportDao.findByMagic(val.getOhipNo(), val.getBillingGroupNo(), val.getSpecialtyCode(), ConversionUtils.fromDateString(fromDate), ConversionUtils.fromDateString(toDate), filename)) {
            toReportData(retval, r);
        }
        return retval;
    }

    private void toReportData(List<BillingErrorReportDto> retval, BillingONEAReport r) {
        BillingErrorReportDto obj = null;
        obj = new BillingErrorReportDto();
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

    public List<BillingErrorReportDto> getErrorRecords(List<BillingProviderDto> list, String fromDate, String toDate, String filename) {
        List<BillingErrorReportDto> retval = new ArrayList<BillingErrorReportDto>();
        if (list == null) {
            return retval;
        }

        for (BillingONEAReport r : billingONEARReportDao.findByMagic(list, ConversionUtils.fromDateString(fromDate), ConversionUtils.fromDateString(toDate), filename)) {
            toReportData(retval, r);
        }

        return retval;
    }

    public boolean deleteErrorReport(BillingErrorReportDto val) {
        List<BillingONEAReport> bs = billingONEARReportDao.findByProviderOhipNoAndGroupNoAndSpecialtyAndProcessDateAndBillingNo(val.getProviderohip_no(), val.getGroup_no(), val.getSpecialty(), ConversionUtils.fromDateString(val.getProcess_date()), Integer.parseInt(val.getBilling_no()));
        for (BillingONEAReport b : bs) {
            billingONEARReportDao.remove(b.getId());
        }
        return true;
    }

    public int addErrorReportRecord(BillingErrorReportDto val) {
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

    /**
     * Toggles the {@code status} flag on a single MOH error-report record.
     *
     * @param id  primary key of the {@code billing_on_ea_report} row
     * @param val the new status — first character is persisted
     * @return {@code true} if a row was updated, {@code false} if no row matched
     *         {@code id} (caller must surface a 404-class error so the AJAX UI
     *         doesn't lie to the provider about acknowledgement)
     */
    public boolean updateErrorReportStatus(String id, String val) {
        BillingONEAReport b = billingONEARReportDao.find(Integer.valueOf(id));
        if (b == null) {
            return false;
        }
        b.setStatus(val.toCharArray()[0]);
        billingONEARReportDao.merge(b);
        return true;
    }

}
