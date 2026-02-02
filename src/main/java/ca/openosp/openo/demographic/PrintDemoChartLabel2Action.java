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

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for generating and printing patient demographic chart labels in PDF format.
 *
 * This action handles the creation of various types of chart labels for patient records,
 * including standard chart labels and specialized labels such as sexual health clinic labels.
 * The generated PDFs can be automatically printed to a configured printer with optional
 * silent printing mode.
 *
 * The action supports:
 * <ul>
 *   <li>Multiple label types via configurable XML templates (ChartLabel, SexualHealthClinicLabel)</li>
 *   <li>User-specific printer configuration and silent print mode preferences</li>
 *   <li>Program-based context information for patient labels</li>
 *   <li>PDF generation using JasperReports with custom JavaScript for print automation</li>
 *   <li>Template loading from user home directory or classpath fallback</li>
 * </ul>
 *
 * Healthcare Context:
 * Chart labels are physical labels printed for patient files containing demographic information,
 * current program assignment, and other identifying information used in paper-based medical
 * record management within healthcare facilities.
 *
 * Security:
 * Requires "_demographic" read privilege to access patient demographic information.
 *
 * @see ca.openosp.openo.commn.dao.UserPropertyDAO
 * @see ca.openosp.openo.managers.ProgramManager2
 * @see ca.openosp.openo.managers.SecurityInfoManager
 * @see ca.openosp.OscarDocumentCreator
 * @since 2026-01-24
 */
public class PrintDemoChartLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Default constructor for PrintDemoChartLabel2Action.
     *
     * Initializes the action with default state. Spring and Struts2 dependencies
     * are injected via field initialization and ServletActionContext.
     */
    public PrintDemoChartLabel2Action() {
    }

    /**
     * Main action execution method that generates and streams a patient chart label PDF.
     *
     * This method performs the following operations:
     * <ol>
     *   <li>Validates user has "_demographic" read privilege</li>
     *   <li>Retrieves user printer preferences (default printer name and silent print mode)</li>
     *   <li>Determines which label template to use (ChartLabel or SexualHealthClinicLabel)</li>
     *   <li>Loads the label template XML from user home directory or classpath</li>
     *   <li>Gathers demographic and program context parameters</li>
     *   <li>Generates PDF using JasperReports via OscarDocumentCreator</li>
     *   <li>Streams PDF to response with optional JavaScript for automatic printing</li>
     * </ol>
     *
     * Request Parameters:
     * <ul>
     *   <li>demographic_no (String) - The patient demographic identifier to generate label for</li>
     *   <li>labelName (String, optional) - The type of label to generate (e.g., "ChartLabel", "SexualHealthClinicLabel")</li>
     * </ul>
     *
     * User Properties Consulted:
     * <ul>
     *   <li>DEFAULT_PRINTER_PDF_CHART_LABEL - Configured printer name for automatic printing</li>
     *   <li>DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT - Whether to print silently without dialog ("yes"/"no")</li>
     * </ul>
     *
     * Template Resolution:
     * First attempts to load template from user's home directory, then falls back to
     * classpath resource at /oscar/oscarDemographic/{labelFile}.
     *
     * PDF Generation:
     * The generated PDF is streamed directly to the response output stream with content
     * type "application/pdf" and inline disposition. If a default printer is configured,
     * embedded JavaScript in the PDF will trigger automatic printing on open.
     *
     * @return String ActionSupport result constant, always returns SUCCESS
     * @throws SecurityException if user lacks "_demographic" read privilege
     */
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

    /**
     * Constructs HTTP response headers for PDF download with inline display disposition.
     *
     * This method configures the HTTP response to display the PDF inline in the browser
     * rather than forcing a download. It sets appropriate cache control headers to prevent
     * caching of the generated label document.
     *
     * Headers Set:
     * <ul>
     *   <li>Cache-Control: max-age=0 - Prevents browser caching</li>
     *   <li>Expires: 0 - Sets expiration to epoch time</li>
     *   <li>Content-Type: application/pdf - Identifies response as PDF document</li>
     *   <li>Content-Disposition: inline; filename=label_.pdf - Displays inline with default filename</li>
     * </ul>
     *
     * @param response HttpServletResponse the servlet response to configure headers on
     * @return StringBuilder containing the Content-Disposition header value in format "inline; filename=label_.pdf"
     */
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
