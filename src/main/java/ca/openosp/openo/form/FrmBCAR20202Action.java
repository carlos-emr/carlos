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
package ca.openosp.openo.form;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.PrintResourceLogDao;
import ca.openosp.openo.commn.model.PrintResourceLog;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.commn.dao.forms.FormBCAR2020Dao;
import ca.openosp.openo.commn.dao.forms.FormBCAR2020DataDao;
import ca.openosp.openo.commn.dao.forms.FormBCAR2020TextDao;
import ca.openosp.openo.form.model.FormBCAR2020;
import ca.openosp.openo.form.model.FormBCAR2020Data;
import ca.openosp.openo.form.model.FormBCAR2020Text;
import ca.openosp.openo.form.util.LanguageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class FrmBCAR20202Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private FormBCAR2020Dao bcar2020Dao = SpringUtils.getBean(FormBCAR2020Dao.class);
    private FormBCAR2020DataDao bcar2020DataDao = SpringUtils.getBean(FormBCAR2020DataDao.class);
    private FormBCAR2020TextDao bcar2020TextDao = SpringUtils.getBean(FormBCAR2020TextDao.class);

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final Logger logger = MiscUtils.getLogger();
    private final String RECORD_NAME = "BCAR2020";
    private SimpleDateFormat formDateFormat = new SimpleDateFormat("yyyy/MM/dd");

    public FrmBCAR20202Action() {
    }

    public String execute() throws Exception {
        if ("saveAndExit".equals(request.getParameter("method"))) {
            return saveAndExit();
        }
        if ("save".equals(request.getParameter("method"))) {
            return save();
        }if ("print".equals(request.getParameter("method"))) {
            return print();
        }
        return "pg1";
    }

    public String saveAndExit() throws Exception {
        save();
        return "exit";
    }

    public String save() throws Exception {
        FrmBCAR2020Record bcar2020Record = (FrmBCAR2020Record) (new FrmRecordFactory()).factory(RECORD_NAME);

        int formId = StringUtils.isNotEmpty(request.getParameter("formId")) ? Integer.parseInt(request.getParameter("formId")) : 0;
        int demographicNo = StringUtils.isNotEmpty(request.getParameter("demographicNo")) ? Integer.parseInt(request.getParameter("demographicNo")) : 0;
        String providerNo = StringUtils.isNotEmpty(request.getParameter("provNo")) ? request.getParameter("provNo") : "0";
        Integer page = StringUtils.isNotEmpty(request.getParameter("pageNo")) ? Integer.parseInt(request.getParameter("pageNo")) : 1;
        Integer forwardTo = StringUtils.isNotEmpty(request.getParameter("forwardTo")) ? Integer.parseInt(request.getParameter("forwardTo")) : page;

        List<FormBCAR2020Data> currentRecords = bcar2020DataDao.findFields(formId);
        List<FormBCAR2020Text> currentTextRecords = bcar2020TextDao.findFields(formId);
        List<FormBCAR2020Data> addRecords = new ArrayList<>();
        List<FormBCAR2020Text> addTextRecords = new ArrayList<>();

        Map<String, String> currentValues = getMappedRecords(currentRecords);
        currentValues.putAll(getMappedTextRecords(currentTextRecords));

        List<FormBCAR2020Data> persistRecords = new ArrayList<>();
        List<FormBCAR2020Text> persistTextRecords = new ArrayList<>();
        Set<String> keys = request.getParameterMap().keySet();

        FormBCAR2020 formBCAR2020 = new FormBCAR2020();
        formBCAR2020.setDemographicNo(demographicNo);
        formBCAR2020.setProviderNo(providerNo);
        formBCAR2020.setFormCreated(new Date());

        bcar2020Dao.persist(formBCAR2020);
        formId = formBCAR2020.getId();

        // copy all current values to new form
        for (FormBCAR2020Data rec : currentRecords) {
            if ((rec.getPageNo() == null || !rec.getPageNo().equals(page)) || StringUtils.trimToNull(request.getParameter(rec.getField())) != null) {
                rec.setFormId(formId);
                addRecords.add(rec);
            }
        }
        for (FormBCAR2020Text rec : currentTextRecords) {
            if ((rec.getPageNo() == null || !rec.getPageNo().equals(page)) || StringUtils.trimToNull(request.getParameter(rec.getField())) != null) {
                rec.setFormId(formId);
                addTextRecords.add(rec);
            }
        }

        for (String key : keys) {
            if (key.contains("mt_")) {
                // Multi text field type (this gets stored in a different table due to the size requirements
                String value = StringUtils.trimToNull(request.getParameter(key));
                // If the field is a global field (exists on all pages), set the page number to 0
                Integer setPage = bcar2020Record.isGlobalField(key) ? 0 : page;

                if (currentValues.get(setPage + "_" + key) != null) {
                    FormBCAR2020Text record = getTextRecordByKey(addTextRecords, key);

                    if (record != null) {
                        if (value != null && !value.equals(currentValues.get(setPage + "_" + key))) {
                            record.setValue(value);
                        } else if (value == null) {
                            record.setValue("");
                        }
                    }

                } else if (currentValues.get(setPage + "_" + key) == null && value != null) {
                    addTextRecords.add(new FormBCAR2020Text(formId, providerNo, setPage, key, value));
                }
            } else if (key.contains("_")) {
                String value = StringUtils.trimToNull(request.getParameter(key));
                // If the field is a global field (exists on all pages), set the page number to 0
                Integer setPage = bcar2020Record.isGlobalField(key) ? 0 : page;

                if (currentValues.get(setPage + "_" + key) != null) {
                    FormBCAR2020Data record = getRecordByKey(addRecords, key);

                    if (record != null) {
                        if (value != null && !value.equals(currentValues.get(setPage + "_" + key))) {
                            value = ((key.startsWith("c_") && value.equals("checked")) || value.equals("on")) ? "X" : value;
                            record.setValue(value);
                        } else if (value == null) {
                            record.setValue("");
                        }
                    }

                } else if (currentValues.get(setPage + "_" + key) == null && value != null) {
                    value = ((key.startsWith("c_") && value.equals("checked")) || value.equals("on")) ? "X" : value;

                    addRecords.add(new FormBCAR2020Data(formId, providerNo, setPage, key, value));
                }
            }
        }

        for (FormBCAR2020Data rec : addRecords) {
            persistRecords.add(rec);
        }

        bcar2020DataDao.batchPersist(persistRecords, 50);

        for (FormBCAR2020Text rec : addTextRecords) {
            persistTextRecords.add(rec);
        }

        bcar2020TextDao.batchPersist(persistTextRecords, 50);

        String url = "/form/formBCAR2020pg";
        if (forwardTo == 6) {
            url = url + "Attachments.jsp";
        } else {
            url = url + forwardTo + ".jsp";
        }
        url = url + "?demographic_no=" + demographicNo + "&formId=" + formId + "&provNo=" + providerNo;
        response.sendRedirect(url);
        return NONE;
    }

    public String print() {
        Integer demographicNo = Integer.parseInt(request.getParameter("demographicNo"));
        Integer formId = Integer.parseInt(request.getParameter("formId"));
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        List<Integer> pagesToPrint = new ArrayList<>();
        // Loops through checking which pages were selected for printing
        for (int page = 1; page <= 6; page++) {
            // If a page was selected to print, adds it to the list to print
            if (Boolean.parseBoolean(request.getParameter("printPg" + page))) {
                pagesToPrint.add(page);
            }
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"BCAR2020_" + formId + ".pdf\"");

        try {
            printPdf(response.getOutputStream(), loggedInInfo, demographicNo, formId, pagesToPrint);
        } catch (IOException e) {
            logger.error("Could not retrieve OutputStream from the response to print the perinatal form", e);
        }

        return null;
    }

    public void printPdf(OutputStream os, LoggedInInfo loggedInInfo, Integer demographicNo, Integer formId, List<Integer> pagesToPrint) {

        // Creates a new print resource log item so we can track who has printed the perinatal form
        PrintResourceLog item = new PrintResourceLog();
        item.setDateTime(new Date());
        item.setExternalLocation("None");
        item.setExternalMethod("None");
        item.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        item.setResourceId(demographicNo.toString());
        item.setResourceName("BCAR2020");

        PrintResourceLogDao printLogDao = SpringUtils.getBean(PrintResourceLogDao.class);
        printLogDao.persist(item);


        final String RESOURCE_PATH = "/oscar/form/bcar2020/BCAR2020_pg";
        ClassLoader cl = getClass().getClassLoader();

        try {
            FrmBCAR2020Record bcar2020Record = (FrmBCAR2020Record) (new FrmRecordFactory()).factory(RECORD_NAME);

            try {
                List<JasperPrint> pages = new ArrayList<>();
                for (Integer pageNumber : pagesToPrint) {
                    String pageImage = RESOURCE_PATH + pageNumber + ".png";
                    String reportUri = RESOURCE_PATH + pageNumber + ".jrxml";

                    Properties recordData = bcar2020Record.getFormRecord(loggedInInfo, demographicNo, formId, pageNumber);
                    recordData.setProperty("background_image", cl.getResource(pageImage).toString());
                    recordData.setProperty("s_languagePreferred", LanguageUtil.getLanguage(recordData.getProperty("s_languagePreferred", "")));

                    JasperReport report = JasperCompileManager.compileReport(cl.getResource(reportUri).toURI().getPath());
                    JasperPrint jasperPrint = JasperFillManager.fillReport(report, (Map) recordData, new JREmptyDataSource());

                    pages.add(jasperPrint);
                }

                JRPdfExporter exporter = new JRPdfExporter();
                exporter.setExporterInput(SimpleExporterInput.getInstance(pages));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(os));
                exporter.exportReport();

            } catch (URISyntaxException e) {
                logger.error("Could not get URI of the BCAR2020 pages for " + pagesToPrint.toString(), e);
            }
        } catch (NumberFormatException e) {
            logger.error("Could not parse formId for " + formId);
        } catch (JRException e) {
            MiscUtils.getLogger().error("Could not parse Report Template for the BCAR2020 form", e);
        } catch (SQLException e) {
            logger.error("Could not retrieve record for BCAR2020 form", e);
        }
    }

    private FormBCAR2020Data getRecordByKey(List<FormBCAR2020Data> records, String key) {
        FormBCAR2020Data record = null;

        for (FormBCAR2020Data rec : records) {
            if (rec.getField().equals(key)) {
                record = rec;
            }
        }

        return record;
    }

    private Map<String, String> getMappedRecords(List<FormBCAR2020Data> records) {
        Map<String, String> recordMap = new HashMap<String, String>();

        if (records != null) {
            for (FormBCAR2020Data record : records) {
                recordMap.put(record.getPageNo() + "_" + record.getField(), record.getValue());
            }
        }

        return recordMap;
    }

    private FormBCAR2020Text getTextRecordByKey(List<FormBCAR2020Text> records, String key) {
        FormBCAR2020Text record = null;

        for (FormBCAR2020Text rec : records) {
            if (rec.getField().equals(key)) {
                record = rec;
            }
        }

        return record;
    }

    private Map<String, String> getMappedTextRecords(List<FormBCAR2020Text> records) {
        Map<String, String> recordMap = new HashMap<String, String>();

        if (records != null) {
            for (FormBCAR2020Text record : records) {
                recordMap.put(record.getPageNo() + "_" + record.getField(), record.getValue());
            }
        }

        return recordMap;
    }
}
