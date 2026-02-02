//CHECKSTYLE:OFF

package ca.openosp.openo.form;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import ca.openosp.Misc;
import ca.openosp.openo.utility.LoggedInInfo;

import ca.openosp.openo.login.DBHelp;
import ca.openosp.openo.db.DBHandler;
import ca.openosp.openo.util.UtilDateUtilities;

/**
 * British Columbia INR (International Normalized Ratio) Form Record Handler.
 * 
 * <p>This class manages the BC-specific INR monitoring form used for tracking anticoagulant therapy.
 * INR is a standardized blood test measurement used to monitor patients on anticoagulant medications
 * such as Warfarin (Coumadin). The form tracks patient demographics, INR test results, dosage changes,
 * and follow-up scheduling for patients requiring long-term anticoagulation therapy.</p>
 * 
 * <p>The form supports:</p>
 * <ul>
 *   <li>Multiple INR test result entries (up to 21 historical records)</li>
 *   <li>Patient demographic information synchronized with the demographic table</li>
 *   <li>Integration with HL7 laboratory results (BCP lab type)</li>
 *   <li>Tracking of medication dosages and changes</li>
 *   <li>Follow-up date scheduling and patient notification tracking</li>
 * </ul>
 * 
 * <p>This form is specific to British Columbia's healthcare system and stores data in the 
 * {@code formBCINR} database table. It integrates with the provincial lab information system
 * to automatically retrieve INR test results from HL7 messages.</p>
 * 
 * @see FrmRecord
 * @see FrmRecordHelp
 * @see ca.openosp.openo.form.data.FrmData
 * @since 2026-01-14
 */
public class FrmBCINRRecord extends FrmRecord {
    private String _dateFormat = "dd/MM/yyyy";

    /**
     * Retrieves form record data for the BC INR form.
     * 
     * <p>This method retrieves either a new form populated with patient demographic data
     * or an existing form record from the database. When creating a new form (existingID &lt;= 0),
     * it populates the form with current patient demographic information. When loading an
     * existing form, it retrieves the stored form data and also fetches current demographic
     * information for comparison purposes (displayed with "_cur" suffix properties).</p>
     * 
     * <p>The method populates a Properties object with form field values including:</p>
     * <ul>
     *   <li>Patient demographics (name, address, phone, PHN)</li>
     *   <li>Date of birth and form creation date</li>
     *   <li>Historical INR test results and medication dosages</li>
     *   <li>Current demographic information (for existing forms) to detect changes</li>
     * </ul>
     * 
     * @param loggedInInfo LoggedInInfo the current user's login session information
     * @param demographicNo int the unique identifier of the patient
     * @param existingID int the unique identifier of an existing form record, or 0 or negative for a new form
     * @return Properties object containing all form field names and values
     * @throws SQLException if a database access error occurs during form data retrieval
     */
    public Properties getFormRecord(LoggedInInfo loggedInInfo, int demographicNo, int existingID) throws SQLException {
        Properties props = new Properties();

        if (existingID <= 0) {

            String sql = "SELECT demographic_no, last_name, first_name, sex, address, city, province, postal, phone, phone2, year_of_birth, month_of_birth, date_of_birth, hin, family_doctor FROM demographic WHERE demographic_no = "
                    + demographicNo;
            ResultSet rs = DBHandler.GetSQL(sql);
            if (rs.next()) {
                java.util.Date date = UtilDateUtilities.calcDate(Misc.getString(rs, "year_of_birth"), rs
                        .getString("month_of_birth"), Misc.getString(rs, "date_of_birth"));
                props.setProperty("demographic_no", Misc.getString(rs, "demographic_no"));
                props
                        .setProperty("formCreated", UtilDateUtilities.DateToString(new Date(),
                                _dateFormat));
                // props.setProperty("formEdited",
                // UtilDateUtilities.DateToString(new Date(),_dateFormat));
                props.setProperty("c_surname", Misc.getString(rs, "last_name"));
                props.setProperty("c_givenName", Misc.getString(rs, "first_name"));
                props.setProperty("c_address", Misc.getString(rs, "address"));
                props.setProperty("c_city", Misc.getString(rs, "city"));
                props.setProperty("c_province", Misc.getString(rs, "province"));
                props.setProperty("c_postal", Misc.getString(rs, "postal"));
                props.setProperty("c_phn", Misc.getString(rs, "hin"));
                props.setProperty("c_dateOfBirth", UtilDateUtilities.DateToString(date, _dateFormat));
                props.setProperty("c_phone1", Misc.getString(rs, "phone"));
                props.setProperty("c_phone2", Misc.getString(rs, "phone2"));
            }
            rs.close();
        } else {
            String sql = "SELECT * FROM formBCINR WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
            FrmRecordHelp frh = new FrmRecordHelp();
            frh.setDateFormat(_dateFormat);
            props = (frh).getFormRecord(sql);

            sql = "SELECT last_name, first_name, address, city, province, postal, phone,phone2, hin FROM demographic WHERE demographic_no = "
                    + demographicNo;
            DBHelp db = new DBHelp();
            ResultSet rs = DBHelp.searchDBRecord(sql);
            if (rs.next()) {
                props.setProperty("c_surname_cur", Misc.getString(rs, "last_name"));
                props.setProperty("c_givenName_cur", Misc.getString(rs, "first_name"));
                props.setProperty("c_address_cur", Misc.getString(rs, "address"));
                props.setProperty("c_city_cur", Misc.getString(rs, "city"));
                props.setProperty("c_province_cur", Misc.getString(rs, "province"));
                props.setProperty("c_postal_cur", Misc.getString(rs, "postal"));
                props.setProperty("c_phn_cur", Misc.getString(rs, "hin"));
                props.setProperty("c_phone1_cur", Misc.getString(rs, "phone"));
                props.setProperty("c_phone2_cur", Misc.getString(rs, "phone2"));
            }
        }
        return props;
    }

    /**
     * Retrieves the most recent lab date from the patient's INR form history.
     * 
     * <p>This method searches through up to 21 historical INR test date entries (date1 through date21)
     * in the patient's form record and returns the most recent valid date. The search is performed
     * in reverse chronological order (from date21 down to date1) to optimize performance by finding
     * the latest entry first.</p>
     * 
     * <p>The method handles both new forms (existingID = 0) and existing forms. For new forms,
     * it retrieves the most recent form ID for the patient. If no valid date is found in the
     * form history, it returns a default date of "20/04/2002".</p>
     * 
     * @param demographicNo int the unique identifier of the patient
     * @param existingID int the unique identifier of an existing form, or 0 to retrieve the most recent form
     * @return String the most recent lab date in dd/MM/yyyy format, or "20/04/2002" if no dates found
     * @throws SQLException if a database access error occurs during date retrieval
     */
    public String getLastLabDate(int demographicNo, int existingID) throws SQLException {
        String ret = "20/04/2002";
        Properties props = new Properties();
        int cId = 0;
        if (existingID == 0) {
            String sql = "SELECT ID FROM formBCINR WHERE demographic_no = " + demographicNo + " order by ID desc";
            ResultSet rs = DBHelp.searchDBRecord(sql);
            if (rs.next()) {
                cId = rs.getInt("ID");
            }
        } else {
            cId = existingID;
        }

        if (cId != 0) {
            String sql = "SELECT * FROM formBCINR WHERE demographic_no = " + demographicNo + " AND ID = " + cId;
            FrmRecordHelp frh = new FrmRecordHelp();
            frh.setDateFormat(_dateFormat);
            props = (frh).getFormRecord(sql);

            for (int i = 21; i >= 1; i--) {
                String labDate = props.getProperty("date" + i, "");
                if (labDate.length() == 10) {
                    ret = labDate;
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * Retrieves INR laboratory test results from HL7 messages for a patient.
     * 
     * <p>This method queries the provincial laboratory information system to retrieve INR
     * (International Normalized Ratio) test results that have been received via HL7 messages.
     * It specifically searches for BCP (British Columbia Provincial) lab type results and
     * extracts INR test values from the HL7 OBX (Observation Result) segments.</p>
     * 
     * <p>The method performs the following operations:</p>
     * <ul>
     *   <li>Queries patientLabRouting table for BCP lab messages for the patient</li>
     *   <li>Retrieves corresponding HL7 OBR (Observation Request) records</li>
     *   <li>Extracts OBX records with observation identifier "INR" and status "F" (Final)</li>
     *   <li>Formats observation dates from HL7 timestamp format (YYYYMMDDHHMMSS) to dd/MM/yyyy</li>
     *   <li>Returns alternating date and result value pairs in a Vector</li>
     * </ul>
     * 
     * <p>The returned Vector contains data in pairs: [date1, result1, date2, result2, ...]
     * where dates are formatted as dd/MM/yyyy strings and results are the INR values.</p>
     * 
     * @param demographic_no int the unique identifier of the patient
     * @return Vector containing alternating date (String) and INR result (String) values from HL7 messages
     * @throws SQLException if a database access error occurs during HL7 data retrieval
     */
    public Vector getINRLabData(int demographic_no) throws SQLException {
        Vector ret = new Vector();

        String sql = "select lab_no from patientLabRouting where lab_type = 'BCP' and demographic_no ="
                + demographic_no + "  order by lab_no";
        ResultSet rs = DBHandler.GetSQL(sql);
        while (rs.next()) {
            int labNo = rs.getInt("lab_no");
            sql = "select obr_id from hl7_obr obr, hl7_pid pid where obr.pid_id = pid.pid_id and pid.message_id ="
                    + labNo;
            ResultSet rs1 = DBHandler.GetSQL(sql);
            if (rs1.next()) {
                int obrId = rs1.getInt("obr_id");
                sql = "select observation_identifier, observation_results, observation_date_time from hl7_obx where obr_id ="
                        + obrId + " and observation_result_status='F'";
                ResultSet rs2 = DBHandler.GetSQL(sql);
                while (rs2.next()) {
                    String labTestName = rs2.getString("observation_identifier").substring(
                            rs2.getString("observation_identifier").indexOf("^") + 1);
                    if ("INR".equals(labTestName)) {
                        String result = rs2.getString("observation_results");
                        String lTimeStamp = rs2.getString("observation_date_time");
                        lTimeStamp = lTimeStamp.length() > 12 ? lTimeStamp.substring(0, 10) : lTimeStamp;
                        lTimeStamp = lTimeStamp.substring(8, 10) + "/" + lTimeStamp.substring(5, 7) + "/"
                                + lTimeStamp.substring(0, 4);
                        ret.add(lTimeStamp);
                        ret.add(result);
                    }
                }
            }
        }

        rs.close();
        return ret;
    }

    /**
     * Saves a BC INR form record to the database.
     * 
     * <p>This method persists the form data to the {@code formBCINR} table. It creates
     * a new record in the database using the form field values provided in the Properties
     * object. The method delegates the actual save operation to FrmRecordHelp, which
     * handles the SQL INSERT operation.</p>
     * 
     * <p>The Properties object should contain all form field values including patient
     * demographics, INR test results, medication dosages, and follow-up information.
     * The demographic_no property is required to associate the form with the correct patient.</p>
     * 
     * @param props Properties object containing all form field names and values to be saved
     * @return int the unique identifier (ID) of the newly created form record
     * @throws SQLException if a database access error occurs during the save operation
     */
    public int saveFormRecord(Properties props) throws SQLException {
        String demographic_no = props.getProperty("demographic_no");
        String sql = "SELECT * FROM formBCINR WHERE demographic_no=" + demographic_no + " AND ID=0";

        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).saveFormRecord(props, sql));
    }

    /**
     * Retrieves a BC INR form record formatted for printing.
     * 
     * <p>This method retrieves an existing form record from the database specifically
     * formatted for printing or PDF generation. It fetches the complete form data
     * including all patient demographics, INR test results, medication information,
     * and historical tracking data.</p>
     * 
     * <p>The method queries the {@code formBCINR} table using both the patient's
     * demographic number and the specific form ID to ensure the correct record is
     * retrieved. The returned Properties object contains all form fields with values
     * formatted according to the class's date format (dd/MM/yyyy).</p>
     * 
     * @param demographicNo int the unique identifier of the patient
     * @param existingID int the unique identifier of the form record to retrieve
     * @return Properties object containing all form field names and values formatted for printing
     * @throws SQLException if a database access error occurs during record retrieval
     */
    public Properties getPrintRecord(int demographicNo, int existingID) throws SQLException {
        String sql = "SELECT * FROM formBCINR WHERE demographic_no = " + demographicNo + " AND ID = " + existingID;
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).getPrintRecord(sql));
    }

    /**
     * Determines the action type from a form submission button value.
     * 
     * <p>This method parses the submit button value from a form submission to determine
     * what action the user requested (e.g., "Save", "Print", "Submit"). The method
     * delegates to FrmRecordHelp which handles the parsing of standard form action
     * button values and returns a normalized action string.</p>
     * 
     * <p>Common action values include:</p>
     * <ul>
     *   <li>"save" - Save the form data</li>
     *   <li>"print" - Print or generate PDF</li>
     *   <li>"submit" - Submit the form</li>
     *   <li>"exit" - Exit without saving</li>
     * </ul>
     * 
     * @param submit String the raw submit button value from the form submission
     * @return String the normalized action value indicating the requested operation
     * @throws SQLException if a database access error occurs during action processing
     */
    public String findActionValue(String submit) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).findActionValue(submit));
    }

    /**
     * Constructs the navigation URL for form actions.
     * 
     * <p>This method generates the appropriate URL for navigating after a form action
     * is completed. The URL construction depends on the context (where the form was
     * accessed from), the action performed, the patient's demographic ID, and the
     * form ID.</p>
     * 
     * <p>The method delegates to FrmRecordHelp which handles the standard URL
     * construction pattern for form navigation, including:</p>
     * <ul>
     *   <li>Returning to the patient's chart or encounter</li>
     *   <li>Opening the print view</li>
     *   <li>Navigating to form listing</li>
     *   <li>Redirecting to the appropriate module based on context</li>
     * </ul>
     * 
     * @param where String the context or location from which the form was accessed
     * @param action String the action that was performed (save, print, submit, etc.)
     * @param demoId String the patient's demographic ID as a string
     * @param formId String the form record ID as a string
     * @return String the complete URL for navigation after the form action
     * @throws SQLException if a database access error occurs during URL construction
     */
    public String createActionURL(String where, String action, String demoId, String formId) throws SQLException {
        FrmRecordHelp frh = new FrmRecordHelp();
        frh.setDateFormat(_dateFormat);
        return ((frh).createActionURL(where, action, demoId, formId));
    }

}
