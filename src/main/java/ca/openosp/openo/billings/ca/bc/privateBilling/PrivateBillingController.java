//CHECKSTYLE:OFF
package ca.openosp.openo.billings.ca.bc.privateBilling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.demographic.data.DemographicData;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.clinic.ClinicData;

/**
 * HTTP servlet controller for managing private billing operations in British Columbia.
 * <p>
 * This controller handles the display and printing of private bills (non-MSP/non-provincial
 * insurance billing) for BC healthcare providers. It supports listing private bills by provider,
 * generating print previews with patient and recipient information, and integrating clinic details
 * for invoice generation.
 * </p>
 * <p>
 * Key operations include:
 * <ul>
 * <li>Listing private bills filtered by provider ID</li>
 * <li>Generating print preview for selected bills with patient demographics</li>
 * <li>Handling recipient information (either patient or third-party recipient)</li>
 * <li>Including clinic information for invoice headers</li>
 * </ul>
 * </p>
 * <p>
 * This controller is specific to British Columbia's private billing workflows and does not
 * interface with provincial MSP billing systems.
 * </p>
 *
 * @see PrivateBillingDAO
 * @see ca.openosp.openo.commn.model.Demographic
 * @see ca.openosp.openo.commn.model.Provider
 * @since 2026-01-23
 */
public class PrivateBillingController extends HttpServlet {
    private static String LIST_PRIVATE_BILLS = "billing/CA/BC/privateBilling/viewStatement.jsp";
    private static String PRINT_PREVIEW_BILLS = "billing/CA/BC/privateBilling/printPreview.jsp";
    private PrivateBillingDAO dao;
    private ProviderDao providerDao;

    /**
     * Constructs a new PrivateBillingController instance.
     * <p>
     * Initializes the PrivateBillingDAO for database operations and retrieves the ProviderDao
     * from the Spring application context for provider data access.
     * </p>
     */
    public PrivateBillingController() {
        super();
        dao = new PrivateBillingDAO();
        providerDao = SpringUtils.getBean(ProviderDao.class);
    }

    /**
     * Lists all private bills, optionally filtered by provider ID.
     * <p>
     * Retrieves all providers from the database and displays private bills for the specified
     * provider. If no provider ID is specified or the parameter is empty, displays bills for
     * all providers using a wildcard filter ("%").
     * </p>
     * <p>
     * The results are forwarded to the viewStatement.jsp page with the following attributes:
     * <ul>
     * <li>providers - List of all Provider objects</li>
     * <li>providerId - String the selected provider ID or "%" for all</li>
     * <li>bills - List of private bills matching the filter criteria</li>
     * </ul>
     * </p>
     *
     * @param request HttpServletRequest the servlet request containing optional "providerId" parameter
     * @param response HttpServletResponse the servlet response for forwarding to the view
     * @param forward String the forward path (overridden to LIST_PRIVATE_BILLS)
     * @throws ServletException if request forwarding fails
     * @throws IOException if an I/O error occurs during request processing
     */
    private void listPrivateBills(HttpServletRequest request, HttpServletResponse response, String forward) throws ServletException, IOException {
        try {
            List<Provider> providers = providerDao.getProviders();
            String providerId = request.getParameter("providerId");
            if (providerId == null || providerId.isEmpty()) {
                providerId = "%";
            }
            forward = LIST_PRIVATE_BILLS;
            request.setAttribute("providers", providers);
            request.setAttribute("providerId", providerId);
            request.setAttribute("bills", dao.listPrivateBills(providerId));

            RequestDispatcher view = request.getRequestDispatcher(forward);
            view.forward(request, response);
        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a print preview for selected private bills with patient and recipient information.
     * <p>
     * Processes a JSON array of bill IDs to create print-ready invoice data. For each bill:
     * <ul>
     * <li>Retrieves patient demographic information (name, address, date of birth)</li>
     * <li>Determines recipient information (patient or third-party recipient)</li>
     * <li>Includes current clinic information (name, address, contact details)</li>
     * <li>Fetches invoice line items for the bill</li>
     * </ul>
     * </p>
     * <p>
     * The method expects a "billIds" parameter containing a JSON array with objects having:
     * <ul>
     * <li>demographicNumber - String the patient's demographic ID</li>
     * <li>recipientId - String the recipient's ID (empty string indicates patient is recipient)</li>
     * </ul>
     * </p>
     * <p>
     * Results are forwarded to printPreview.jsp with attributes:
     * <ul>
     * <li>date - String current date for invoice generation</li>
     * <li>billToClinic - String flag indicating if bill is directed to clinic</li>
     * <li>billIds - String the original JSON array of bill IDs</li>
     * <li>patientBills - List of HashMap objects containing complete bill data for each patient</li>
     * </ul>
     * </p>
     *
     * @param request HttpServletRequest the servlet request containing "billIds" parameter (JSON array)
     * @param response HttpServletResponse the servlet response for forwarding to the print preview
     * @param forward String the forward path (overridden to PRINT_PREVIEW_BILLS)
     * @throws ServletException if request forwarding fails
     * @throws IOException if an I/O error occurs during request processing
     */
    private void printPreviewBills(HttpServletRequest request, HttpServletResponse response, String forward) throws ServletException, IOException {
        try {
            DemographicData demoData = new DemographicData();
            List<HashMap> patientBills = new ArrayList<HashMap>();

            String[] paramValues = request.getParameterValues("billIds");
            if (paramValues.length == 1) {
                JsonArray jsonArr = new JsonParser().parse(paramValues[0]).getAsJsonArray();
                for (int i = 0; i < jsonArr.size(); i++) {
                    HashMap<String, Object> map = new HashMap<String, Object>();
                    JsonElement jsonElem = jsonArr.get(i);
                    JsonElement demographicNumber = jsonElem.getAsJsonObject().get("demographicNumber");
                    String strDemographicNumber = demographicNumber.getAsString();
                    Demographic patient = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), strDemographicNumber);
                    map.put("patientFirstName", patient.getFirstName());
                    map.put("patientLastName", patient.getLastName());
                    map.put("patientAddress", patient.getAddress());
                    map.put("patientCity", patient.getCity());
                    map.put("patientProvince", patient.getProvince());
                    map.put("patientPostal", patient.getPostal());
                    map.put("patientMonthOfBirth", patient.getMonthOfBirth());
                    map.put("patientDateOfBirth", patient.getDateOfBirth());
                    map.put("patientYearOfBirth", patient.getYearOfBirth());

                    // get recipient info (note: a null attribute means the recipient is the patient)
                    JsonElement recipientId = jsonElem.getAsJsonObject().get("recipientId");
                    String strRecipientId = recipientId.getAsString();
                    if (strRecipientId.isEmpty() || strRecipientId == "") {
                        map.put("recipientName", patient.getFirstName() + " " + patient.getLastName());
                        map.put("recipientAddress", patient.getAddress());
                        map.put("recipientCity", patient.getCity());
                        map.put("recipientProvince", patient.getProvince());
                        map.put("recipientPostal", patient.getPostal());
                    } else {
                        HashMap<String, String> recipient = dao.getRecipientById(strRecipientId);
                        map.put("recipientName", recipient.get("name"));
                        map.put("recipientAddress", recipient.get("address"));
                        map.put("recipientCity", recipient.get("city"));
                        map.put("recipientProvince", recipient.get("province"));
                        map.put("recipientPostal", recipient.get("postal"));
                    }

                    // get current clinic info
                    ClinicData clinic = new ClinicData();
                    map.put("clinicName", clinic.getClinicName());
                    map.put("clinicAddress", clinic.getClinicAddress());
                    map.put("clinicCity", clinic.getClinicCity());
                    map.put("clinicProvince", clinic.getClinicProvince());
                    map.put("clinicPostal", clinic.getClinicPostal());
                    map.put("clinicPhone", clinic.getClinicPhone());
                    map.put("clinicFax", clinic.getClinicFax());

                    // get patient invoice items
                    String strRecipientName = (strRecipientId.isEmpty() || strRecipientId == "") ? "" : map.get("recipientName").toString();
                    List<HashMap<String, String>> invoiceItems = dao.listPrivateBillItems(strDemographicNumber, strRecipientName);
                    map.put("invoiceItems", invoiceItems);

                    // queue patient bill data
                    patientBills.add(map);
                }
            }

            String billToClinic = request.getParameter("billToClinic");
            String billIds = request.getParameter("billIds");

            forward = PRINT_PREVIEW_BILLS;
            request.setAttribute("date", new Date().toString());
            request.setAttribute("billToClinic", billToClinic);
            request.setAttribute("billIds", billIds);
            request.setAttribute("patientBills", patientBills);

            RequestDispatcher view = request.getRequestDispatcher(forward);
            view.forward(request, response);
        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles HTTP GET requests for private billing operations.
     * <p>
     * Routes requests based on the "action" parameter to the appropriate handler method:
     * <ul>
     * <li>"listPrivateBills" - Displays a list of private bills (default action)</li>
     * <li>"printPreviewBills" - Generates print preview for selected bills</li>
     * </ul>
     * </p>
     * <p>
     * If the action parameter is missing or null, defaults to listing private bills.
     * This provides a safe fallback behavior for direct navigation to the controller.
     * </p>
     *
     * @param request HttpServletRequest the servlet request containing the "action" parameter
     * @param response HttpServletResponse the servlet response for forwarding to the appropriate view
     * @throws ServletException if request forwarding fails
     * @throws IOException if an I/O error occurs during request processing
     * @throws NullPointerException if the action parameter is null (caught and handled internally)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException, NullPointerException {
        String forward = "";
        String action = request.getParameter("action");
        try {
            if (action.equalsIgnoreCase("listPrivateBills")) {
                listPrivateBills(request, response, forward);
            } else if (action.equalsIgnoreCase("printPreviewBills")) {
                printPreviewBills(request, response, forward);
            } else {
                // missing 'billIds' parameters, go back to default action 'LIST_PRIVATE_BILLS'
                listPrivateBills(request, response, forward);
            }
        } catch (NullPointerException e) {
            // action is not provided, by default forward to LIST_PRIVATE_BILLS
            listPrivateBills(request, response, forward);
        } catch (ServletException e) {
            e.printStackTrace();
        }
    }

}