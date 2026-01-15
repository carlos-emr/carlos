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


package ca.openosp.openo.billings.ca.bc.Teleplan;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.NameValuePair;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.ContentType;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.OscarProperties;

/**
 * @author jay
 */
public class TeleplanAPI {
    static Logger log = MiscUtils.getLogger();

    public static String ExternalActionLogon = "AsignOn";
    public static String ExternalActionLogoff = "AsignOff";
    public static String ExternalActionChangePW = "AchangePW";
    public static String ExternalActionGetLog = "AgetLog";
    public static String ExternalActionGetLogList = "AgetLogList";
    public static String ExternalActionGetRemit = "AgetRemit";
    public static String ExternalActionGetAscii = "AgetAscii";
    public static String ExternalActionGetAsciiMF = "AgetAsciiMF";
    public static String ExternalActionPutAscii = "AputAscii";
    public static String ExternalActionPutRemit = "AputRemit";
    public static String ExternalActionCheckE45 = "AcheckE45";


    //public String CONTACT_URL = "https://tlpt2.moh.hnet.bc.ca/TeleplanBroker";
    public String CONTACT_URL = "https://teleplan.hnet.bc.ca/TeleplanBroker";

    private CloseableHttpClient httpclient = null;
    private HttpClientContext httpContext = null;

    /**
     * Creates a new instance of TeleplanAPI
     */
    public TeleplanAPI() {
        getClient();
    }

    public TeleplanAPI(String username, String password) {
        getClient();

    }

    private void getClient() {
        CONTACT_URL = OscarProperties.getInstance().getProperty("TELEPLAN_URL", CONTACT_URL);

        BasicCookieStore cookieStore = new BasicCookieStore();
        BasicClientCookie cookie = new BasicClientCookie("mycookie", "stuff");
        cookie.setDomain("moh.hnet.bc.ca");
        cookie.setPath("/");
        cookieStore.addCookie(cookie);

        httpContext = HttpClientContext.create();
        httpContext.setCookieStore(cookieStore);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(30_000)
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent("TeleplanPerl 1.0")
                .build();
    }

    private TeleplanResponse processRequest(String url, List<NameValuePair> data) {
        TeleplanResponse tr = null;
        try {
            HttpPost post = new HttpPost(url);
            post.setEntity(new UrlEncodedFormEntity(data, "UTF-8"));
            try (CloseableHttpResponse response = httpclient.execute(post)) {
                InputStream in = response.getEntity().getContent();
                tr = new TeleplanResponse();
                tr.processResponseStream(in);
                TeleplanResponseDAO trDAO = new TeleplanResponseDAO();
                trDAO.save(tr);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return tr;
    }

    private TeleplanResponse processRequest(String url, Map<String, Object> parts) {
        TeleplanResponse tr = null;
        try {
            HttpPost post = new HttpPost(url);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Map.Entry<String, Object> entry : parts.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof File) {
                    builder.addBinaryBody(name, (File) value, ContentType.APPLICATION_OCTET_STREAM, ((File) value).getName());
                } else if (value instanceof String) {
                    builder.addTextBody(name, (String) value, ContentType.TEXT_PLAIN);
                }
            }

            post.setEntity(builder.build());

            try (CloseableHttpResponse response = httpclient.execute(post)) {
                InputStream in = response.getEntity().getContent();
                tr = new TeleplanResponse();
                tr.processResponseStream(in);
                new TeleplanResponseDAO().save(tr);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return tr;
    }

    //////////
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: userid, password, new password, new password
     * Parameters to TeleplanBroker are
     * ExternalAction = "AchangePW"
     * username         = $uid
     * password         = $pw
     * new.password     = $chgpw1
     * confirm.password = $chgpw2
     * Results from TeleplanBroker are:
     * "SUCCESS" Password was changed successfully
     * "FAILURE" Password was not changed
     */
    public TeleplanResponse changePassword(String username, String password, String newPassword, String confirmPassword) {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("username", username));
        data.add(new BasicNameValuePair("password", password));
        data.add(new BasicNameValuePair("new.password", newPassword));
        data.add(new BasicNameValuePair("confirm.password", confirmPassword));
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionChangePW));

        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: userid, password
     * Parameters to TeleplanBroker are
     * ExternalAction = "AsignOn"
     * Username = $uid
     * password = $pw
     * Results from TeleplanBroker are:
     * "SUCCESS" for valid logon
     * "FAILURE" for invalid logon
     * "EXPIRED.PASSWORD" for valid username/password, user must change PW
     * before the application will return a SUCCESS
     */
    public TeleplanResponse login(String username, String password) {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("username", username));
        data.add(new BasicNameValuePair("password", password));
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionLogon));

        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters:none
     * Parameters to TeleplanBroker are
     * ExternalAction = "AsignOff"
     * Results from TeleplanBroker are: "SUCCESS" for valid logoff
     */
    public TeleplanResponse logoff() {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionLogoff));

        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: LogType which can be "T" or "L" or "S"
     */
    public TeleplanResponse getCurrentLog(String logtype) {
        return getLog(logtype, "CURRENT");
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: LogType, LogName
     * Parameters to TeleplanBroker are
     * LOGNAME = Name representing an actual log file (from getLogList) or "CURRENT"
     * ExternalAction = "AgetLog"
     * LOGTYPE = "T" or "L" or "S"
     * MODE = "DOWNLOAD"
     * Results from TeleplanBroker are:
     * "SUCCESS" for valid download
     * "FAILURE" for problem
     */
    public TeleplanResponse getLog(String logname, String logtype) {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("LOGNAME", logname));
        data.add(new BasicNameValuePair("LOGTYPE", logtype));
        data.add(new BasicNameValuePair("MODE", "DOWNLOAD")); 
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionGetLog));
        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: None
     * Parameters to TeleplanBroker are
     * ExternalAction = "AgetLogList"
     * Results from TeleplanBroker are:
     * "SUCCESS"
     * "FAILURE"
     * This will return a file contain a list (1 logname/line) of logs
     * the user can download
     * The format of the logname is: 001E2805.LOG
     * To convert this to the same style as the www download name you must
     * extract the century from the first character (0..Z 0=20, Z=56)
     * extract the Month which is the 4th character (A (Jan)..L(Dec))
     * Then you would create the filename CCYY_MM_DD_Gdd.LOG
     * The filename 001E2805.LOG would convert to 2001_5_28_G05.LOG
     */
    public TeleplanResponse getLogList() {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionGetLogList));
        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: include remittance which can be either "true" or "false"
     * Parameters to TeleplanBroker are
     * remittance = $includeRemit which can be "true" or "false"
     * ExternalAction = "AgetRemit"
     * Results from TeleplanBroker are:
     * "SUCCESS"
     * "FAILURE"
     */
    public TeleplanResponse getRemittance(boolean includeRemittance) {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("remittance", Boolean.toString(includeRemittance)));
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionGetRemit));
        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: File Type, which is outlined below
     * Vendors use this section and command to obtain Other Processing files
     * Parameters to TeleplanBroker are
     * filechar = $filetype which can be:
     * V = VENDORS TEST SELECTION ONLY
     * I = MSP ICD9 Codes (4 and 3 some 5)
     * D = Diagnostic Facility's Codes
     * G = Geographic Differential Payment Codes
     * R = Rural Retention Premium Codes
     * ? = List of Services Available
     * 9 = MSP Technical Use ONLY
     * 3 = MSP Fee Schedule Costs
     * 2 = MSP ICD9 Codes (3 char)
     * 1 = MSP Explanatory Codes List
     * 0 = Get list of valid codes
     * ExternalAction = "AgetAscii"
     * Results from TeleplanBroker are:
     * "SUCCESS"
     * "FAILURE"
     */
    public TeleplanResponse getAsciiFile(String filetype) {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("filechar", filetype));
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionGetAscii));
        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: File Type which is outlined below
     * #Parameters to TeleplanBroker are
     * This section and command restricted to MoH Internal staff access
     * filechar = $filetype which can be:
     * V = VENDORS TEST SELECTION ONLY
     * I = MSP ICD9 Codes (4 and 3 some 5)
     * D = Diagnostic Facility's Codes
     * G = Geographic Differential Payment Codes
     * R = Rural Retention Premium Codes
     * ? = List of Services Available
     * 9 = MSP Technical Use ONLY
     * 3 = MSP Fee Schedule Costs
     * 2 = MSP ICD9 Codes (3 char)
     * 1 = MSP Explanatory Codes List
     * ExternalAction = "AgetAsciiMF"
     * Results from TeleplanBroker are:
     * "SUCCESS"
     * "FAILURE"
     */
    public TeleplanResponse getAsciiFileMF(String filetype) {
        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("filechar", filetype));
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionGetAsciiMF));
        return processRequest(CONTACT_URL, data);
    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: Filename which is a string representing a file on the local system
     * Parameters to TeleplanBroker are
     * ExternalAction = "AputAscii"
     * This is a RFC1867 Multi-part post
     * Results from TeleplanBroker are:
     * "SUCCESS"
     * "FAILURE"
     */
    public TeleplanResponse putAsciiFile(File f) throws FileNotFoundException {
        Map<String, Object> parts = new HashMap<>();
        parts.put("ExternalAction", "AputAscii");
        parts.put("submitASCII", f);
        return processRequest(CONTACT_URL, parts);

//    my ($filename) = @_;
//    my $request = POST $WEBBASE, Content_type => 'form-data',
//                                 Content      => ['submitASCII'    => [ $filename ], 
//                                                  'ExternalAction' => 'AputAscii'
//                                                 ];
//    my $retVal = processRequest($request);	
//    if ($retVal == $SUCCESS)
//    {#valid response
//       if ($Result ne "SUCCESS")
//       {
//           $retVal = $VALID;
//       }
//    }
//    else
//    {
//       $retVal = $ERROR;
//    }
//    return $retVal;

    }
    //-------------------------------------------------------------------------

    /**
     * Procedure parameters: Filename which is a string representing a file on the local system
     * Parameters to TeleplanBroker are
     * ExternalAction = "AputRemit"
     * This is a RFC1867 Multi-part post
     * Results from TeleplanBroker are:
     * "SUCCESS"
     * "FAILURE"
     */
    public TeleplanResponse putMSPFile(File f) throws FileNotFoundException {
        Map<String, Object> parts = new HashMap<>();
        parts.put("ExternalAction", "AputRemit");
        parts.put("submitFile", f);
        return processRequest(CONTACT_URL, parts);
//
//    my ($filename) = @_;
//    my $request = POST $WEBBASE, Content_Type => 'form-data',
//                                 Content      => ['submitFile'    => [ $filename ], 
//                                                  'ExternalAction' => 'AputRemit'
//                                                 ];
//    my $retVal = processRequest($request);	
//    if ($retVal == $SUCCESS)
//    {#valid response
//       if ($Result ne "SUCCESS")
//       {
//           $retVal = $VALID;
//       }
//    }
//    else
//    {
//       $retVal = $ERROR;
//    }
//    return $retVal;
//            return null;
    }
    //-------------------------------------------------------------------------

    /**
     * Note: Internal field names(Patient Visit Charge) reflect Subsidy Insured Service
     * We cannot change Internal modules but you may reflect externally, see Browser screens
     * <p>
     * Procedure parameters and TeleplanBroker inputs:
     * PHN	  	       	= $phn			(string representing valid PHN Number)
     * dateOfBirthyyyy	= $dateofbirthyyyy	(string denoting numeric year)
     * dateOfBirthmm		= $dateofbirthmm	(string denoting numeric month)
     * dateOfBirthdd		= $dateofbirthdd	(string denoting numeric day)
     * dateOfServiceyyyy	= $dateofserviceyyyy	(string denoting year)
     * dateOfServicemm	= $dateofservicemm	(string denoting numeric month)
     * dateOfServicedd	= $dateofservicedd	(string denoting numeric day)
     * PatientVisitCharge	= $patientvisitcharge	(string representing a boolean true/false)
     * LastEyeExam		= $lasteyeexam		(string representing a boolean true/false)
     * PatientRestriction 	= $patientrestriction	(string representing a boolean true/false)
     * ExternalAction 	= "AcheckE45"
     * Results from TeleplanBroker are:
     * Still to be determined but "SUCCESS" and "FAILURE" will be returned
     */
    public TeleplanResponse checkElig(String phn, String dateofbirthyyyy, String dateofbirthmm, String dateofbirthdd,
                                      String dateofserviceyyyy, String dateofservicemm, String dateofservicedd,
                                      boolean patientvisitcharge, boolean lasteyeexam, boolean patientrestriction) {

        List<NameValuePair> data = new ArrayList<>();
        data.add(new BasicNameValuePair("PHN", phn));
        data.add(new BasicNameValuePair("dateOfBirthyyyy", dateofbirthyyyy));
        data.add(new BasicNameValuePair("dateOfBirthmm", dateofbirthmm));
        data.add(new BasicNameValuePair("dateOfBirthdd", dateofbirthdd));
        data.add(new BasicNameValuePair("dateOfServiceyyyy", dateofserviceyyyy));
        data.add(new BasicNameValuePair("dateOfServicemm", dateofservicemm));
        data.add(new BasicNameValuePair("dateOfServicedd", dateofservicedd));
        data.add(new BasicNameValuePair("PatientVisitCharge", Boolean.toString(patientvisitcharge)));
        data.add(new BasicNameValuePair("LastEyeExam", Boolean.toString(lasteyeexam)));
        data.add(new BasicNameValuePair("PatientRestriction", Boolean.toString(patientrestriction)));
        data.add(new BasicNameValuePair("ExternalAction", ExternalActionCheckE45));
        return processRequest(CONTACT_URL, data);
    }
//-------------------------------------------------------------------------

    ///////////
}
