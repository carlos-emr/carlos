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

package ca.openosp.openo.billings.ca.on.pageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.utility.Utility;
import ca.openosp.openo.commn.dao.BillingONCHeader1Dao;
import ca.openosp.openo.commn.dao.BillingONItemDao;
import ca.openosp.openo.commn.model.BillingONCHeader1;
import ca.openosp.openo.commn.model.BillingONItem;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarDocumentCreator;

/**
 * @author Eugene Katyukhin
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PatientEndYearStatement2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Logger _logger = MiscUtils.getLogger();
    private static final String RES_SUCCESS = "success";
    private static final String RES_FAILURE = "failure";

    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);


    public String execute() {

        List<PatientEndYearStatementInvoiceBean> result = null;
        PatientEndYearStatementBean summary = new PatientEndYearStatementBean("", "", 0, "", "", "", new Date(), new Date(), "", "");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (request.getParameter("search") != null || request.getParameter("pdf") != null) {

            request.setAttribute("fromDateParam", this.getFromDateParam());
            request.setAttribute("toDateParam", this.getToDateParam());
            Date fromDate = this.getFromDate();
            Date toDate = this.getToDate();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

            if (request.getParameter("search") != null) {
                List<Demographic> demographicList = new ArrayList<Demographic>();
                if (this.getDemographicNoParam() != null && this.getDemographicNoParam().length() > 0) {
                    Demographic d = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), this.getDemographicNoParam());
                    if (d != null) {
                        demographicList.add(d);
                    }
                } else {
                    demographicList = demographicManager.searchDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), this.getLastNameParam() + "," + this.getFirstNameParam());

                }
                if (demographicList == null || demographicList.size() == 0) {
                    _logger.error("Failed to find patient name: " + this.getFirstNameParam() + "," + this.getLastNameParam());
                    addActionError(getText("error.billingReport.invalidPatientName"));
                    return RES_FAILURE;
                }
                if (demographicList.size() > 1) {
                    addActionError(getText("error.billingReport.notSelectivePatientName"));
                    _logger.error("Patient name is not selective enough: " + this.getFirstNameParam() + "," + this.getLastNameParam());
                    return RES_FAILURE;
                }
                Demographic demographic = demographicList.get(0);
                summary.setPatientNo(demographic.getDemographicNo().toString());
                summary.setPatientName(demographic.getFormattedName());
                summary.setHin(demographic.getHin());
                summary.setAddress(demographic.getAddress() + " " + demographic.getCity() + " " + demographic.getProvince());
                summary.setPhone(demographic.getPhone() + " " + demographic.getPhone2());
                request.setAttribute("summary", summary);

                double totalInvoiced = 0;
                double totalPaid = 0;
                int invoiceCount = 0;

                BillingONCHeader1Dao hDao = SpringUtils.getBean(BillingONCHeader1Dao.class);
                BillingONItemDao iDao = SpringUtils.getBean(BillingONItemDao.class);
                try {
                    for (Object[] o : hDao.findBillingsAndDemographicsByDemoIdAndDates(demographic.getDemographicNo(), "PAT", fromDate, toDate)) {
                        BillingONCHeader1 bch = (BillingONCHeader1) o[0];
                        Demographic d = (Demographic) o[1];

                        result = new ArrayList<PatientEndYearStatementInvoiceBean>();

                        double paid = bch.getPaid().doubleValue();
                        double invoiced = bch.getTotal().doubleValue();
                        PatientEndYearStatementInvoiceBean bean = new PatientEndYearStatementInvoiceBean(bch.getId(), bch.getBillingDate(), String.valueOf(invoiced), String.valueOf(paid));

                        List<PatientEndYearStatementServiceBean> services = null;
                        try {
                            services = new ArrayList<PatientEndYearStatementServiceBean>();
                            for (BillingONItem bi : iDao.findByCh1Id(bch.getId())) {
                                String fee = Utility.toCurrency(bi.getFee());
                                PatientEndYearStatementServiceBean serviceBean =
                                        new PatientEndYearStatementServiceBean(bi.getServiceCode(), fee);
                                services.add(serviceBean);
                            }
                        } catch (Exception e) {
                            _logger.error("error", e);
                            addActionError(getText("errors.billing.ca.on.database", "SQL error"));
                            return RES_FAILURE;
                        }

                        bean.setServices(services);
                        result.add(bean);
                        totalInvoiced += invoiced;
                        totalPaid += paid;
                        invoiceCount += 1;
                        request.setAttribute("result", result);
                    }

                    summary.setInvoiced(Utility.toCurrency(totalInvoiced));
                    summary.setPaid(Utility.toCurrency(totalPaid));
                    summary.setCount(Integer.toString(invoiceCount));
                    summary.setFromDate(fromDate);
                    summary.setToDate(toDate);
                    request.getSession().setAttribute("summary", summary);
                } catch (Exception e) {
                    _logger.error("error", e);

                    addActionError(getText("errors.billing.ca.on.database", "SQL error"));
                    return RES_FAILURE;
                }

            } else if (request.getParameter("pdf") != null) {
                summary = (PatientEndYearStatementBean) request.getSession().getAttribute("summary");
                OscarDocumentCreator osc = new OscarDocumentCreator();
                String docFmt = "pdf";

                HashMap<String, Object> reportParams = new HashMap<String, Object>();
                reportParams.put("patientId", summary.getPatientNo());
                reportParams.put("patientName", summary.getPatientName());
                reportParams.put("hin", summary.getHin());
                reportParams.put("address", summary.getAddress());
                reportParams.put("phone", summary.getPhone());
                reportParams.put("fromDate", this.getFromDateParam());
                reportParams.put("toDate", this.getToDateParam());
                reportParams.put("invoiceCount", summary.getCount());
                reportParams.put("totalInvoiced", summary.getInvoiced());
                reportParams.put("totalPaid", summary.getPaid());
                reportParams.put("fromDate", this.getFromDateParam());
                reportParams.put("toDate", this.getToDateParam());
                reportParams.put("SUBREPORT_DIR", "/oscar/oscarBilling/ca/on/reports/");

                ServletOutputStream outputStream = null;
                try {
                    outputStream = response.getOutputStream();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                //open corresponding Jasper Report Definition
                InputStream reportInstream = osc.getDocumentStream("/oscar/oscarBilling/ca/on/reports/" + "end_year_statement_report.jrxml");
                try {
                    //COnfigure Reponse Header
                    cfgHeader(response, "end_year_statement_report.pdf", docFmt);
                    //Fill document with report parameter data
                    Connection dbConn = null;
                    try {
                        dbConn = DbConnectionFilter.getThreadLocalDbConnection();
                    } catch (SQLException ex) {
                        addActionError(getText("errors.billing.ca.on.database", "Database access error"));
                        _logger.error("Can't get db connection", ex);
                        return RES_FAILURE;
                    }
                    if (dbConn != null) {
                        osc.fillDocumentStream(reportParams, outputStream, docFmt, reportInstream, dbConn);
                    }
                    return null;
                } finally {
                    IOUtils.closeQuietly(reportInstream);
                }
            }
        } else if (request.getParameter("demosearch") != null) {
            request.getSession().setAttribute("summary", null);

            List<Demographic> demographicList = new ArrayList<Demographic>();
            if (request.getParameter("demographic_no") != null && request.getParameter("demographic_no").length() > 0) {
                Demographic d = demographicManager.getDemographic(loggedInInfo, request.getParameter("demographic_no"));
                if (d != null) {
                    demographicList.add(d);
                }
            } else {
                demographicList = demographicManager.searchDemographic(loggedInInfo, this.getLastNameParam() + "," + this.getFirstNameParam());

            }

            if (demographicList == null || demographicList.size() == 0) {
                addActionError(getText("error.billingReport.invalidPatientName"));
                _logger.error("Failed to find patient name: " + this.getFirstNameParam() + "," + this.getLastNameParam());
                return RES_FAILURE;
            }
            if (demographicList.size() > 1) {
                addActionError(getText("error.billingReport.notSelectivePatientName"));
                _logger.error("Patient name is not selective enough: " + this.getFirstNameParam() + "," + this.getLastNameParam());
                return RES_FAILURE;
            }
            Demographic demographic = demographicList.get(0);
            summary.setPatientNo(demographic.getChartNo());
            summary.setPatientName(demographic.getFormattedName());
            summary.setHin(demographic.getHin());
            summary.setAddress(demographic.getAddress() + " " + demographic.getCity() + " " + demographic.getProvince());
            summary.setPhone(demographic.getPhone() + " " + demographic.getPhone2());
            request.setAttribute("summary", summary);
        } else {
            request.getSession().setAttribute("summary", null);
        }
        return RES_SUCCESS;
    }

    /**
     * Configures the response header for upload of specified mime-type
     *
     * @param response HttpServletResponse
     * @param docName  String
     * @param docType  String
     */
    public void cfgHeader(HttpServletResponse response, String docName,
                          String docType) {
        String mimeType = "application/octet-stream";
        if (docType.equals("pdf")) {
            mimeType = "application/pdf";
        } else if (docType.equals("csv")) {
            mimeType = "application/csv";
        }
        response.setContentType(mimeType);
        response.setHeader("Content-Disposition",
                "attachment;filename=" + docName + "." + docType);
    }

    private String firstNameParam;
    private String lastNameParam;
    private String fromDateParam;
    private String toDateParam;
    private String demographicNoParam;

    public String getFirstNameParam() {
        return firstNameParam;
    }

    public void setFirstNameParam(String firstNameParam) {
        this.firstNameParam = firstNameParam;
    }

    public String getLastNameParam() {
        return lastNameParam;
    }

    public void setLastNameParam(String lastNameParam) {
        this.lastNameParam = lastNameParam;
    }

    public String getFromDateParam() {
        return fromDateParam;
    }

    public void setFromDateParam(String fromDateParam) {
        this.fromDateParam = fromDateParam;
    }

    public String getDemographicNoParam() {
        return demographicNoParam;
    }

    public void setDemographicNoParam(String demographicNoParam) {
        this.demographicNoParam = demographicNoParam;
    }

    public Date getFromDate() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date res = null;
        try {
            if (fromDateParam != null && fromDateParam.length() > 0) res = df.parse(fromDateParam);
        } catch (ParseException ex) {
            //logger.error("Can't parse date: " + fromDateParam);
            return null;
        }
        return res;
    }

    public String getToDateParam() {
        return toDateParam;
    }

    public void setToDateParam(String toDate) {
        this.toDateParam = toDate;
    }

    public Date getToDate() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date res = null;
        try {
            if (toDateParam != null && toDateParam.length() > 0) res = df.parse(toDateParam);
        } catch (ParseException ex) {
            return null;
        }
        return res;
    }
}
