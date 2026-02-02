//CHECKSTYLE:OFF

package ca.openosp.openo.demographic;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarDocumentCreator;
import ca.openosp.OscarProperties;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that generates and prints patient demographic labels as PDF documents.
 *
 * <p>This 2Action class handles the generation of patient demographic labels for printing,
 * typically used for chart filing, specimen labeling, or patient identification purposes
 * in a healthcare setting. The action supports configurable label templates and printer
 * settings per provider.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>PDF label generation from XML templates using JasperReports</li>
 *   <li>Per-provider default printer configuration</li>
 *   <li>Silent printing support (automatic printing without user interaction)</li>
 *   <li>Multiple label template support (MRP labels, appointment provider labels)</li>
 *   <li>Integration with patient demographic and appointment data</li>
 * </ul>
 *
 * <p>Security: Requires the "_demographic" security privilege with read access.
 * All patient data access is logged through the LoggedInInfo framework for
 * HIPAA/PIPEDA compliance.</p>
 *
 * <p>Label templates are configurable via OscarProperties:</p>
 * <ul>
 *   <li><code>pdfLabelMRP</code> - Path to the MRP (Most Responsible Provider) label template</li>
 *   <li><code>pdfLabelApptProvider</code> - Path to the appointment provider label template</li>
 * </ul>
 *
 * <p>Default printer settings are managed per provider using UserProperty entries:</p>
 * <ul>
 *   <li><code>DEFAULT_PRINTER_PDF_LABEL</code> - Default printer name for PDF labels</li>
 *   <li><code>DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT</code> - Silent print flag (yes/no)</li>
 * </ul>
 *
 * @see ca.openosp.openo.commn.model.UserProperty
 * @see ca.openosp.OscarDocumentCreator
 * @see ca.openosp.openo.managers.SecurityInfoManager
 * @since 2026-01-24
 */
public class PrintDemoLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Default constructor for PrintDemoLabel2Action.
     *
     * <p>Initializes the action with Spring-injected dependencies via SpringUtils.
     * The HttpServletRequest and HttpServletResponse are obtained from ServletActionContext
     * following the Struts2 2Action pattern.</p>
     */
    public PrintDemoLabel2Action() {
    }

    /**
     * Executes the main action logic to generate and stream a PDF label for a patient.
     *
     * <p>This method performs the following workflow:</p>
     * <ol>
     *   <li>Validates security privilege for demographic data access</li>
     *   <li>Retrieves provider-specific printer settings from UserProperty</li>
     *   <li>Determines the appropriate label template (MRP or appointment provider)</li>
     *   <li>Loads the label template XML from configured path or default classpath resource</li>
     *   <li>Generates PDF using OscarDocumentCreator with patient demographic data</li>
     *   <li>Streams the PDF to the response output with appropriate headers</li>
     *   <li>Optionally injects JavaScript for automatic/silent printing</li>
     * </ol>
     *
     * <p>Request parameters:</p>
     * <ul>
     *   <li><code>demographic_no</code> - String the patient demographic number (required)</li>
     *   <li><code>appointment_no</code> - Integer the appointment number (optional, triggers
     *       appointment provider label if configured)</li>
     * </ul>
     *
     * <p>The method uses the provider's configured printer settings to determine whether
     * to enable automatic printing and which printer to use. If silent printing is enabled,
     * the PDF will include JavaScript to automatically print to the specified printer
     * without user interaction.</p>
     *
     * <p>Label template resolution follows this order:</p>
     * <ol>
     *   <li>If appointment_no provided and pdfLabelApptProvider configured: use appointment provider template</li>
     *   <li>Otherwise: use pdfLabelMRP template (default: ~/label.xml)</li>
     *   <li>Fallback: use bundled /oscar/oscarDemographic/label.xml resource</li>
     * </ol>
     *
     * <p>The generated PDF is streamed directly to the HttpServletResponse with
     * Content-Type: application/pdf and Content-Disposition: inline, causing
     * the browser to display the PDF inline rather than prompting for download.</p>
     *
     * @return String always returns SUCCESS after streaming the PDF
     * @throws SecurityException if the current user lacks "_demographic" read privilege
     */
    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        //patient
        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        if (classpath == null)
            classpath = (String) request.getSession().getServletContext().getAttribute("com.ibm.websphere.servlet.application.classpath");

        System.setProperty("jasper.reports.compile.class.path", classpath);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String curUser_no = loggedInInfo.getLoggedInProviderNo();
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_LABEL);
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
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("demo", request.getParameter("demographic_no"));

        Integer apptNo = null;
        try {
            apptNo = Integer.parseInt(request.getParameter("appointment_no"));
        } catch (NumberFormatException e) {
        }

        String defaultLabelPath = System.getProperty("user.home") + "/label.xml";
        String labelPath = OscarProperties.getInstance().getProperty("pdfLabelMRP", defaultLabelPath);
        String apptProviderLabelPath = OscarProperties.getInstance().getProperty("pdfLabelApptProvider", "");

        if (apptNo != null && !apptProviderLabelPath.isEmpty()) {
            parameters.put("appt", String.valueOf(apptNo));
            labelPath = apptProviderLabelPath;
        }

        ServletOutputStream sos = null;
        InputStream ins = null;


        logger.debug("user home: " + System.getProperty("user.home"));
        try {
            ins = new FileInputStream(labelPath);
            logger.debug("loading from :" + labelPath + " " + ins);
        } catch (FileNotFoundException ex1) {
            logger.warn("label xml file not found at " + labelPath + " using default instead");
        }
        if (ins == null) {
            try {
//                ServletContext context = getServlet().getServletContext();
                ins = getClass().getResourceAsStream("/oscar/oscarDemographic/label.xml");
                logger.debug("loading from : /oscar/oscarDemographic/label.xml " + ins);
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
        try {
            osc.fillDocumentStream(parameters, sos, "pdf", ins, DbConnectionFilter.getThreadLocalDbConnection(), exportPdfJavascript);
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    /**
     * Constructs the HTTP Content-Disposition header value for the PDF label response.
     *
     * <p>This method builds the Content-Disposition header to instruct the browser to
     * display the PDF inline (within the browser) rather than prompting for download.
     * It also sets cache control headers to prevent caching of the PDF document.</p>
     *
     * <p>The method sets the following response headers:</p>
     * <ul>
     *   <li><code>Cache-Control: max-age=0</code> - Prevents browser caching</li>
     *   <li><code>Expires: 0</code> - Sets expiration to epoch (prevents caching)</li>
     *   <li><code>Content-Type: application/pdf</code> - Identifies content as PDF</li>
     * </ul>
     *
     * <p>The generated filename is always "label_.pdf". This appears to be a potential
     * issue as the underscore suggests a missing identifier (demographic_no or timestamp)
     * should be included in the filename.</p>
     *
     * @param response HttpServletResponse the HTTP response object to configure headers on
     * @return StringBuilder the Content-Disposition header value in format "inline; filename=label_.pdf"
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
