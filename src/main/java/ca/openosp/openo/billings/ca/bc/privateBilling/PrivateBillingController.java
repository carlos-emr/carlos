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
 * Controller servlet for managing private billing operations in British Columbia.
 *
 * <p>This servlet handles HTTP requests for BC private billing functionality, including:
 * <ul>
 *   <li>Listing private bills for healthcare providers with optional filtering</li>
 *   <li>Generating print preview invoices with patient, recipient, and clinic information</li>
 *   <li>Processing billing data for non-MSP (Medical Services Plan) services</li>
 * </ul>
 *
 * <p>Private billing is used for services not covered by BC's Medical Services Plan,
 * such as medical certificates, employment forms, insurance examinations, and other
 * uninsured services. This controller integrates with the PrivateBillingDAO for data
 * access and uses Spring-managed beans for provider information.
 *
 * <p><strong>Key Operations:</strong>
 * <ul>
 *   <li><strong>listPrivateBills</strong>: Displays private bills filtered by provider</li>
 *   <li><strong>printPreviewBills</strong>: Generates invoice previews with comprehensive
 *       patient demographics, recipient details, and clinic information</li>
 * </ul>
 *
 * <p><strong>Healthcare Context:</strong>
 * BC private billing operates outside the provincial MSP billing system and requires
 * direct patient payment. This controller supports the complete workflow from bill
 * listing through invoice generation for non-insured medical services.
 *
 * @since 2026-01-23
 * @see PrivateBillingDAO
 * @see ca.openosp.openo.commn.model.Demographic
 * @see ca.openosp.openo.commn.model.Provider
 */
public class PrivateBillingController extends HttpServlet {
    private static String LIST_PRIVATE_BILLS = "billing/CA/BC/privateBilling/viewStatement.jsp";
    private static String PRINT_PREVIEW_BILLS = "billing/CA/BC/privateBilling/printPreview.jsp";
    private PrivateBillingDAO dao;
    private ProviderDao providerDao;

    /**
     * Constructs a new PrivateBillingController and initializes data access objects.
     *
     * <p>This constructor initializes:
     * <ul>
     *   <li>PrivateBillingDAO for direct instantiation of private billing data access</li>
     *   <li>ProviderDao retrieved from Spring context for provider information</li>
     * </ul>
     *
     * <p>The ProviderDao is obtained via SpringUtils to leverage Spring-managed
     * transaction boundaries and dependency injection, while PrivateBillingDAO
     * is directly instantiated for specialized BC private billing operations.
     */
    public PrivateBillingController() {
        super();
        dao = new PrivateBillingDAO();
        providerDao = SpringUtils.getBean(ProviderDao.class);
    }

    /**
     * Lists private bills filtered by healthcare provider.
     *
     * <p>This method retrieves and displays private billing records for BC providers.
     * If no provider ID is specified in the request parameters, it defaults to showing
     * all providers' bills using a wildcard filter ("%").
     *
     * <p><strong>Request Parameters:</strong>
     * <ul>
     *   <li><code>providerId</code> (String, optional): The unique identifier of the
     *       healthcare provider. If null or empty, defaults to "%" to match all providers.</li>
     * </ul>
     *
     * <p><strong>Request Attributes Set:</strong>
     * <ul>
     *   <li><code>providers</code> (List&lt;Provider&gt;): All available healthcare providers</li>
     *   <li><code>providerId</code> (String): The active provider filter (may be "%")</li>
     *   <li><code>bills</code> (List): Private billing records matching the provider filter</li>
     * </ul>
     *
     * <p>Forwards to <code>billing/CA/BC/privateBilling/viewStatement.jsp</code> for rendering.
     *
     * @param request HttpServletRequest containing optional providerId parameter
     * @param response HttpServletResponse for the servlet response
     * @param forward String path to the JSP view (overridden to LIST_PRIVATE_BILLS)
     * @throws ServletException if the request forwarding fails
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
     * Generates print preview invoices for private billing with comprehensive patient and clinic details.
     *
     * <p>This method processes JSON-formatted billing IDs to create invoice previews containing:
     * <ul>
     *   <li>Patient demographic information (name, address, date of birth)</li>
     *   <li>Recipient information (defaults to patient if no separate recipient specified)</li>
     *   <li>Clinic contact details (name, address, phone, fax)</li>
     *   <li>Invoice line items for each patient's private billing services</li>
     * </ul>
     *
     * <p><strong>Request Parameters:</strong>
     * <ul>
     *   <li><code>billIds</code> (String[]): JSON array of bill objects, each containing:
     *     <ul>
     *       <li><code>demographicNumber</code> (String): Patient's unique demographic ID</li>
     *       <li><code>recipientId</code> (String, optional): ID of billing recipient
     *           (empty string means patient is the recipient)</li>
     *     </ul>
     *   </li>
     *   <li><code>billToClinic</code> (String): Clinic billing indicator</li>
     * </ul>
     *
     * <p><strong>Request Attributes Set:</strong>
     * <ul>
     *   <li><code>date</code> (String): Current date/time for invoice timestamp</li>
     *   <li><code>billToClinic</code> (String): Clinic billing flag</li>
     *   <li><code>billIds</code> (String): Original billIds parameter</li>
     *   <li><code>patientBills</code> (List&lt;HashMap&gt;): Collection of patient billing data,
     *       each HashMap containing patient demographics, recipient details, clinic info, and invoice items</li>
     * </ul>
     *
     * <p><strong>JSON Processing:</strong>
     * The method expects a single-element String array containing a JSON array. Each JSON object
     * in the array represents one patient's bill with demographic and optional recipient information.
     *
     * <p><strong>Recipient Logic:</strong>
     * If recipientId is null or empty, the patient is used as the recipient. Otherwise,
     * recipient information is fetched from the database using the provided recipientId.
     *
     * <p>Forwards to <code>billing/CA/BC/privateBilling/printPreview.jsp</code> for rendering.
     *
     * @param request HttpServletRequest containing billIds JSON array and billToClinic flag
     * @param response HttpServletResponse for the servlet response
     * @param forward String path to the JSP view (overridden to PRINT_PREVIEW_BILLS)
     * @throws ServletException if the request forwarding fails
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
     *
     * <p>This method routes requests to the appropriate handler based on the
     * <code>action</code> request parameter. It supports the following actions:
     * <ul>
     *   <li><code>listPrivateBills</code>: Displays a list of private bills with provider filtering</li>
     *   <li><code>printPreviewBills</code>: Generates invoice preview with patient/clinic details</li>
     *   <li>No action or unknown action: Defaults to <code>listPrivateBills</code></li>
     * </ul>
     *
     * <p><strong>Request Parameters:</strong>
     * <ul>
     *   <li><code>action</code> (String, optional): The action to perform.
     *       Case-insensitive comparison. If null or unrecognized, defaults to listing bills.</li>
     * </ul>
     *
     * <p><strong>Error Handling:</strong>
     * <ul>
     *   <li>NullPointerException: Caught when action parameter is not provided,
     *       defaults to listing private bills</li>
     *   <li>ServletException: Logged to standard error output</li>
     * </ul>
     *
     * <p><strong>Default Behavior:</strong>
     * When no action is specified or an exception occurs, the controller
     * defaults to the safe operation of listing private bills.
     *
     * @param request HttpServletRequest containing the action parameter and action-specific parameters
     * @param response HttpServletResponse for the servlet response
     * @throws ServletException if the request processing fails
     * @throws IOException if an I/O error occurs during request processing
     * @throws NullPointerException if the action parameter is null (handled internally with default behavior)
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