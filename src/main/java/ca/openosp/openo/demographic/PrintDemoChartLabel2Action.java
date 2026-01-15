//CHECKSTYLE:OFF

package ca.openosp.openo.demographic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarDocumentCreator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PrintDemoChartLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public PrintDemoChartLabel2Action() {
    }

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        Provider provider = loggedInInfo.getLoggedInProvider();
        String curUser_no = loggedInInfo.getLoggedInProviderNo();
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT);
        if (prop != null) {
            if (prop.getValue().equalsIgnoreCase("yes")) {
                silentPrint = true;
            }
        }
        String exportPdfJavascript = null;

        if (defaultPrinterName != null && !defaultPrinterName.isEmpty()) {
            exportPdfJavascript = "var params = this.getPrintParams();"
                    + "params.pageHandling=params.constants.handling.none;"
                    + "params.printerName='" + defaultPrinterName + "';";
            if (silentPrint == true) {
                exportPdfJavascript += "params.interactive=params.constants.interactionLevel.silent;";
            }
            exportPdfJavascript += "this.print(params);";
        }
        Map<String, String> nameToFileMap = new HashMap<String, String>();
        nameToFileMap.put("ChartLabel", "Chartlabel.xml");
        nameToFileMap.put("SexualHealthClinicLabel", "SexualHealthClinicLabel.xml");

        String labelFile = nameToFileMap.get("ChartLabel");

        if (request.getParameter("labelName") != null) {
            labelFile = nameToFileMap.get(request.getParameter("labelName"));
        }

        if (labelFile == null) {
            logger.warn("requested invalid label : " + request.getParameter("labelName"));
            return SUCCESS;
        }

        //patient
        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        if (classpath == null)
            classpath = (String) request.getSession().getServletContext().getAttribute("com.ibm.websphere.servlet.application.classpath");
        System.setProperty("jasper.reports.compile.class.path", classpath);

        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("demo", request.getParameter("demographic_no"));

        ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);

        parameters.put("program", "N/A");
        ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, provider.getProviderNo());
        if (pp != null) {
            Program program = programManager2.getProgram(loggedInInfo, pp.getProgramId().intValue());
            if (program != null) {
                parameters.put("program", program.getName());
            }
        }

        ServletOutputStream sos = null;
        InputStream ins = null;

        try {
            try {
                ins = new FileInputStream(System.getProperty("user.home") + File.separator + labelFile);
            } catch (FileNotFoundException ex1) {
                logger.warn(labelFile + " not found in user's home directory. Using default instead (classpath)", ex1);
            }

            if (ins == null) {
                try {
                    ins = getClass().getResourceAsStream("/oscar/oscarDemographic/" + labelFile);
                    logger.debug("loading from : /oscar/oscarDemographic/" + labelFile);
                } catch (Exception ex1) {
                    MiscUtils.getLogger().error("Error", ex1);
                }
            }

            try {
                sos = response.getOutputStream();
            } catch (IOException ex) {
                MiscUtils.getLogger().error("Error", ex);
            }

            response.setHeader("Content-disposition", getHeader(response).toString());
            OscarDocumentCreator osc = new OscarDocumentCreator();

            osc.fillDocumentStream(parameters, sos, "pdf", ins, DbConnectionFilter.getThreadLocalDbConnection(), exportPdfJavascript);
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            if (ins != null) {
                try {
                    ins.close();
                } catch (IOException e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }
        }

        return SUCCESS;
    }

    private StringBuilder getHeader(HttpServletResponse response) {
        StringBuilder strHeader = new StringBuilder();
        strHeader.append("label_");
        strHeader.append(".pdf");
        response.setHeader("Cache-Control", "max-age=0");
        response.setDateHeader("Expires", 0);
        response.setContentType("application/pdf");
        StringBuilder sbContentDispValue = new StringBuilder();
        sbContentDispValue.append("inline; filename="); //inline - display
        sbContentDispValue.append(strHeader);
        return sbContentDispValue;
    }
}
