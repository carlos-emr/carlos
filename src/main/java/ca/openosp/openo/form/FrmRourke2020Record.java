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
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.dao.forms.Rourke2020Dao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ca.openosp.openo.form.model.FormRourke2020;
import ca.openosp.openo.form.util.JasperReportPdfPrint;
import ca.openosp.openo.encounter.data.EctFormData;
import ca.openosp.openo.util.UtilDateUtilities;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FrmRourke2020Record extends FrmRecord implements JasperReportPdfPrint {
    private final Logger logger = LoggerFactory.getLogger(FrmRourke2020Record.class);

    private static final String HEAD_CIRCUMFERENCE_GRAPH = "HEAD_CIRC";
    private static final String LENGTH_GRAPH = "LENGTH";

    private String graphType;

    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) {
        Properties props = new Properties();

        Demographic demo = demographicManager.getDemographic(loggedInInfo, demographicNo);
        String updated = "false";
        Date dob = null;

        if (demo != null) {
            if (existingID <= 0) {
                // Set form defaults
                props.setProperty("demographic_no", String.valueOf(demographicNo));
                props.setProperty("c_pName", demo.getFormattedName());
                props.setProperty("formCreated", UtilDateUtilities.DateToString(new Date(), "dd/MM/yyyy"));
                dob = UtilDateUtilities.calcDate(demo.getYearOfBirth(), demo.getMonthOfBirth(), demo.getDateOfBirth());
                props.setProperty("c_birthDate", UtilDateUtilities.DateToString(dob, "dd/MM/yyyy"));
                String postal = demo.getPostal();
                if (postal != null && postal.length() >= 6) {
                    postal = postal.replaceAll("\\s+", "");
                    postal = postal.substring(0, 3);
                } else {
                    postal = "";
                }

                props.setProperty("c_fsa", postal);

            } else {

                Rourke2020Dao rourke2020Dao = SpringUtils.getBean(Rourke2020Dao.class);
                FormRourke2020 form = rourke2020Dao.find(existingID);
                props = form.toProperties();
                String rourkeVal = props.getProperty("c_pName", "");
                String demoVal = demo.getFormattedName();

                if (!rourkeVal.equals(demoVal)) {
                    props.setProperty("c_pName", demoVal);
                    updated = "true";
                }

                rourkeVal = props.getProperty("c_birthDate", "");
                dob = UtilDateUtilities.calcDate(demo.getYearOfBirth(), demo.getMonthOfBirth(), demo.getDateOfBirth());
                demoVal = UtilDateUtilities.DateToString(dob, "dd/MM/yyyy");

                if (!rourkeVal.equals(demoVal)) {
                    props.setProperty("c_birthDate", demoVal);
                    updated = "true";
                }

                String gestationalAge = "";
                if (form.getStartOfGestation() != null) {
                    gestationalAge = calculateGestationalAgeInWeeks(form.getStartOfGestation(), dob) + " weeks";
                }
                props.setProperty("c_gestationalAge", gestationalAge);

                demoVal = demo.getPostal();
                if (demoVal != null && demoVal.length() >= 6) {
                    demoVal = demoVal.replaceAll("\\s+", "");
                    demoVal = demoVal.substring(0, 3);
                    rourkeVal = props.getProperty("c_fsa", "");
                    if (!rourkeVal.equals(demoVal)) {
                        props.setProperty("c_fsa", demoVal);
                        updated = "true";
                    }
                }
            }
        }
        props.setProperty("updated", updated);

        if (dob != null) {
            //set startdate for second page as defined in config file
            Calendar cal = Calendar.getInstance();
            cal.setTime(dob);
            cal.add(Calendar.YEAR, 2);
            props.setProperty("__startDate", UtilDateUtilities.DateToString(cal.getTime(), "dd/MM/yyyy"));
        }

        //don't forget to set the xAxis scale for the 2 pages
        props.setProperty("__xDateScale_1", String.valueOf(Calendar.MONTH));
        props.setProperty("__xDateScale_2", String.valueOf(Calendar.YEAR));

        return props;
    }

    public int saveFormRecord(Properties props) throws SQLException {
        FrmRecordHelp frmRec = new FrmRecordHelp();
        frmRec.setDateFormat("dd/MM/yyyy");

        FormRourke2020 newForm = new FormRourke2020(props);
        newForm.createBooleanValueMapFromFormProperties(props);
        Rourke2020Dao rourke2020Dao = SpringUtils.getBean(Rourke2020Dao.class);
        rourke2020Dao.saveEntity(newForm);

        return newForm.getId();
    }

    public boolean isFemale(LoggedInInfo loggedInInfo, int demoNo) {
        boolean retval = false;
        Demographic demo = demographicManager.getDemographic(loggedInInfo, demoNo);

        if (demo != null && demo.getSex().equalsIgnoreCase("F")) {
            retval = true;
        }
        return retval;
    }

    public Properties getGraph(LoggedInInfo loggedInInfo, int demographicNo, int existingID) {
        String formClass = "Growth0_36";
        Properties props = new Properties();

        if (existingID == 0) {
            return props;
        } else {
            try {
                String[] functions = {"getC_pName", "getC_birthDate", "getC_birthWeight", "getC_headCirc",
                        "getCLength", "getP1Date1w", "getP1Date2w", "getP1Date1m", "getP2Date2m", "getP2Date4m",
                        "getP2Date6m", "getP3Date9m", "getP3Date12m", "getP3Date15m", "getP4Date18m", "getP4Date24m",
                        "getP1Hc1w", "getP1Hc2w", "getP1Hc1m", "getP2Hc2m", "getP2Hc4m", "getP2Hc6m", "getP3Hc9m",
                        "getP3Hc12m", "getP3Hc15m", "getP4Hc18m", "getP4Hc24m", "getP1Wt1w", "getP1Wt2w", "getP1Wt1m",
                        "getP2Wt2m", "getP2Wt4m", "getP2Wt6m", "getP3Wt9m", "getP3Wt12m", "getP3Wt15m", "getP4Wt18m",
                        "getP4Wt24m", "getP1Ht1w", "getP1Ht2w", "getP1Ht1m", "getP2Ht2m", "getP2Ht4m", "getP2Ht6m",
                        "getP3Ht9m", "getP3Ht12m", "getP3Ht15m", "getP4Ht18m", "getP4Ht24m", "getP4Ht48m", "getP4Wt48m", "getP4Date48m"};
                String[] names = {"c_pName", "c_birthDate", "c_birthWeight", "c_headCirc", "c_length",
                        "p1_date1w", "p1_date2w", "p1_date1m", "p2_date2m", "p2_date4m", "p2_date6m", "p3_date9m",
                        "p3_date12m", "p3_date15m", "p4_date18m", "p4_date24m", "p1_hc1w", "p1_hc2w", "p1_hc1m",
                        "p2_hc2m", "p2_hc4m", "p2_hc6m", "p3_hc9m", "p3_hc12m", "p3_hc15m", "p4_hc18m", "p4_hc24m",
                        "p1_wt1w", "p1_wt2w", "p1_wt1m", "p2_wt2m", "p2_wt4m", "p2_wt6m", "p3_wt9m", "p3_wt12m", "p3_wt15m",
                        "p4_wt18m", "p4_wt24m", "p1_ht1w", "p1_ht2w", "p1_ht1m", "p2_ht2m", "p2_ht4m", "p2_ht6m", "p3_ht9m",
                        "p3_ht12m", "p3_ht15m", "p4_ht18m", "p4_ht24m", "p4_ht48m", "p4_wt48m", "p4_date48m"};
                Rourke2020Dao rourkeDao = SpringUtils.getBean(Rourke2020Dao.class);
                FormRourke2020 frmRourke = rourkeDao.find(existingID);

                if (frmRourke != null) {
                    Class cRourke = FormRourke2020.class;
                    Object returnVal;
                    String value;
                    for (int idx = 0; idx < names.length; ++idx) {
                        Method method = cRourke.getMethod(functions[idx]);
                        returnVal = method.invoke(frmRourke);

                        if (returnVal != null) {
                            if (returnVal instanceof Date) {
                                value = UtilDateUtilities.DateToString((Date) returnVal, "dd/MM/yyyy");
                            } else {
                                value = (String) returnVal;
                            }
                            props.setProperty(names[idx], value);
                        }
                    }
                    Calendar cal = Calendar.getInstance();
                    Date now = cal.getTime();
                    Date dob = frmRourke.getC_birthDate();
                    String age;
                    if (dob != null) {
                        age = String.valueOf((now.getTime() - dob.getTime()) / 1000L / 60L / 60L / 24L);
                    } else {
                        age = "";
                    }

                    props.setProperty("c_Age", age);

                }//end if

                //now we add measurements from formGrowth0_36 form

                EctFormData.PatientForm[] pforms
                        = EctFormData.getPatientFormsFromLocalAndRemote(loggedInInfo, String.valueOf(demographicNo), "formGrowth0_36");
                if (pforms.length > 0) {
                    EctFormData.PatientForm pfrm = pforms[0];
                    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);

                    try {
                        Properties growthProps = rec.getFormRecord(loggedInInfo, demographicNo, pfrm.formId);
                        Enumeration<Object> keys = growthProps.keys();
                        String key;
                        String value;
                        String[] dates;
                        String date;
                        while (keys.hasMoreElements()) {
                            key = (String) keys.nextElement();
                            if (key.startsWith("date_")) {
                                value = growthProps.getProperty(key, "");
                                if (!value.equals("")) {
                                    dates = value.split("\\/");
                                    if (dates.length == 3) {
                                        date = dates[2] + "/" + dates[1] + "/" + dates[0];
                                        props.setProperty(key, date);
                                    }
                                }
                            } else if (key.startsWith("weight_") || key.startsWith("length_") || key.startsWith("headCirc_")) {
                                props.setProperty(key, growthProps.getProperty(key, ""));
                            }

                        }
                    } catch (SQLException e) {

                        MiscUtils.getLogger().error("", e);
                    }
                }

                //now add measurements from Ht and Wt in measurements group
                //first set up cutoff for first page = 2 years of age
                //then set up cutoff for second page = 19 years of age
                //then we can compare measurement dates and slot them accordingly
                Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);

                MeasurementDao measurementDao = (MeasurementDao) SpringUtils.getBean(MeasurementDao.class);

                Date mDateHt, mDateWt;
                Date dob = demographic.getBirthDay().getTime();
                String date;
                int idx = 0;
                String graphicPage;
                float age;

                //set startdate for second page as defined in config file
                Calendar cal = Calendar.getInstance();
                cal.setTime(dob);
                cal.add(Calendar.YEAR, 2);
                Date startDate = cal.getTime();
                date = UtilDateUtilities.DateToString(startDate, "dd/MM/yyyy");
                props.setProperty("__startDate", date);
                List<Measurement> measurementsHt = measurementDao.findByType(demographicNo, "Ht");
                List<Measurement> measurementsWt = measurementDao.findByType(demographicNo, "Wt");

                for (Measurement mHt : measurementsHt) {
                    graphicPage = null;


                    if (mHt.getDateObserved() != null) {
                        mDateHt = mHt.getDateObserved();
                    } else {
                        mDateHt = mHt.getCreateDate();
                    }

                    age = this.calcYears(dob, mDateHt);
                    MiscUtils.getLogger().info("Age " + age);
                    if (age <= 2) {
                        graphicPage = "0";
                    } else if (age <= 19) {
                        graphicPage = "1";
                    } else {
                        continue;
                    }

                    if (graphType.equals(FrmRourke2020Record.HEAD_CIRCUMFERENCE_GRAPH)) {
                        for (Measurement mWt : measurementsWt) {
                            if (mWt.getDateObserved() != null) {
                                mDateWt = mWt.getDateObserved();
                            } else {
                                mDateWt = mWt.getCreateDate();
                            }

                            if (mDateHt.compareTo(mDateWt) == 0) {
                                //name = elementName_num_section_page
                                props.setProperty("xVal_" + idx + "_1_" + graphicPage, mHt.getDataField());
                                props.setProperty("yVal_" + idx + "_1_" + graphicPage, mWt.getDataField());
                                break;
                            }
                        }
                    } else if (graphType.equals(FrmRourke2020Record.LENGTH_GRAPH)) {
                        date = UtilDateUtilities.DateToString(mDateHt, "dd/MM/yyyy");

                        //name = elementName_num_section_page
                        props.setProperty("xVal_" + idx + "_1_" + graphicPage, date);
                        props.setProperty("yVal_" + idx + "_1_" + graphicPage, mHt.getDataField());
                    }

                    ++idx;
                }

                if (graphType.equals(FrmRourke2020Record.LENGTH_GRAPH)) {
                    for (Measurement m : measurementsWt) {
                        graphicPage = null;
                        if (m.getDateObserved() != null) {
                            mDateWt = m.getDateObserved();
                        } else {
                            mDateWt = m.getCreateDate();
                        }

                        age = this.calcYears(dob, mDateWt);
                        if (age <= 2) {
                            graphicPage = "0";
                        } else if (age <= 19) {
                            graphicPage = "1";
                        } else {
                            continue;
                        }

                        date = UtilDateUtilities.DateToString(mDateWt, "dd/MM/yyyy");


                        props.setProperty("xVal_" + idx + "_0_" + graphicPage, date);
                        props.setProperty("yVal_" + idx + "_0_" + graphicPage, m.getDataField());

                        ++idx;
                    }

                }
                //don't forget to set the xAxis scale for the 2 pages
                props.setProperty("__xDateScale_1", String.valueOf(Calendar.MONTH));
                props.setProperty("__xDateScale_2", String.valueOf(Calendar.YEAR));

            } catch (NoSuchMethodException e) {
                MiscUtils.getLogger().error("No Such Method Called", e);
            } catch (IllegalAccessException e) {
                MiscUtils.getLogger().error("Illegal Access of FormRourke Method", e);
            } catch (InvocationTargetException e) {
                MiscUtils.getLogger().error("Cannot Call Method on Target", e);
            }

        }
        return props;
    }


    public String findActionValue(String submit) throws SQLException {
        return ((new FrmRecordHelp()).findActionValue(submit));
    }

    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        return ((new FrmRecordHelp()).createActionURL(where, action, demoId, formId));
    }

    private float calcYears(Date startDate, Date endDate) {
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(startDate);

        Calendar endCalendar = Calendar.getInstance();
        endCalendar.setTime(endDate);

        long time = endCalendar.getTimeInMillis() - startCalendar.getTimeInMillis();
        float years = time / 1000.0f / 60.0f / 60.0f / 24.0f / 365.0f;

        return years;
    }

    public String getGraphType() {
        return graphType;
    }

    public void setGraphType(String graphType) {
        this.graphType = graphType;
    }

    @Override
    public void PrintJasperPdf(OutputStream os, LoggedInInfo loggedInInfo, Integer demographicNo,
                               Integer formId, List<Integer> pagesToPrint) {
        final String RESOURCE_PATH = "/oscar/form/rourke2020/";
        ClassLoader cl = getClass().getClassLoader();

        Properties recordData = getFormRecord(loggedInInfo, demographicNo, formId);
        String checkImage = RESOURCE_PATH + "check.png";
        String crossImage = RESOURCE_PATH + "cross.png";
        recordData.setProperty("check_image", cl.getResource(checkImage).toString());
        recordData.setProperty("cross_image", cl.getResource(crossImage).toString());


        try {
            List<JasperPrint> pages = new ArrayList<>();
            for (Integer pageNumber : pagesToPrint) {
                String pageImage = RESOURCE_PATH + "page" + pageNumber + ".png";
                String reportUri = RESOURCE_PATH + "page" + pageNumber + ".jrxml";
                recordData.setProperty("background_image", cl.getResource(pageImage).toString());
                JasperReport report = JasperCompileManager.compileReport(cl.getResource(reportUri).toURI().getPath());
                JasperPrint jasperPrint = JasperFillManager.fillReport(report, (Map) recordData, new JREmptyDataSource());

                pages.add(jasperPrint);
            }

            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(SimpleExporterInput.getInstance(pages));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(os));
            exporter.exportReport();

        } catch (URISyntaxException e) {
            logger.error("Could not get URI of the Rourke2020 pages for " + pagesToPrint.toString(), e);
        } catch (NumberFormatException e) {
            logger.error("Could not parse formId for " + 0);
        } catch (JRException e) {
            logger.error("Could not parse Report Template for the Rourke2020 form", e);
        }
    }

    private int calculateGestationalAgeInWeeks(Date pregnancyStartDate, Date birthDate) {

        if (birthDate.before(pregnancyStartDate)) {
            return 0;
        }

        Calendar cal = new GregorianCalendar();
        cal.setTime(pregnancyStartDate);
        int weeks = 0;
        while (cal.getTime().before(birthDate)) {
            // add another week
            cal.add(Calendar.WEEK_OF_YEAR, 1);
            weeks++;
        }
        return weeks - 1;
    }
}
