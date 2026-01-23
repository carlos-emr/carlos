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


package ca.openosp.openo.demographic.pageUtil;

import ca.openosp.openo.casemgmt.model.*;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.hospitalReportManager.dao.*;
import ca.openosp.openo.hospitalReportManager.model.*;
import cds.AlertsAndSpecialNeedsDocument.AlertsAndSpecialNeeds;
import cds.AllergiesAndAdverseReactionsDocument.AllergiesAndAdverseReactions;
import cds.AppointmentsDocument.Appointments;
import cds.CareElementsDocument.CareElements;
import cds.ClinicalNotesDocument.ClinicalNotes;
import cds.DemographicsDocument.Demographics;
import cds.DemographicsDocument.Demographics.Enrolment.EnrolmentHistory;
import cds.DemographicsDocument.Demographics.Enrolment.EnrolmentHistory.EnrolledToPhysician;
import cds.DemographicsDocument.Demographics.PreferredPharmacy;
import cds.FamilyHistoryDocument.FamilyHistory;
import cds.ImmunizationsDocument.Immunizations;
import cds.LaboratoryResultsDocument.LaboratoryResults;
import cds.LaboratoryResultsDocument.LaboratoryResults.ResultReviewer;
import cds.MedicationsAndTreatmentsDocument.MedicationsAndTreatments;
import cds.NewCategoryDocument.NewCategory;
import cds.OmdCdsDocument;
import cds.PastHealthDocument.PastHealth;
import cds.PatientRecordDocument.PatientRecord;
import cds.PersonalHistoryDocument.PersonalHistory;
import cds.ProblemListDocument.ProblemList;
import cds.ReportsDocument.Reports;
import cds.ReportsDocument.Reports.OBRContent;
import cds.ReportsDocument.Reports.ReportReviewed;
import cds.ReportsDocument.Reports.SourceAuthorPhysician;
import cds.RiskFactorsDocument.RiskFactors;
import cdsDt.*;
import cdsDt.DiabetesComplicationScreening.ExamCode;
import cdsDt.DiabetesMotivationalCounselling.CounsellingPerformed;
import cdsDt.PersonNameStandard.LegalName;
import cdsDt.PersonNameStandard.OtherNames;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.codehaus.jettison.json.JSONException;
import org.owasp.encoder.Encode;
import org.codehaus.jettison.json.JSONObject;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.casemgmt.dao.IssueDAO;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.hospitalReportManager.HRMReport;
import ca.openosp.openo.hospitalReportManager.HRMReportParser;
import ca.openosp.openo.managers.NioFileManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SessionConstants;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.webserv.LabUploadWs;
import ca.openosp.OscarProperties;
import ca.openosp.openo.demographic.data.DemographicAddResult;
import ca.openosp.openo.demographic.data.DemographicData;
import ca.openosp.openo.demographic.data.DemographicRelationship;
import ca.openosp.openo.encounter.data.EctProgram;
import ca.openosp.openo.encounter.oscarMeasurements.data.ImportExportMeasurements;
import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.LabRequestReportLink;
import ca.openosp.openo.lab.ca.all.Hl7textResultsData;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.CMLHandler;
import ca.openosp.openo.lab.ca.all.upload.handlers.ExcellerisOntarioHandler;
import ca.openosp.openo.lab.ca.all.upload.handlers.GDMLHandler;
import ca.openosp.openo.lab.ca.all.upload.handlers.MDSHandler;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;
import ca.openosp.openo.lab.ca.all.upload.handlers.PATHL7Handler;
import ca.openosp.openo.lab.ca.all.util.Utilities;
import ca.openosp.openo.prevention.PreventionData;
import ca.openosp.openo.providers.data.ProviderData;
import ca.openosp.openo.prescript.data.RxDrugData;
import ca.openosp.openo.prescript.data.RxDrugData.DrugMonograph;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.StringUtils;
import ca.openosp.openo.util.UtilDateUtilities;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportDemographicDataAction42Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final Logger logger = MiscUtils.getLogger();
    private static final String PATIENTID = "Patient";
    private static final String ALERT = "Alert";
    private static final String ALLERGY = "Allergy";
    private static final String APPOINTMENT = "Appointment";
    private static final String CAREELEMENTS = "Care";
    private static final String CLINICALNOTE = "Clinical";
    private static final String FAMILYHISTORY = "Family";
    private static final String IMMUNIZATION = "Immunization";
    private static final String LABS = "Labs";
    private static final String MEDICATION = "Medication";
    private static final String PASTHEALTH = "Past";
    private static final String PERSONALHISTORY = "Personal";
    private static final String PROBLEMLIST = "Problem";
    private static final String REPORTBINARY = "Binary";
    private static final String REPORTTEXT = "Text";
    private static final String RISKFACTOR = "Risk";
    private static String currentDirectory;
    private static List<Path> validXmlFileList = Collections.emptyList();

    String admProviderNo = null;
    Demographic demographic = null;
    String demographicNo = null;
    String patientName = null;
    String programId = null;
    HashMap<String, Integer> entries = new HashMap<String, Integer>();
    Integer importNo = 0;
    OscarProperties oscarProperties = OscarProperties.getInstance();
    List<String> importErrors = new ArrayList<String>();

    ProgramManager programManager = (ProgramManager) SpringUtils.getBean(ProgramManager.class);
    AdmissionManager admissionManager = (AdmissionManager) SpringUtils.getBean(AdmissionManager.class);
    AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
    CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
    DrugDao drugDao = (DrugDao) SpringUtils.getBean(DrugDao.class);
    DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);
    DemographicArchiveDao demoArchiveDao = (DemographicArchiveDao) SpringUtils.getBean(DemographicArchiveDao.class);
    ProviderDataDao providerDataDao = (ProviderDataDao) SpringUtils.getBean(ProviderDataDao.class);
    PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);
    DemographicExtDao demographicExtDao = (DemographicExtDao) SpringUtils.getBean(DemographicExtDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    PatientLabRoutingDao patientLabRoutingDao = SpringUtils.getBean(PatientLabRoutingDao.class);
    ProviderLabRoutingDao providerLabRoutingDao = SpringUtils.getBean(ProviderLabRoutingDao.class);
    MeasurementsExtDao measurementsExtDao = SpringUtils.getBean(MeasurementsExtDao.class);
    IssueDAO issueDao = SpringUtils.getBean(IssueDAO.class);
    DemographicContactDao contactDao = (DemographicContactDao) SpringUtils.getBean(DemographicContactDao.class);

    private final NioFileManager nioFileManager = SpringUtils.getBean(NioFileManager.class);

    private LabUploadWs labUpload = new LabUploadWs();

    @Override
    public String execute() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        //TODO: More thought needs to be put into the user interface. Extra attention on multithreading is advised
        //Status Quo of as 2024.02.03
        // Importing single XML patient files (with embedded HL7 and PDFs) does work ok (most recently done with an Avaros export 2024.02.03)
        // Importing multiple XML patient files also works ok (most recently done with ~500 Avaros exports 2024.02.03 amounting to 18GB in approximately 5 hours)
        // The batch uploading is currently done via a javascript loop which uploads and processes each file, one at a time.  The browser window cannot be closed or navigated away during uploading.
        // The status of each file is individually reported back to the front end.
        // HOWEVER, if OSCAR session expires or after the user navigates away from the upload page, the whole process is lost.
        // To help overcome interuptions, consider reworking process to upload files quickly to temporary folder before batch back end procesing and reporting of status

        // initialize
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        
        admProviderNo = (String) request.getSession().getAttribute("user");
        programId = new EctProgram(request.getSession()).getProgram(admProviderNo);
        matchProviderNames = this.isMatchProviderNames();
        ArrayList<String> warnings = new ArrayList<>();
        ArrayList<String[]> logs = new ArrayList<>();
        validXmlFileList = new ArrayList<>();
        String[] logResult;

        /*
         * get filename, filetype, and input stream of the import; then
         * save the upload stream to a temp directory.  This should allow the HTTP
         * thread to close gracefully while the import is being processed.
         */
        String filename = importFileFileName;
        Path filePath = importFile.toPath().normalize();

        // Get context of the temp directory, get the file path to the the temp directory
        ServletContext servletContext = ServletActionContext.getServletContext();

        // Validate the paths using PathValidationUtils
        File safeDir = (File) servletContext.getAttribute("javax.servlet.context.tempdir"); // Use a safe directory
        try {
            PathValidationUtils.validateExistingPath(filePath.toFile(), safeDir);
        } catch (SecurityException e) {
            throw new IllegalArgumentException("Invalid file path: Access outside the allowed directory is not permitted.");
        }

        int dotIndex = filename.lastIndexOf('.');
        String filetype = (dotIndex == -1) ? "" : filename.substring(dotIndex + 1).toLowerCase();
        Path directory;
        try (InputStream inputStream = Files.newInputStream(filePath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            int length;
            byte[] bytes = new byte[1024];

            // copy data from input stream to output stream
            while ((length = inputStream.read(bytes)) != -1) {
                byteArrayOutputStream.write(bytes, 0, length);
            }

            directory = nioFileManager.createTempFile(filename, byteArrayOutputStream);
        }

        /*
         * Perform a poorly understood "student" process.  Is this code block
         * still needed??  Happy to delete if it's no longer applied.
         */
        logger.debug("import to course id " + this.getCourseId() + " using timeshift value " + this.getTimeshiftInDays());
        List<Provider> students = new ArrayList<Provider>();
        int courseId = 0;
        if (this.getCourseId() != null && this.getCourseId().length() > 0) {
            courseId = Integer.parseInt(this.getCourseId());
            if (courseId > 0) {
                logger.info("need to apply this import to a learning environment");
                //get all the students from this course
                List<ProgramProvider> courseProviders = programManager.getProgramProviders(String.valueOf(courseId));
                for (ProgramProvider pp : courseProviders) {
                    if (pp.getRole().getName().equalsIgnoreCase("student")) {
                        students.add(pp.getProvider());
                    }
                }
            }
        }
        logger.debug("apply this patient to " + students.size() + " students");

        /*
         * Determine what kind of file was uploaded and process accordingly
         * Single XML File: process accordingly.
         * ZIP File: unzip into temp directory and delete source
         * Link to Directory: recurse tree and process XML
         *
         * TODO: At the moment it is expected that an XML file is an import of a single patient file.
         *  Whereas a ZIP file will contain a directory of several directories each representing
         *  a patient file and each containing a single XML File and additional PDF's
         *  Expectations of how uploaded export files are batched and formatted should be studied and standardized.
         *
         */

        // if the saved temporary file is an XML import of a single patient file, then go straight to processing
        if (filetype.contains("xml") && Files.exists(directory) && Files.isRegularFile(directory)) {
            processXmlFile(loggedInInfo, directory, warnings, logs, request, this.getTimeshiftInDays(), students, courseId);
        }

        //TODO if the saved temporary file is a zip file then go on to unzip and process the directory tree.
        // more checks and standards needed.
        else if (filetype.contains("zip") && Files.exists(directory)) {
            // unzip into parent directory
            Path rootDirectory = unzipFile(directory);
            // process starting at parent directory.
            processXmlFilesInDirectory(loggedInInfo, rootDirectory, warnings, logs, request, this.getTimeshiftInDays(), students, courseId);
        }

        // if the saved temporary file is a directory tree; then search for and process the xml file in each directory
        else if (Files.exists(directory)) {
            processXmlFilesInDirectory(loggedInInfo, directory, warnings, logs, request, this.getTimeshiftInDays(), students, courseId);
        }

        //TODO is it possible that the uploaded file is an batch file of XML files? If so, then a process is needed to
        // parse the xml batch file into individual XML patient files.


        // use the completed valid xml list to run through the contact imports.
        for (Path validXmlFile : validXmlFileList) {
            logResult = importContacts(loggedInInfo, validXmlFile.toString(), warnings, request, this.getTimeshiftInDays(), students, courseId);
            logs.add(logResult);
        }

        /*
         * a new import log gets generated into the root of the temporary directory.
         * It gets offered as a download to the end user.
         * TODO this log should be stored so that it can be retrieved later by the end user.
         */
        // Check if any files were processed before creating import log
        if (logs.isEmpty()) {
            warnings.add("No valid XML files found to import. Please check the uploaded file structure.");
            // Create empty log entry so makeImportLog doesn't crash
            logs.add(new String[]{"No files processed"});
        }

        // Save import log to servlet context temp directory (safeDir) so ImportLogDownload2Action can find it
        File importLog = makeImportLog(logs, safeDir.getCanonicalPath());

        //channel warnings and importlog to browser
        request.setAttribute("warnings", warnings);
        request.setAttribute("importlog", importLog.getPath());
        resetProviderBean(request);
        generateResponse(response, warnings, importLog.getPath());
        return SUCCESS;
    }

    private void generateResponse(HttpServletResponse response, ArrayList<String> warnings, String importLog) {
        JSONObject json = new JSONObject();
        response.setContentType("text/javascript");
        try {
            json.put("warnings", warnings);
            json.put("importLog", importLog);
            response.getWriter().write(json.toString());
        } catch (IOException | JSONException e) {
            logger.error("An error occurred while writing JSON response to the output stream", e);
        }
    }

    /**
     * Search for all XML / CDS / CMS patient files in a given directory and process.
     */
    private void processXmlFilesInDirectory(LoggedInInfo loggedInInfo, Path fileDirectory, ArrayList<String> warnings, ArrayList<String[]> logs,
                                            HttpServletRequest request, int timeshiftInDays, List<Provider> students, int courseId) throws IOException {
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(fileDirectory)) {
            for (Path stream : directoryStream) {
                if (Files.isDirectory(stream)) {
                    // set the current directory globally. Other methods use it to retrieve relative files.
                    currentDirectory = stream.toAbsolutePath().toString();

                    /* check for an XML file that matches the folder name (standard). It's best for performance
                     * to avoid hunting through the folders when not needed.
                     */
                    Path xmlFile = Paths.get(currentDirectory, stream.toFile().getName() + ".xml");
                    if (Files.exists(xmlFile)) {
                        processXmlFile(loggedInInfo, xmlFile, warnings, logs, request, timeshiftInDays, students, courseId);
                    }

                    /*
                     * otherwise hunting for a valid xml file is required. There should only be 1.
                     */
                    else {
                        List<Path> possibleXmlFileList = searchFileByExtension(stream, warnings);
                        for (Path possibleXmlFile : possibleXmlFileList) {
                            if (Files.exists(possibleXmlFile)) {
                                processXmlFile(loggedInInfo, possibleXmlFile, warnings, logs, request, timeshiftInDays, students, courseId);
                            }
                        }
                    }
                } else if (Files.isRegularFile(stream)) {
                    String filePath = stream.toString();
                    if (filePath.toLowerCase().endsWith(".xml")) {
                        // Set the current directory to the XML file's parent for attachment resolution
                        currentDirectory = stream.getParent().toAbsolutePath().toString();
                        processXmlFile(loggedInInfo, stream, warnings, logs, request, timeshiftInDays, students, courseId);
                    } else {
                        // Skip regular files (like JPG, PDF attachments) - they will be referenced by XML files
                    }
                } else {
                    // Log the actual path for debugging, but don't expose it to the user (may contain PHI)
                    logger.debug("Skipping path that is neither a file nor directory: {}", stream);
                    warnings.add("Skipped invalid path type (not a file or directory)");
                }
            }
        } catch (Exception e) {
            logger.error("Error processing XML files in directory", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Zip format only.
     * Extracts entire contents to current directory
     * TODO disk capacity should be evaluated first.
     */
    private Path unzipFile(Path zipFilePath) throws IOException {
        Path directoryPath = zipFilePath.getParent();
        File targetDir = directoryPath.toFile();

        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath.toString())))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = resolveAndValidateZipEntry(zipEntry, targetDir);
                if (newFile == null) {
                    zipEntry = zis.getNextEntry();
                    continue;
                }

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // Ensure parent directories exist (for Windows-created archives)
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create parent directory " + parent);
                    }

                    // Write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    } catch (Exception e) {
                        throw new IOException("Failed to write file: " + e.getMessage(), e);
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }

        // delete the zip file
        if (Files.exists(directoryPath)) {
            Files.deleteIfExists(zipFilePath);
        }
        return directoryPath;
    }

    /**
     * Resolves and validates a ZIP entry against the target directory.
     *
     * @param zipEntry the ZIP entry to validate
     * @param targetDir the target directory for extraction
     * @return the validated File if entry is valid, null otherwise
     */
    private File resolveAndValidateZipEntry(ZipEntry zipEntry, File targetDir) {
        String entryName = zipEntry.getName();

        // Validate ZIP entry name before processing
        if (entryName == null || entryName.trim().isEmpty()) {
            logger.error("Skipping ZIP entry with null or empty name");
            return null;
        }

        // Normalize path separators to handle cross-platform ZIP files
        String normalizedEntryName = entryName.replace("\\", "/");

        // Create file by resolving entry name against target directory (preserves directory structure)
        File newFile = new File(targetDir, normalizedEntryName);

        // CRITICAL SECURITY: Validate the resolved path is within the target directory
        // This prevents ZIP Slip attacks (e.g., "../../../etc/passwd")
        // Uses validateExistingPath for containment check without stripping path components
        try {
            // First, perform any existing validation logic
            PathValidationUtils.validateExistingPath(newFile, targetDir);

            // Explicit canonical / normalized path containment check to prevent Zip Slip
            // and to make the security property obvious to static analysis tools.
            java.nio.file.Path basePath = targetDir.getCanonicalFile().toPath().normalize();
            java.nio.file.Path resolvedPath = newFile.getCanonicalFile().toPath().normalize();
            if (!resolvedPath.startsWith(basePath)) {
                logger.error("SECURITY: ZIP entry {} resolves outside target directory {}", Encode.forJava(entryName), targetDir);
                return null;
            }

            // Also ensure the parent directory (if any) is within the target directory
            File parent = newFile.getParentFile();
            if (parent != null) {
                java.nio.file.Path parentPath = parent.getCanonicalFile().toPath().normalize();
                if (!parentPath.startsWith(basePath)) {
                    logger.error("SECURITY: Parent directory of ZIP entry {} resolves outside target directory {}", Encode.forJava(entryName), targetDir);
                    return null;
                }
            }

            // Additional defense-in-depth: keep existing helper-based checks
            if (!isWithinDirectory(newFile, targetDir)) {
                logger.error("SECURITY: ZIP entry {} resolves outside target directory according to isWithinDirectory", Encode.forJava(entryName));
                return null;
            }

            return newFile;
        } catch (IOException e) {
            logger.error("SECURITY: I/O error while validating ZIP entry {}: {}", Encode.forJava(entryName), e.getMessage(), e);
            return null;
        } catch (SecurityException e) {
            logger.error("SECURITY: Rejecting malicious ZIP entry: {}", Encode.forJava(entryName), e);
            return null;
        }
    }

    /**
     * Canonical path containment check used to prevent Zip Slip.
     * Ensures that {@code file} is equal to or a descendant of {@code baseDir}.
     *
     * @param file File the file to check
     * @param baseDir File the base directory that should contain the file
     * @return boolean true if file is within baseDir, false otherwise
     */
    private boolean isWithinDirectory(File file, File baseDir) {
        if (file == null || baseDir == null) {
            return false;
        }
        try {
            File baseCanonical = baseDir.getCanonicalFile();
            File fileCanonical = file.getCanonicalFile();
            String basePath = baseCanonical.getPath();
            String filePath = fileCanonical.getPath();

            return filePath.equals(basePath) || filePath.startsWith(basePath + File.separator);
        } catch (IOException e) {
            logger.error("Error performing canonical containment check for {}", Encode.forJava(file.getPath()), e);
            return false;
        }
    }

    /**
     * Process a single patient XML / CDS / CMS file import and add to OSCAR's database.
     */
    private void processXmlFile(LoggedInInfo loggedInInfo, Path xmlFile, ArrayList<String> warnings, ArrayList<String[]> logs, HttpServletRequest request, int timeshiftInDays, List<Provider> students, int courseId) throws Exception {
        String[] logResult = importXML(loggedInInfo, xmlFile.toString(), warnings, request, timeshiftInDays, students, courseId, false);
        validXmlFileList.add(xmlFile);
        logs.add(logResult);
    }

    private List<Path> searchFileByExtension(Path path, ArrayList<String> warnings) {
        List<Path> filteredFileList = new ArrayList<>();
        try (DirectoryStream<Path> fileList = Files.newDirectoryStream(path)) {
            for (Path file : fileList) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".xml")) {
                    filteredFileList.add(file);
                }
            }
        } catch (IOException ex) {
            // Don't expose path in user-facing warnings (may contain PHI)
            warnings.add("Error while searching for XML files in directory");
            logger.error("Error while locating .xml files in path: {}", path, ex);
        }
        return filteredFileList;
    }

//    private void saveParts(String tmpDir,String ifile) throws Exception {
//    	int len = 0;
//    	byte[] buf = new byte[1024];
//
//        ZipInputStream in = new ZipInputStream(new FileInputStream(ifile));
//        ZipEntry entry = in.getNextEntry();
//        String entryDir = "";
//
//        while (entry!=null) {
//            String entryName = entry.getName();
//            if (entry.isDirectory())
//            	entryDir = entryName;
//            if (entryName.startsWith(entryDir))
//            	entryName = entryName.substring(entryDir.length());
//
//            if(entryName.isEmpty()) {
//            	entry = in.getNextEntry();
//            	continue;
//            }
//
//
//            String ofile = tmpDir + entryDir +  entryName;
//
//            if (!matchFileExt(ofile, "xml")) {
//                OutputStream out = null;
//                try {
//                	String path = ofile.substring(0,ofile.lastIndexOf(File.separator));
//                	new File(path).mkdirs();
//                    out = new FileOutputStream(ofile);
//                    while ((len=in.read(buf)) > 0) out.write(buf,0,len);
//                    out.close();
//                } finally {
//                	IOUtils.closeQuietly(out);
//                }
//            }
//            entry = in.getNextEntry();
//        }
//        in.close();
//    }

    private void resetProviderBean(HttpServletRequest request) {
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        Properties providerBean = new Properties();
        for (Provider p : providerDao.getActiveProviders()) {
            providerBean.setProperty(p.getProviderNo(), p.getFormattedName());
        }
        request.getSession().setAttribute("providerBean", providerBean);
    }

    private String[] importContacts(LoggedInInfo loggedInInfo, String xmlFile, ArrayList<String> warnings, HttpServletRequest request, int timeShiftInDays, List<Provider> students, int courseId) throws SQLException, Exception {
        return importContacts(loggedInInfo, xmlFile, warnings, request, timeShiftInDays, null, null, 0);
    }

    private String[] importXML(LoggedInInfo loggedInInfo, String xmlFile, ArrayList<String> warnings, HttpServletRequest request, int timeShiftInDays, List<Provider> students, int courseId, boolean cleanFile) throws SQLException, Exception {
        if (students == null || students.isEmpty()) {
            return importXML(loggedInInfo, xmlFile, warnings, request, timeShiftInDays, null, null, 0, cleanFile);
        }

        List<String> logs = new ArrayList<String>();

        for (Provider student : students) {
            logger.info("importing patient for student " + student.getFormattedName());
            //need that student's personal program
            Integer pid = programManager.getProgramIdByProgramName("program" + student.getProviderNo());
            if (pid == null) {
                logger.warn("student's program not found");
                continue;
            }
            Program p = programManager.getProgram(pid);

            String[] result = importXML(loggedInInfo, xmlFile, warnings, request, timeShiftInDays, student, p, courseId, cleanFile);
            logs.addAll(convertLog(result));
        }
        return logs.toArray(new String[logs.size()]);
    }

    private List<String> convertLog(String[] logs) {
        List<String> tmp = new ArrayList<String>();
        tmp.addAll(Arrays.asList(logs));
        return tmp;
    }

    private String[] importContacts(LoggedInInfo loggedInInfo, String xmlFile, ArrayList<String> warnings, HttpServletRequest request, int timeShiftInDays, Provider student, Program admitTo, int courseId) throws SQLException, Exception {
        DemographicData dd = new DemographicData();

        String docDir = oscarProperties.getProperty("DOCUMENT_DIR");
        docDir = Util.fixDirName(docDir);
        if (!Util.checkDir(docDir)) {
            logger.debug("Error! Cannot write to DOCUMENT_DIR - Check oscar.properties or dir permissions.");
        }

        File xmlF = new File(xmlFile);
        OmdCdsDocument.OmdCds omdCds = null;
        try {
            XmlOptions opts = new XmlOptions();
            List c = new ArrayList();

            opts.setErrorListener(c);
            opts.setDocumentType(OmdCdsDocument.Factory.newInstance().schemaType());
            omdCds = OmdCdsDocument.Factory.parse(xmlF, opts).getOmdCds();

            omdCds.validate(opts);


        } catch (IOException ex) {
            logger.error("Error", ex);
        } catch (XmlException ex) {
            logger.error("Error", ex);
        }
        PatientRecord patientRec = omdCds.getPatientRecord();

        //DEMOGRAPHICS
        Demographics demo = patientRec.getDemographics();
        cdsDt.PersonNameStandard.LegalName legalName = demo.getNames().getLegalName();
        String lastName = "", firstName = "";
        String lastNameQualifier = null, firstNameQualifier = null;
        if (legalName != null) {
            if (legalName.getLastName() != null) {
                lastName = StringUtils.noNull(legalName.getLastName().getPart());
                if (legalName.getLastName().getPartQualifier() != null) {
                    lastNameQualifier = legalName.getLastName().getPartQualifier().toString();
                }
            }
            if (legalName.getFirstName() != null) {
                firstName = StringUtils.noNull(legalName.getFirstName().getPart());
                if (legalName.getFirstName().getPartQualifier() != null) {
                    firstNameQualifier = legalName.getFirstName().getPartQualifier().toString();
                }
            }
            patientName = lastName + "," + firstName;
        }

        String birthDate = getCalDate(demo.getDateOfBirth(), timeShiftInDays);
        String sex = demo.getGender() != null ? demo.getGender().toString() : "";
        String hin = null;
        cdsDt.HealthCard healthCard = demo.getHealthCard();
        if (healthCard != null) {
            hin = StringUtils.noNull(healthCard.getNumber());
        }

        //Check duplicate
        ArrayList<Demographic> demodup = null;
        if (StringUtils.filled(hin)) demodup = dd.getDemographicWithHIN(loggedInInfo, hin);
        else demodup = dd.getDemographicWithLastFirstDOB(loggedInInfo, lastName, firstName, birthDate);
        if (demodup.size() == 0) {
            logger.info("patient to add contact to not found");
            return null;
        }
        if (demodup.size() > 1) {
            logger.info("found multiple patients to add contact to");
            return null;
        }

        Demographic patient = demodup.get(0);


        Demographics.Contact[] contt = demo.getContactArray();
        for (int i = 0; i < contt.length; i++) {
            HashMap<String, String> contactName = getPersonName(contt[i].getName());
            String cFirstName = StringUtils.noNull(contactName.get("firstname"));
            String cLastName = StringUtils.noNull(contactName.get("lastname"));
            String cEmail = StringUtils.noNull(contt[i].getEmailAddress());

            cdsDt.PhoneNumber[] pn = contt[i].getPhoneNumberArray();
            String workPhone = "", workExt = "", homePhone = "", homeExt = "", cellPhone = "", ext = "", patientPhone = "";

            workPhone = "";
            workExt = "";
            homePhone = "";
            homeExt = "";
            cellPhone = "";
            ext = "";
            for (int j = 0; j < pn.length; j++) {
                String phone = pn[j].getPhoneNumber();
                if (phone == null) {
                    if (pn[j].getNumber() != null) {
                        if (pn[j].getAreaCode() != null) phone = pn[j].getAreaCode() + "-" + pn[j].getNumber();
                        else phone = pn[j].getNumber();
                    }
                }
                if (phone != null) {
                    if (pn[j].getExtension() != null) ext = pn[j].getExtension();
                    else if (pn[j].getExchange() != null) ext = pn[j].getExchange();

                    if (pn[j].getPhoneNumberType() == cdsDt.PhoneNumberType.W) {
                        workPhone = phone;
                        workExt = ext;
                    } else if (pn[j].getPhoneNumberType() == cdsDt.PhoneNumberType.R) {
                        homePhone = phone;
                        homeExt = ext;
                    } else if (pn[j].getPhoneNumberType() == cdsDt.PhoneNumberType.C) {
                        cellPhone = phone;
                    }
                }
            }

            String contactNote = StringUtils.noNull(contt[i].getNote());
            String cDemoNo = dd.getDemoNoByNamePhoneEmail(loggedInInfo, cFirstName, cLastName, homePhone, workPhone, cEmail);
            String cPatient = cLastName + "," + cFirstName;

            // If contact not found, create as "Contact-only" demographic
            if (StringUtils.empty(cDemoNo)) {
                String psDate = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");
                DemographicAddResult demoRes = dd.addDemographic(loggedInInfo,
                        "", cLastName, cFirstName, "", // title, last, first, middleNames
                        "", "", "", "", // address, city, province, postal
                        "", "", "", "", // residentialAddress, residentialCity, residentialProvince, residentialPostal
                        homePhone, workPhone, // phone, phone2
                        "", "", "", "", "", // year_of_birth, month, date, hin, ver
                        "Contact-only", psDate, "", "", "", // roster_status, roster_date, termination_date, termination_reason, enrolledTo
                        "IN", psDate, "", "", // patient_status, patient_status_date, date_joined, chart_no
                        "", "", "", // official_lang, spoken_lang, provider_no
                        "U", "", "", "", "", "", "", // sex, end_date, eff_date, pcn_indicator, hc_type, hc_renew_date, family_doctor
                        cEmail, "", "", "", "", "", ""); // email, alias, previousAddress, children, sourceOfIncome, citizenship, sin
                cDemoNo = demoRes.getId();

                // Save phone extensions and cellPhone to demographicExt
                String providerNo = loggedInInfo.getLoggedInProviderNo();
                if (StringUtils.filled(workExt)) {
                    demographicExtDao.addKey(providerNo, Integer.parseInt(cDemoNo), "wPhoneExt", workExt);
                }
                if (StringUtils.filled(homeExt)) {
                    demographicExtDao.addKey(providerNo, Integer.parseInt(cDemoNo), "hPhoneExt", homeExt);
                }
                if (StringUtils.filled(cellPhone)) {
                    demographicExtDao.addKey(providerNo, Integer.parseInt(cDemoNo), "demo_cell", cellPhone);
                }

                insertIntoAdmission(cDemoNo);
            }

            logger.info("adding contacts: " + cLastName + "," + cFirstName + " = " + cDemoNo);

            cdsDt.PurposeEnumOrPlainText[] contactPurposes = contt[i].getContactPurposeArray();
            String sdm = "", emc = "", cPurpose = null;
            String[] rel = new String[contactPurposes.length];

            for (int j = 0; j < contactPurposes.length; j++) {
                cPurpose = contactPurposes[j].getPurposeAsPlainText();
                if (cPurpose == null) cPurpose = contactPurposes[j].getPurposeAsEnum().toString();
                if (cPurpose != null) cPurpose = cPurpose.trim();
                else continue;

                if (cPurpose.equals("EC") || cPurpose.equalsIgnoreCase("emergency contact"))
                    emc = "true";
                else if (cPurpose.equals("SDM") || cPurpose.equalsIgnoreCase("substitute decision maker"))
                    sdm = "true";
                else if (cPurpose.equals("NK")) rel[j] = "Next of Kin";
                else if (cPurpose.equals("AS")) rel[j] = "Administrative Staff";
                else if (cPurpose.equals("CG")) rel[j] = "Care Giver";
                else if (cPurpose.equals("PA")) rel[j] = "Power of Attorney";
                else if (cPurpose.equals("IN")) rel[j] = "Insurance";
                else if (cPurpose.equals("GT")) rel[j] = "Guarantor";
                else if (cPurpose.equals("O")) rel[j] = "Other";
                else {
                    rel[j] = cPurpose;
                }
            }

            // Create demographic relationships
            if (StringUtils.filled(cDemoNo)) {
                Facility facility = (Facility) request.getSession().getAttribute(SessionConstants.CURRENT_FACILITY);
                Integer facilityId = null;
                if (facility != null) facilityId = facility.getId();

                for (int j = 0; j < rel.length; j++) {
                    if (rel[j] == null) continue;

                    DemographicRelationship demoRel = new DemographicRelationship();
                    demoRel.addDemographicRelationship(demographicNo, cDemoNo, rel[j], sdm.equals("true"), emc.equals("true"), contactNote, admProviderNo, facilityId);

                    //clear emc, sdm, contactNote after 1st save
                    emc = "";
                    sdm = "";
                    contactNote = "";
                }
            }
        }

        return null;
    }


    private String[] importXML(LoggedInInfo loggedInInfo, String xmlFile, ArrayList<String> warnings, HttpServletRequest request, int timeShiftInDays, Provider student, Program admitTo, int courseId, boolean cleanFile) throws SQLException, Exception {
        ArrayList<String> err_demo = new ArrayList<String>(); //errors: duplicate demographics
        ArrayList<String> err_data = new ArrayList<String>(); //errors: discrete data
        ArrayList<String> err_summ = new ArrayList<String>(); //errors: summary
        ArrayList<String> err_othe = new ArrayList<String>(); //errors: other categories
        ArrayList<String> err_note = new ArrayList<String>(); //non-errors: notes
        importErrors = new ArrayList<String>();

        String docDir = oscarProperties.getProperty("DOCUMENT_DIR");
        docDir = Util.fixDirName(docDir);
        if (!Util.checkDir(docDir)) {
            logger.debug("Error! Cannot write to DOCUMENT_DIR - Check oscar.properties or dir permissions.");
        }

        File xmlF = new File(xmlFile);
        PatientRecord patientRec = null;
        try {
            XmlOptions opts = new XmlOptions();
            List c = new ArrayList();

            opts.setErrorListener(c);
            opts.setDocumentType(OmdCdsDocument.Factory.newInstance().schemaType());
            OmdCdsDocument.OmdCds omdCds = OmdCdsDocument.Factory.parse(xmlF, opts).getOmdCds();
            omdCds.validate(opts);
            patientRec = omdCds.getPatientRecord();
        } catch (IOException ex) {
            logger.error("Error", ex);
        } catch (XmlException ex) {
            logger.error("Error", ex);
        }

        //DEMOGRAPHICS
        Demographics demo = patientRec.getDemographics();
        cdsDt.PersonNameStandard.LegalName legalName = demo.getNames().getLegalName();
        String lastName = "", firstName = "";
        String lastNameQualifier = null, firstNameQualifier = null;
        if (legalName != null) {
            if (legalName.getLastName() != null) {
                lastName = StringUtils.noNull(legalName.getLastName().getPart());
                if (legalName.getLastName().getPartQualifier() != null) {
                    lastNameQualifier = legalName.getLastName().getPartQualifier().toString();
                }
            }
            if (legalName.getFirstName() != null) {
                firstName = StringUtils.noNull(legalName.getFirstName().getPart());
                if (legalName.getFirstName().getPartQualifier() != null) {
                    firstNameQualifier = legalName.getFirstName().getPartQualifier().toString();
                }
            }
        } else {
            err_data.add("Error! No Legal Name");
        }

        //other names
        String otherNameTxt = null;

        LegalName.OtherName[] legalOtherNames = new LegalName.OtherName[0];
        if (legalName != null && legalName.getOtherNameArray() != null) {
            legalOtherNames = legalName.getOtherNameArray();
        }
        String middleNames = "";

        for (LegalName.OtherName otherName : legalOtherNames) {
            if (otherName.getPartQualifier() == PersonNamePartQualifierCode.CL && otherName.getPartType() == PersonNamePartTypeCode.GIV) {
                middleNames += (otherName.getPart() + " ");
            }
        }
        middleNames = middleNames.trim();

        OtherNames[] otherNames = demo.getNames().getOtherNamesArray();
        for (OtherNames otherName : otherNames) {
            OtherNames.OtherName[] otherNames2 = otherName.getOtherNameArray();
            for (OtherNames.OtherName otherName2 : otherNames2) {
                if (otherNameTxt == null) {
                    otherNameTxt = otherName2.getPart();
                    // sometimes the legal first name is empty
                    if (firstName.isEmpty()) {
                        firstName = otherName2.getPart();
                    }
                } else {
                    otherNameTxt += ", " + otherName2.getPart();
                    // sometimes the legal last name is empty
                    lastName = otherName2.getPart();
                }
            }
            if (otherName.getNamePurpose() != null) {
                otherNameTxt = Util.addLine(mapNamePurpose(otherName.getNamePurpose()) + ": ", otherNameTxt);
            }
        }
        otherNameTxt = Util.addLine("", otherNameTxt);

        String title = demo.getNames().getNamePrefix() != null ? demo.getNames().getNamePrefix().toString() : "";
        String suffix = demo.getNames().getLastNameSuffix() != null ? demo.getNames().getLastNameSuffix().toString() : "";

        patientName = lastName + "," + firstName;

        // GENDER
        String sex = demo.getGender() != null ? demo.getGender().toString() : "";
        if (StringUtils.empty(sex)) {
            err_data.add("Error! No Gender");
        }
        String birthDate = getCalDate(demo.getDateOfBirth(), timeShiftInDays);
        if (StringUtils.empty(birthDate)) {
            birthDate = null;
            err_data.add("Error! No Date Of Birth");
        }
        String versionCode = "", hin = "", hc_type = "", hc_renew_date = "";
        cdsDt.HealthCard healthCard = demo.getHealthCard();
        if (healthCard != null) {
            hin = StringUtils.noNull(healthCard.getNumber());
            if (hin.equals("")) {
                err_data.add("Error! No health card number");
            }
            hc_type = getProvinceCode(healthCard.getProvinceCode());
            if (hc_type.equals("")) {
                err_data.add("Error! No Province Code for health card");
            }
            versionCode = StringUtils.noNull(healthCard.getVersion());
            hc_renew_date = getCalDate(healthCard.getExpirydate());
        }

        //Check duplicate
        DemographicData dd = new DemographicData();
        ArrayList<Demographic> demodup = null;
        if (StringUtils.filled(hin)) demodup = dd.getDemographicWithHIN(loggedInInfo, hin);
        else demodup = dd.getDemographicWithLastFirstDOB(loggedInInfo, lastName, firstName, birthDate);
        if (demodup.size() > 0) {
            err_data.clear();
            err_demo.add("Error! Patient " + patientName + " already exist! Not imported.");
            return packMsgs(err_demo, err_data, err_summ, err_othe, err_note, warnings);
        }


        String patient_status = null;
        Demographics.PersonStatusCode personStatusCode = demo.getPersonStatusCode();
        if (personStatusCode != null) {
            if (personStatusCode.getPersonStatusAsEnum() != null) {
                if (personStatusCode.getPersonStatusAsEnum().equals(cdsDt.PersonStatus.A)) patient_status = "AC";
                if (personStatusCode.getPersonStatusAsEnum().equals(cdsDt.PersonStatus.I)) patient_status = "IN";
                if (personStatusCode.getPersonStatusAsEnum().equals(cdsDt.PersonStatus.D)) patient_status = "DE";
            } else if (personStatusCode.getPersonStatusAsPlainText() != null) {
                patient_status = personStatusCode.getPersonStatusAsPlainText();
            } else {
                err_data.add("Error! No Person Status Code");
            }
        } else {
            err_data.add("Error! No Person Status Code");
        }

        EnrolmentHistory[] enrolments = new EnrolmentHistory[0];
        if (demo.getEnrolment() != null) {
            enrolments = demo.getEnrolment().getEnrolmentHistoryArray();
        }
        int enrolTotal = enrolments.length;
        String[] roster_status = new String[enrolTotal],
                roster_date = new String[enrolTotal],
                term_date = new String[enrolTotal],
                term_reason = new String[enrolTotal],
                roster_enrolledTo = new String[enrolTotal];

        String rosterInfo;
        Calendar enrollDate, currentEnrollDate;

        for (int i = 0; i < enrolTotal; i++) {
            roster_status[i] = enrolments[i].getEnrollmentStatus() != null ? enrolments[i].getEnrollmentStatus().toString() : "";
            if (roster_status[i].equals("1")) roster_status[i] = "RO";
            else if (roster_status[i].equals("0")) roster_status[i] = "NR";
            roster_date[i] = getCalDate(enrolments[i].getEnrollmentDate(), timeShiftInDays);
            term_date[i] = getCalDate(enrolments[i].getEnrollmentTerminationDate(), timeShiftInDays);
            if (enrolments[i].getTerminationReason() != null)
                term_reason[i] = enrolments[i].getTerminationReason().toString();
            if (enrolments[i].getEnrolledToPhysician() != null) {
                EnrolledToPhysician enrolledToPhysician = enrolments[i].getEnrolledToPhysician();

                HashMap<String, String> personName = getPersonName(enrolledToPhysician.getName());
                String personOHIP = enrolledToPhysician.getOHIPPhysicianId();
                if (StringUtils.empty(personName.get("firstname")))
                    err_data.add("Error! No Enrolled To Physician first name");
                if (StringUtils.empty(personName.get("lastname")))
                    err_data.add("Error! No Enrolled To Physician last name");
                if (StringUtils.empty(personOHIP)) err_data.add("Error! No Enrolled To Physician OHIP billing number");

                roster_enrolledTo[i] = writeProviderData(personName.get("firstname"), personName.get("lastname"), personOHIP, null);
            }

            //Sort enrolments by date
            if (enrolments[i].getEnrollmentDate() != null) currentEnrollDate = enrolments[i].getEnrollmentDate();
            else if (enrolments[i].getEnrollmentTerminationDate() != null)
                currentEnrollDate = enrolments[i].getEnrollmentTerminationDate();
            else currentEnrollDate = null;

            for (int j = i - 1; j >= 0; j--) {
                if (enrolments[j].getEnrollmentDate() != null) enrollDate = enrolments[j].getEnrollmentDate();
                else if (enrolments[j].getEnrollmentTerminationDate() != null)
                    enrollDate = enrolments[j].getEnrollmentTerminationDate();
                else break;

                if (currentEnrollDate == null || currentEnrollDate.before(enrollDate)) {
                    rosterInfo = roster_status[j];
                    roster_status[j] = roster_status[i];
                    roster_status[i] = rosterInfo;
                    rosterInfo = roster_date[j];
                    roster_date[j] = roster_date[i];
                    roster_date[i] = rosterInfo;
                    rosterInfo = term_date[j];
                    term_date[j] = term_date[i];
                    term_date[i] = rosterInfo;
                    rosterInfo = term_reason[j];
                    term_reason[j] = term_reason[i];
                    term_reason[i] = rosterInfo;
                    rosterInfo = roster_enrolledTo[j];
                    roster_enrolledTo[j] = roster_enrolledTo[i];
                    roster_enrolledTo[i] = rosterInfo;
                }
            }
        }

        String rosterStatus = null, rosterDate = null, termDate = null, termReason = null, rosterEnrolledTo = null;
        if (enrolTotal > 0) {
            rosterStatus = roster_status[enrolTotal - 1];
            rosterDate = roster_date[enrolTotal - 1];
            termDate = term_date[enrolTotal - 1];
            termReason = term_reason[enrolTotal - 1];
            rosterEnrolledTo = roster_enrolledTo[enrolTotal - 1];
        }

        String sin = StringUtils.noNull(demo.getSIN());

        String chart_no = StringUtils.noNull(demo.getChartNumber());
        String official_lang = null;
        if (demo.getPreferredOfficialLanguage() != null) {
            official_lang = demo.getPreferredOfficialLanguage().toString();
            official_lang = official_lang.equals("ENG") ? "English" : official_lang;
            official_lang = official_lang.equals("FRE") ? "French" : official_lang;
        }

        String spoken_lang = null;
        if (demo.getPreferredSpokenLanguage() != null) {
            spoken_lang = Util.convertCodeToLanguage(demo.getPreferredSpokenLanguage());
            if (StringUtils.empty(spoken_lang))
                err_data.add("Error! Cannot map spoken language code " + demo.getPreferredSpokenLanguage());
        }

        String dNote = StringUtils.noNull(demo.getNoteAboutPatient());
        String uvID = demo.getUniqueVendorIdSequence();
        String psDate = getCalDate(demo.getPersonStatusDate(), timeShiftInDays);
        String extra = null;

        if (StringUtils.filled(lastNameQualifier)) {
            extra = Util.addLine(extra, "Lastname Qualifier: ", lastNameQualifier);
        }

        if (StringUtils.filled(firstNameQualifier)) {
            extra = Util.addLine(extra, "Firstname Qualifier: ", firstNameQualifier);
        }

        if (StringUtils.filled(otherNameTxt)) {
            extra = Util.addLine(extra, "Other name: ", otherNameTxt);
        }

        if (StringUtils.filled(suffix)) {
            extra = Util.addLine(extra, "Lastname suffix: ", suffix);
        }

        if (StringUtils.filled(uvID)) {
            extra = Util.addLine(extra, "Unique Vendor ID: ", uvID);
        } else {
            err_data.add("Error! No Unique Vendor ID Sequence");
        }

        String address = "", city = "", province = "", postalCode = "";
        String residentialAddress = "", residentialCity = "", residentialProvince = "", residentialPostalCode = "";

        if (demo.getAddressArray() != null) {
            for (cdsDt.Address addr : demo.getAddressArray()) {
                if (addr.getAddressType() == AddressType.M) {
                    if (StringUtils.filled(addr.getFormatted())) {
                        address = addr.getFormatted();
                    } else {
                        cdsDt.AddressStructured addrStr = addr.getStructured();
                        if (addrStr != null) {
                            address = StringUtils.noNull(addrStr.getLine1()) + StringUtils.noNull(addrStr.getLine2()) + StringUtils.noNull(addrStr.getLine3());
                            city = StringUtils.noNull(addrStr.getCity());
                            province = getCountrySubDivCode(addrStr.getCountrySubdivisionCode());
                            cdsDt.PostalZipCode postalZip = addrStr.getPostalZipCode();
                            if (postalZip != null) postalCode = StringUtils.noNull(postalZip.getPostalCode());
                        }
                    }
                } else {

                    //there's an address we don't support
                    if (StringUtils.filled(addr.getFormatted())) {
                        residentialAddress = addr.getFormatted();
                        extra = Util.addLine(extra, "Residential Address: ", residentialAddress);
                    } else {
                        cdsDt.AddressStructured addrStr = addr.getStructured();
                        if (addrStr != null) {
                            residentialAddress = StringUtils.noNull(addrStr.getLine1()) + StringUtils.noNull(addrStr.getLine2()) + StringUtils.noNull(addrStr.getLine3());
                            residentialCity = StringUtils.noNull(addrStr.getCity());
                            residentialProvince = getCountrySubDivCode(addrStr.getCountrySubdivisionCode());
                            cdsDt.PostalZipCode residentialPostalZip = addrStr.getPostalZipCode();
                            if (residentialPostalZip != null)
                                residentialPostalCode = StringUtils.noNull(residentialPostalZip.getPostalCode());

                            extra = Util.addLine(extra, "Residential Address: ", residentialAddress);
                            extra = Util.addLine(extra, "Residential City: ", residentialCity);
                            extra = Util.addLine(extra, "Residential Province: ", residentialProvince);
                            extra = Util.addLine(extra, "Residential Postal Code: ", residentialPostalCode);


                        }

                    }
                }
            }
        }
        cdsDt.PhoneNumber[] pn = demo.getPhoneNumberArray();
        String workPhone = "", workExt = "", homePhone = "", homeExt = "", cellPhone = "", ext = "", patientPhone = "";
        for (int i = 0; i < pn.length; i++) {
            String phone = pn[i].getPhoneNumber();
            if (StringUtils.empty(phone)) {
                if (StringUtils.filled(pn[i].getNumber())) {
                    String areaCode = StringUtils.filled(pn[i].getAreaCode()) ? "(" + pn[i].getAreaCode() + ")" : "";
                    phone = areaCode + pn[i].getNumber();
                }
            }
            if (StringUtils.filled(phone)) {
                if (StringUtils.filled(pn[i].getExtension())) ext = pn[i].getExtension();
                else if (StringUtils.filled(pn[i].getExchange())) ext = pn[i].getExchange();

                if (pn[i].getPhoneNumberType() == cdsDt.PhoneNumberType.W) {
                    workPhone = phone;
                    workExt = ext;
                } else if (pn[i].getPhoneNumberType() == cdsDt.PhoneNumberType.R) {
                    homePhone = phone;
                    homeExt = ext;
                } else if (pn[i].getPhoneNumberType() == cdsDt.PhoneNumberType.C) {
                    cellPhone = phone;
                }
            }
        }

        if (StringUtils.filled(homePhone)) patientPhone = homePhone + " " + homeExt;
        else if (StringUtils.filled(workPhone)) patientPhone = workPhone + " " + workExt;
        else if (StringUtils.filled(cellPhone)) patientPhone = cellPhone;
        String email = StringUtils.noNull(demo.getEmail());

        String primaryPhysician = "";
        if (student == null) {
            Demographics.PrimaryPhysician demoPrimaryPhysician = demo.getPrimaryPhysician();
            if (demoPrimaryPhysician != null) {
                HashMap<String, String> personName = getPersonName(demoPrimaryPhysician.getName());
                String personOHIP = demoPrimaryPhysician.getOHIPPhysicianId();
                if (StringUtils.empty(personName.get("firstname")))
                    err_data.add("Error! No Primary Physician first name");
                if (StringUtils.empty(personName.get("lastname")))
                    err_data.add("Error! No Primary Physician last name");
                if (StringUtils.empty(personOHIP)) err_data.add("Error! No Primary Physician OHIP billing number");
                String personCPSO = demoPrimaryPhysician.getPrimaryPhysicianCPSO();
                primaryPhysician = writeProviderData(personName.get("firstname"), personName.get("lastname"), personOHIP, personCPSO);
            }
            if (StringUtils.empty(primaryPhysician)) {
                primaryPhysician = defaultProviderNo();
                err_data.add("Error! No Primary Physician; patient assigned to \"doctor oscardoc\"");
            }
        } else {
            primaryPhysician = student.getProviderNo();
        }

        String year_of_birth = null;
        String month_of_birth = null;
        String date_of_birth = null;
        if (birthDate != null) {
            Date bDate = UtilDateUtilities.StringToDate(birthDate, "yyyy-MM-dd");
            year_of_birth = UtilDateUtilities.DateToString(bDate, "yyyy");
            month_of_birth = UtilDateUtilities.DateToString(bDate, "MM");
            date_of_birth = UtilDateUtilities.DateToString(bDate, "dd");
        }

        DemographicAddResult demoRes = null;

        //Check if Contact-only demographic exists
        if (courseId == 0) {
            // make the cell phone a home phone if home phone is not defined.
            String phone = homePhone;
            if (phone.isEmpty() || !cellPhone.isEmpty()) {
                phone = cellPhone;
            }
            demographicNo = dd.getDemoNoByNamePhoneEmail(loggedInInfo, firstName, lastName, phone, workPhone, email);
            demographic = dd.getDemographic(loggedInInfo, demographicNo);
        }
/*
        if (demographic!=null && StringUtils.nullSafeEqualsIgnoreCase(demographic.getPatientStatus(), "Contact-only")) {
        	//found contact-only demo, replace!
        	SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            demographic.setTitle(title);
            demographic.setMiddleNames(middleNames);
            demographic.setAddress(address);
            demographic.setCity(city);
            demographic.setProvince(province);
            demographic.setPostal(postalCode);
            demographic.setYearOfBirth(year_of_birth);
            demographic.setMonthOfBirth(month_of_birth);
            demographic.setDateOfBirth(date_of_birth);
            demographic.setHin(hin);
            demographic.setVer(versionCode);
            demographic.setRosterStatus(rosterStatus);
            demographic.setRosterEnrolledTo(rosterEnrolledTo);

            Date dDate;
            try {
            	dDate = formatter.parse(rosterDate);
            }
            catch( Exception e ) {
            	dDate = null;
            }

            demographic.setRosterDate(dDate);


            try {
            	dDate = formatter.parse(termDate);
            }
            catch( Exception e ) {
            	dDate = null;
            }

            demographic.setRosterTerminationDate(dDate);
            demographic.setRosterTerminationReason(termReason);
            demographic.setPatientStatus(patient_status);

            try {
            	dDate = formatter.parse(psDate);
            }
            catch( Exception e ) {
            	dDate = null;
            }

            demographic.setPatientStatusDate(dDate);
            demographic.setChartNo(chart_no);
            demographic.setOfficialLanguage(official_lang);
            demographic.setSpokenLanguage(spoken_lang);
            demographic.setFamilyDoctor(primaryPhysician);
            demographic.setSex(sex);
            demographic.setHcType(hc_type);

            try {
            	dDate = formatter.parse(hc_renew_date);
            }
            catch( Exception e ) {
            	dDate = null;
            }

            demographic.setHcRenewDate(dDate);
            demographic.setSin(sin);
            dd.setDemographic(loggedInInfo, demographic);
            err_note.add("Replaced Contact-only patient "+patientName+" (Demo no="+demographicNo+")");

        } else { //add patient!
*/
        demoRes = dd.addDemographic(loggedInInfo, title, lastName, firstName, middleNames, address, city, province, postalCode, residentialAddress, residentialCity, residentialProvince, residentialPostalCode, homePhone, workPhone, year_of_birth, month_of_birth, date_of_birth, hin, versionCode, rosterStatus, rosterDate, termDate, termReason, rosterEnrolledTo, patient_status, psDate, ""/*date_joined*/, chart_no, official_lang, spoken_lang, primaryPhysician, sex, ""/*end_date*/, ""/*eff_date*/, ""/*pcn_indicator*/, hc_type, hc_renew_date, ""/*family_doctor*/, email, ""/*alias*/, ""/*previousAddress*/, ""/*children*/, ""/*sourceOfIncome*/, ""/*citizenship*/, sin);
        demographicNo = demoRes.getId();
        /*        }

         */

        if (StringUtils.filled(demographicNo)) {
            //TODO: Course - Admit to student program

            entries.put(PATIENTID + importNo, Integer.valueOf(demographicNo));

            if (admitTo == null) {
                insertIntoAdmission(demographicNo);
            } else {
                admissionManager.processAdmission(Integer.valueOf(demographicNo), student.getProviderNo(), admitTo, "", "batch import");
            }

            //Put enrolment history into demographicArchive
            demographic = dd.getDemographic(loggedInInfo, demographicNo);
            for (int i = 0; i < roster_status.length - 1; i++) {
                DemographicArchive demographicArchive = archiveDemographic(demographic);
                demographicArchive.setRosterStatus(roster_status[i]);
                demographicArchive.setRosterDate(UtilDateUtilities.StringToDate(roster_date[i]));
                demographicArchive.setRosterTerminationDate(UtilDateUtilities.StringToDate(term_date[i]));
                demographicArchive.setRosterTerminationReason(term_reason[i]);
                demographicArchive.setRosterEnrolledTo(roster_enrolledTo[i]);
                demoArchiveDao.persist(demographicArchive);
            }

            //Patient notes
            if (StringUtils.filled(dNote)) dd.addDemographiccust(demographicNo, dNote);

            //to dumpsite: Extra demographic data
            if (StringUtils.filled(extra)) {
                extra = Util.addLine("imported.cms4.2011.06", extra);
                if (!"imported.cms4.2011.06".equals(extra)) {
                    CaseManagementNote dmNote = prepareCMNote("2", null);
                    dmNote.setNote(extra);
                    saveLinkNote(dmNote, CaseManagementNoteLink.DEMOGRAPHIC, Long.valueOf(demographicNo));
                }
            }

            if (!workExt.equals(""))
                demographicExtDao.addKey(primaryPhysician, Integer.parseInt(demographicNo), "wPhoneExt", workExt);
            if (!homeExt.equals(""))
                demographicExtDao.addKey(primaryPhysician, Integer.parseInt(demographicNo), "hPhoneExt", homeExt);
            if (!cellPhone.equals(""))
                demographicExtDao.addKey(primaryPhysician, Integer.parseInt(demographicNo), "demo_cell", cellPhone);
            if (courseId > 0)
                demographicExtDao.addKey(primaryPhysician, Integer.parseInt(demographicNo), "course", String.valueOf(courseId));

            PharmacyInfoDao pharmacyInfoDao = SpringUtils.getBean(PharmacyInfoDao.class);
            DemographicPharmacyDao demographicPharmacyDao = SpringUtils.getBean(DemographicPharmacyDao.class);

            if (demo.getPreferredPharmacy() != null) {
                PreferredPharmacy pp = demo.getPreferredPharmacy();

                PharmacyInfo pi = new PharmacyInfo();
                pi.setName(pp.getName());
                pi.setEmail(pp.getEmailAddress());
                if (pp.getPhoneNumber() != null) {
                    pi.setPhone1(pp.getPhoneNumber().getPhoneNumber().replaceAll("\\-", ""));
                }
                if (pp.getFaxNumber() != null) {
                    pi.setFax(pp.getFaxNumber().getPhoneNumber().replaceAll("\\-", ""));
                }
                if (pp.getAddress() != null) {
                    cdsDt.AddressStructured addrStr = pp.getAddress().getStructured();
                    if (addrStr != null) {
                        pi.setAddress((StringUtils.noNull(addrStr.getLine1()) + " " + StringUtils.noNull(addrStr.getLine2()) + " " + StringUtils.noNull(addrStr.getLine3())).trim());
                        pi.setCity(StringUtils.noNull(addrStr.getCity()));
                        pi.setProvince(getCountrySubDivCode(addrStr.getCountrySubdivisionCode()));
                        cdsDt.PostalZipCode postalZip = addrStr.getPostalZipCode();
                        if (postalZip != null) pi.setPostalCode(StringUtils.noNull(postalZip.getPostalCode()));
                    }

                }
                pi.setStatus('1');
                pi.setNotes("");
                pi.setServiceLocationIdentifier("");
                pharmacyInfoDao.persist(pi);

                DemographicPharmacy dp = new DemographicPharmacy();
                dp.setPharmacyId(pi.getId());
                dp.setDemographicNo(Integer.parseInt(demographicNo));
                dp.setAddDate(new Date());
                dp.setPreferredOrder(1);
                dp.setStatus("1");

                demographicPharmacyDao.persist(dp);
            }

            ContactDao contactDao1 = SpringUtils.getBean(ContactDao.class);

            if (demo.getReferredPhysician() != null) {
                Contact c = new Contact();
                c.setFirstName(demo.getReferredPhysician().getFirstName());
                c.setLastName(demo.getReferredPhysician().getLastName());
                contactDao1.persist(c);

                DemographicContact demoContact = new DemographicContact();
                demoContact.setCreated(new Date());
                demoContact.setUpdateDate(new Date());
                demoContact.setDemographicNo(Integer.valueOf(demographicNo));
                demoContact.setContactId(c.getId().toString());
                demoContact.setType(2); //should be "type" - display problem
                demoContact.setCategory("professional");
                demoContact.setRole("Referring Doctor");
                demoContact.setCreator(loggedInInfo.getLoggedInProviderNo());
                contactDao.persist(demoContact);

            }

            if (demo.getFamilyPhysician() != null) {
                Contact c = new Contact();
                c.setFirstName(demo.getFamilyPhysician().getFirstName());
                c.setLastName(demo.getFamilyPhysician().getLastName());
                contactDao.persist(c);

                DemographicContact demoContact = new DemographicContact();
                demoContact.setCreated(new Date());
                demoContact.setUpdateDate(new Date());
                demoContact.setDemographicNo(Integer.valueOf(demographicNo));
                demoContact.setContactId(c.getId().toString());
                demoContact.setType(2); //should be "type" - display problem
                demoContact.setCategory("professional");
                demoContact.setRole("Family Doctor");
                demoContact.setCreator(loggedInInfo.getLoggedInProviderNo());
                contactDao.persist(demoContact);
            }

            //Demographic Contacts
/*
            Demographics.Contact[] contt = demo.getContactArray();
            for (int i=0; i<contt.length; i++) {
                HashMap<String,String> contactName = getPersonName(contt[i].getName());
                String cFirstName = StringUtils.noNull(contactName.get("firstname"));
                String cLastName  = StringUtils.noNull(contactName.get("lastname"));
                String cEmail = StringUtils.noNull(contt[i].getEmailAddress());

                pn = contt[i].getPhoneNumberArray();
                workPhone=""; workExt=""; homePhone=""; homeExt=""; cellPhone=""; ext="";
                for (int j=0; j<pn.length; j++) {
                    String phone = pn[j].getPhoneNumber();
                    if (phone==null) {
                        if (pn[j].getNumber()!=null) {
                            if (pn[j].getAreaCode()!=null) phone = pn[j].getAreaCode()+"-"+pn[j].getNumber();
                            else phone = pn[j].getNumber();
                        }
                    }
                    if (phone!=null) {
                        if (pn[j].getExtension()!=null) ext = pn[j].getExtension();
                        else if (pn[j].getExchange()!=null) ext = pn[j].getExchange();

                        if (pn[j].getPhoneNumberType()==cdsDt.PhoneNumberType.W) {
                            workPhone = phone;
                            workExt   = ext;
                        } else if (pn[j].getPhoneNumberType()==cdsDt.PhoneNumberType.R) {
                            homePhone = phone;
                            homeExt   = ext;
                        } else if (pn[j].getPhoneNumberType()==cdsDt.PhoneNumberType.C) {
                            cellPhone = phone;
                        }
                    }
                }

                String contactNote = StringUtils.noNull(contt[i].getNote());
                String cDemoNo = dd.getDemoNoByNamePhoneEmail(loggedInInfo, cFirstName, cLastName, homePhone, workPhone, cEmail);
                String cPatient = cLastName+","+cFirstName;
                if (StringUtils.empty(cDemoNo)) {   //add new demographic as contact
                    psDate = UtilDateUtilities.DateToString(new Date(),"yyyy-MM-dd");
                    demoRes = dd.addDemographic(loggedInInfo, "", cLastName, cFirstName,"" , "", "", "", "","","","","",
                    			homePhone, workPhone, "", "", "", "", "", "", "", "", "",null,
                    			"Contact-only", psDate, "", "", "", "", "",
                    			"F", "", "", "", "", "", "",
                    			cEmail, "", "", "", "", "", "", "");
                	cDemoNo = demoRes.getId();
                    err_note.add("Contact-only patient "+cPatient+" (Demo no="+cDemoNo+") created");

                    if (!workExt.equals("")) demographicExtDao.addKey("", Integer.parseInt(cDemoNo), "wPhoneExt", workExt);
                    if (!homeExt.equals("")) demographicExtDao.addKey("", Integer.parseInt(cDemoNo), "hPhoneExt", homeExt);
                    if (!cellPhone.equals("")) demographicExtDao.addKey("", Integer.parseInt(cDemoNo), "demo_cell", cellPhone);
                }
                insertIntoAdmission(cDemoNo);

                cdsDt.PurposeEnumOrPlainText[] contactPurposes = contt[i].getContactPurposeArray();
                String sdm="", emc="", cPurpose=null;
                String[] rel = new String[contactPurposes.length];

                for (int j=0; j<contactPurposes.length; j++) {
                    cPurpose = contactPurposes[j].getPurposeAsPlainText();
                    if (cPurpose==null) cPurpose = contactPurposes[j].getPurposeAsEnum().toString();
                    if (cPurpose!=null) cPurpose = cPurpose.trim();
                    else continue;

                    if (cPurpose.equals("EC") || cPurpose.equalsIgnoreCase("emergency contact"))
                    	emc = "true";
                    else if (cPurpose.equals("SDM") || cPurpose.equalsIgnoreCase("substitute decision maker"))
                    	sdm = "true";
                    else if (cPurpose.equals("NK")) rel[j] = "Next of Kin";
                    else if (cPurpose.equals("AS")) rel[j] = "Administrative Staff";
                    else if (cPurpose.equals("CG")) rel[j] = "Care Giver";
                    else if (cPurpose.equals("PA")) rel[j] = "Power of Attorney";
                    else if (cPurpose.equals("IN")) rel[j] = "Insurance";
                    else if (cPurpose.equals("GT")) rel[j] = "Guarantor";
                    else if (cPurpose.equals("O")) rel[j] = "Other";
                    else {
                        rel[j] = cPurpose;
                    }
                }

                if (StringUtils.filled(cDemoNo)) {
                	if (oscarProperties.isPropertyActive("NEW_CONTACTS_UI")) {
                        for (int j=0; j<rel.length; j++) {
                        	if (rel[j]==null) continue;

                            DemographicContact demoContact = new DemographicContact();
                            demoContact.setCreated(new Date());
                            demoContact.setUpdateDate(new Date());
                            demoContact.setDemographicNo(Integer.valueOf(demographicNo));
                            demoContact.setContactId(cDemoNo);
                            demoContact.setType(1); //should be "type" - display problem
                            demoContact.setCategory("personal");
                        	demoContact.setRole(rel[j]);
                            demoContact.setEc(emc);
                            demoContact.setSdm(sdm);
                            demoContact.setNote(contactNote);
                            demoContact.setCreator(loggedInInfo.getLoggedInProviderNo());
                        	contactDao.persist(demoContact);

                        	//clear emc, sdm, contactNote after 1st save
                        	emc = "";
                        	sdm = "";
                        	contactNote = "";
                        }
                	} else {
				        Facility facility = (Facility) request.getSession().getAttribute(SessionConstants.CURRENT_FACILITY);
				        Integer facilityId = null;
				        if (facility!=null) facilityId = facility.getId();

				        for (int j=0; j<rel.length; j++) {
				        	if (rel[j]==null) continue;

							DemographicRelationship demoRel = new DemographicRelationship();
							demoRel.addDemographicRelationship(demographicNo, cDemoNo, rel[j], sdm.equals("true"), emc.equals("true"), contactNote, admProviderNo, facilityId);

                        	//clear emc, sdm, contactNote after 1st save
                        	emc = "";
                        	sdm = "";
                        	contactNote = "";
				        }
                	}
                }
            }
*/
            Set<CaseManagementIssue> scmi = null;    //Declare a set for CaseManagementIssues
            //PERSONAL HISTORY
            PersonalHistory[] pHist = patientRec.getPersonalHistoryArray();
            for (int i = 0; i < pHist.length; i++) {
                cdsDt.ResidualInformation residualInformation = pHist[i].getResidualInfo();
                if (residualInformation == null) {
                    continue;
                }
                if (i == 0) {
                    scmi = getCMIssue("SocHistory");
                }
                CaseManagementNote cmNote = prepareCMNote("1", null);
                cmNote.setIssues(scmi);

                //main field
                String socialHist = "Imported Personal History";
                StringBuilder stringBuilder = new StringBuilder();
                for (cdsDt.ResidualInformation.DataElement dataElement : residualInformation.getDataElementArray()) {
                    if (!"Personal History".equals(dataElement.getName()))
                        stringBuilder.append(dataElement.getName()).append(": ");  //not prepending the phrase "Personal History" unnecessarily
                    stringBuilder.append(dataElement.getContent()).append("\n");
                }
                if (stringBuilder.capacity() > 0) {
                    socialHist = stringBuilder.toString();
                }

                cmNote.setNote(socialHist);

                caseManagementManager.saveNoteSimple(cmNote);
                addOneEntry(PERSONALHISTORY);

                //to dumpsite
                socialHist = Util.addLine("imported.cms4.2011.06", socialHist);
                Long hostNoteId = cmNote.getId();
                cmNote = prepareCMNote("2", null);
                cmNote.setNote(socialHist);
                saveLinkNote(hostNoteId, cmNote);
            }

            //FAMILY HISTORY
            FamilyHistory[] fHist = patientRec.getFamilyHistoryArray();
            for (int i = 0; i < fHist.length; i++) {
                if (i == 0) scmi = getCMIssue("FamHistory");
                CaseManagementNote cmNote = prepareCMNote("1", null);

                //diagnosis code
                if (fHist[i].getDiagnosisProcedureCode() == null) {
                    cmNote.setIssues(scmi);
                } else {
                    cmNote.setIssues(getCMIssue("FamHistory", fHist[i].getDiagnosisProcedureCode(), false));
                }

                //main field
                String familyHist = fHist[i].getProblemDiagnosisProcedureDescription();
                if (StringUtils.empty(familyHist)) {
                    if (fHist[i].isSetDiagnosisProcedureCode()) {
                        familyHist = fHist[i].getDiagnosisProcedureCode().getStandardCodeDescription();
                    }
                }
                if (StringUtils.empty(familyHist)) familyHist = "Imported Family History";
                cmNote.setNote(familyHist);
                caseManagementManager.saveNoteSimple(cmNote);
                addOneEntry(FAMILYHISTORY);

                //annotation
                Long hostNoteId = cmNote.getId();
                cmNote = prepareCMNote("2", null);
                String note = StringUtils.noNull(fHist[i].getNotes());
                cmNote.setNote(note);
                saveLinkNote(hostNoteId, cmNote);

                //to dumpsite
                String dump = "imported.cms4.2011.06";
                /*
                String summary = fHist[i].getCategorySummaryLine();
                if (StringUtils.empty(summary)) {
                        err_summ.add("No Summary for Family History ("+(i+1)+")");
                }
                dump = Util.addLine(dump, summary);
                */
                //String diagCode = getCode(fHist[i].getDiagnosisProcedureCode(),"Diagnosis/Procedure");
                //dump = Util.addLine(dump, diagCode);
                dump = Util.addLine(dump, getResidual(fHist[i].getResidualInfo()));
                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(hostNoteId, cmNote);
                }

                //extra fields
                CaseManagementNoteExt cme = new CaseManagementNoteExt();
                cme.setNoteId(hostNoteId);
                if (fHist[i].getStartDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.STARTDATE);
                    cme.setDateValue(dateFPtoDate(fHist[i].getStartDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(fHist[i].getStartDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                //TODO refactor code. Entire process fails if exception thrown due to bad data. It would be better to handle the exception.
                if (fHist[i].getAgeAtOnset() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.AGEATONSET);
                    cme.setDateValue((Date) null);
                    cme.setValue(fHist[i].getAgeAtOnset().toString());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (StringUtils.filled(fHist[i].getRelationship())) {
                    cme.setKeyVal(CaseManagementNoteExt.RELATIONSHIP);
                    cme.setDateValue((Date) null);
                    cme.setValue(fHist[i].getRelationship());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (StringUtils.filled(fHist[i].getTreatment())) {
                    cme.setKeyVal(CaseManagementNoteExt.TREATMENT);
                    cme.setDateValue((Date) null);
                    cme.setValue(fHist[i].getTreatment());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (fHist[i].getLifeStage() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.LIFESTAGE);
                    cme.setDateValue((Date) null);
                    cme.setValue(fHist[i].getLifeStage().toString());
                    caseManagementManager.saveNoteExt(cme);
                }
            }

            //PAST HEALTH
            PastHealth[] pHealth = patientRec.getPastHealthArray();
            for (int i = 0; i < pHealth.length; i++) {
                if (i == 0) scmi = getCMIssue("MedHistory");
                CaseManagementNote cmNote = prepareCMNote("1", null);

                //diagnosis code
                if (pHealth[i].getDiagnosisProcedureCode() == null) {
                    cmNote.setIssues(scmi);
                } else {
                    cmNote.setIssues(getCMIssue("MedHistory", pHealth[i].getDiagnosisProcedureCode(), pHealth[i].isSetResolvedDate()));

                }

                //main field
                String medicalHist = pHealth[i].getPastHealthProblemDescriptionOrProcedures();
                if (StringUtils.empty(medicalHist)) {
                    if (pHealth[i].isSetDiagnosisProcedureCode()) {
                        medicalHist = pHealth[i].getDiagnosisProcedureCode().getStandardCodeDescription();
                    }
                }
                if (StringUtils.empty(medicalHist)) medicalHist = "Imported Medical History";
                cmNote.setNote(medicalHist);
                caseManagementManager.saveNoteSimple(cmNote);
                addOneEntry(FAMILYHISTORY);

                //annotation
                Long hostNoteId = cmNote.getId();
                cmNote = prepareCMNote("2", null);
                String note = pHealth[i].getNotes();
                cmNote.setNote(note);
                saveLinkNote(hostNoteId, cmNote);


                //to dumpsite
                String dump = "imported.cms4.2011.06";
                /*
                String summary = pHealth[i].getCategorySummaryLine();
                if (StringUtils.empty(summary)) {
                    err_summ.add("No Summary for Past Health ("+(i+1)+")");
                }
                dump = Util.addLine(dump, summary);
                */
                String diagCode = isICD9(pHealth[i].getDiagnosisProcedureCode()) || isICD9CM(pHealth[i].getDiagnosisProcedureCode()) || isICD10(pHealth[i].getDiagnosisProcedureCode()) ? null : getCode(pHealth[i].getDiagnosisProcedureCode(), "Diagnosis/Procedure");
                dump = Util.addLine(dump, diagCode);
                dump = Util.addLine(dump, getResidual(pHealth[i].getResidualInfo()));

                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(hostNoteId, cmNote);
                }

                //extra fields
                CaseManagementNoteExt cme = new CaseManagementNoteExt();
                cme.setNoteId(hostNoteId);
                if (pHealth[i].getOnsetOrEventDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.STARTDATE);
                    cme.setDateValue(dateFPtoDate(pHealth[i].getOnsetOrEventDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(pHealth[i].getOnsetOrEventDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (pHealth[i].getProcedureDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.PROCEDUREDATE);
                    cme.setDateValue(dateFPtoDate(pHealth[i].getProcedureDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(pHealth[i].getProcedureDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (pHealth[i].getResolvedDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.RESOLUTIONDATE);
                    cme.setDateValue(dateFPtoDate(pHealth[i].getResolvedDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(pHealth[i].getResolvedDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (pHealth[i].getLifeStage() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.LIFESTAGE);
                    cme.setDateValue((Date) null);
                    cme.setValue(pHealth[i].getLifeStage().toString());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (StringUtils.filled(pHealth[i].getProblemStatus())) {
                    cme.setKeyVal(CaseManagementNoteExt.PROBLEMSTATUS);
                    cme.setDateValue((Date) null);
                    cme.setValue(pHealth[i].getProblemStatus());
                    caseManagementManager.saveNoteExt(cme);
                }
            }

            //PROBLEM LIST
            ProblemList[] probList = patientRec.getProblemListArray();
            for (int i = 0; i < probList.length; i++) {
                if (i == 0) scmi = getCMIssue("Concerns");
                CaseManagementNote cmNote = prepareCMNote("1", null);

                //diagnosis code
                if (probList[i].getDiagnosisCode() == null) {
                    cmNote.setIssues(scmi);
                } else {
                    cmNote.setIssues(getCMIssue("Concerns", probList[i].getDiagnosisCode(), probList[i].isSetResolutionDate()));
                }

                //main field
                String ongConcerns = probList[i].getProblemDiagnosisDescription();
                if (StringUtils.empty(ongConcerns)) {
                    if (probList[i].isSetDiagnosisCode()) {
                        ongConcerns = probList[i].getDiagnosisCode().getStandardCodeDescription();
                    }
                }
                if (StringUtils.empty(ongConcerns)) {
                    if (!StringUtils.empty(probList[i].getProblemDescription())) {
                        ongConcerns = probList[i].getProblemDescription();
                    }
                }
                if (StringUtils.empty(ongConcerns)) ongConcerns = "Imported Concern";
                cmNote.setNote(ongConcerns);
                caseManagementManager.saveNoteSimple(cmNote);
                addOneEntry(PROBLEMLIST);

                //annotation
                Long hostNoteId = cmNote.getId();
                cmNote = prepareCMNote("2", null);
                String note = probList[i].getNotes();
                cmNote.setNote(note);
                saveLinkNote(hostNoteId, cmNote);


                //to dumpsite
                String dump = "imported.cms4.2011.06";
                    /*
                    String summary = probList[i].getCategorySummaryLine();
                    if (StringUtils.empty(summary)) {
                            err_summ.add("No Summary for Problem List ("+(i+1)+")");
                    }
                    dump = Util.addLine(dump, summary);
                    */
                String diagCode = isICD9(probList[i].getDiagnosisCode()) || isICD9CM(probList[i].getDiagnosisCode()) || isICD10(probList[i].getDiagnosisCode()) ? null : getCode(probList[i].getDiagnosisCode(), "Diagnosis");
                dump = Util.addLine(dump, diagCode);
                dump = Util.addLine(dump, getResidual(probList[i].getResidualInfo()));
                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(hostNoteId, cmNote);
                }

                //extra fields
                CaseManagementNoteExt cme = new CaseManagementNoteExt();
                cme.setNoteId(hostNoteId);
                if (StringUtils.filled(probList[i].getProblemDescription())) {
                    cme.setKeyVal(CaseManagementNoteExt.PROBLEMDESC);
                    cme.setDateValue((Date) null);
                    cme.setValue(probList[i].getProblemDescription());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (probList[i].getOnsetDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.STARTDATE);
                    cme.setDateValue(dateFPtoDate(probList[i].getOnsetDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(probList[i].getOnsetDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (probList[i].getResolutionDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.RESOLUTIONDATE);
                    cme.setDateValue(dateFPtoDate(probList[i].getResolutionDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(probList[i].getResolutionDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (StringUtils.filled(probList[i].getProblemStatus())) {
                    cme.setKeyVal(CaseManagementNoteExt.PROBLEMSTATUS);
                    cme.setDateValue((Date) null);
                    cme.setValue(probList[i].getProblemStatus());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (probList[i].getLifeStage() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.LIFESTAGE);
                    cme.setDateValue((Date) null);
                    cme.setValue(probList[i].getLifeStage().toString());
                    caseManagementManager.saveNoteExt(cme);
                }
            }

            //RISK FACTORS
            RiskFactors[] rFactors = patientRec.getRiskFactorsArray();
            for (int i = 0; i < rFactors.length; i++) {
                if (i == 0) scmi = getCMIssue("RiskFactors");
                CaseManagementNote cmNote = prepareCMNote("1", null);
                cmNote.setIssues(scmi);

                //main field
                String riskFactors = rFactors[i].getRiskFactor();
                if (StringUtils.empty(riskFactors)) riskFactors = "Imported Risk Factor";
                cmNote.setNote(riskFactors);
                caseManagementManager.saveNoteSimple(cmNote);
                addOneEntry(RISKFACTOR);

                //annotation
                Long hostNoteId = cmNote.getId();
                cmNote = prepareCMNote("2", null);
                String note = rFactors[i].getNotes();
                cmNote.setNote(note);
                saveLinkNote(hostNoteId, cmNote);

                //to dumpsite
                String dump = "imported.cms4.2011.06";
                    /*
                    String summary = rFactors[i].getCategorySummaryLine();
                    if (StringUtils.empty(summary)) {
                        err_summ.add("No Summary for Risk Factors ("+(i+1)+")");
                    }
                    dump = Util.addLine(dump, summary);
                    */
                dump = Util.addLine(dump, getResidual(rFactors[i].getResidualInfo()));
                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(hostNoteId, cmNote);
                }

                //extra fields
                CaseManagementNoteExt cme = new CaseManagementNoteExt();
                cme.setNoteId(hostNoteId);
                if (rFactors[i].getStartDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.STARTDATE);
                    cme.setDateValue(dateFPtoDate(rFactors[i].getStartDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(rFactors[i].getStartDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (rFactors[i].getEndDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.RESOLUTIONDATE);
                    cme.setDateValue(dateFPtoDate(rFactors[i].getEndDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(rFactors[i].getEndDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (rFactors[i].getAgeOfOnset() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.AGEATONSET);
                    cme.setDateValue((Date) null);
                    cme.setValue(rFactors[i].getAgeOfOnset().toString());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (StringUtils.filled(rFactors[i].getExposureDetails())) {
                    cme.setKeyVal(CaseManagementNoteExt.EXPOSUREDETAIL);
                    cme.setDateValue((Date) null);
                    cme.setValue(rFactors[i].getExposureDetails());
                    caseManagementManager.saveNoteExt(cme);
                }
                if (rFactors[i].getLifeStage() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.LIFESTAGE);
                    cme.setDateValue((Date) null);
                    cme.setValue(rFactors[i].getLifeStage().toString());
                    caseManagementManager.saveNoteExt(cme);
                }
            }

            //ALERTS & SPECIAL NEEDS
            AlertsAndSpecialNeeds[] alerts = patientRec.getAlertsAndSpecialNeedsArray();
            for (int i = 0; i < alerts.length; i++) {
                if (i == 0) scmi = getCMIssue("Reminders");
                CaseManagementNote cmNote = prepareCMNote("1", null);
                cmNote.setIssues(scmi);

                //main field
                String reminders = alerts[i].getAlertDescription();
                if (StringUtils.empty(reminders)) {
                    err_data.add("Error! No Alert Description (" + (i + 1) + ")");
                    reminders = "Imported Alert";
                }
                cmNote.setNote(reminders);
                caseManagementManager.saveNoteSimple(cmNote);
                addOneEntry(ALERT);

                //annotation
                Long hostNoteId = cmNote.getId();
                cmNote = prepareCMNote("2", null);
                String note = alerts[i].getNotes();
                cmNote.setNote(note);
                saveLinkNote(hostNoteId, cmNote);

                //to dumpsite
                String dump = "imported.cms4.2011.06";
                    /*
                    String summary = alerts[i].getCategorySummaryLine();
                    if (StringUtils.empty(summary)) {
                            err_summ.add("No Summary for Alerts & Special Needs ("+(i+1)+")");
                    }
                    dump = Util.addLine(dump, summary);
                    */
                dump = Util.addLine(dump, getResidual(alerts[i].getResidualInfo()));
                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(hostNoteId, cmNote);
                }

                //extra fields
                CaseManagementNoteExt cme = new CaseManagementNoteExt();
                cme.setNoteId(hostNoteId);
                if (alerts[i].getDateActive() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.STARTDATE);
                    cme.setDateValue(dateFPtoDate(alerts[i].getDateActive(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(alerts[i].getDateActive()));
                    caseManagementManager.saveNoteExt(cme);
                }
                if (alerts[i].getEndDate() != null) {
                    cme.setKeyVal(CaseManagementNoteExt.RESOLUTIONDATE);
                    cme.setDateValue(dateFPtoDate(alerts[i].getEndDate(), timeShiftInDays));
                    cme.setValue(dateFPGetPartial(alerts[i].getEndDate()));
                    caseManagementManager.saveNoteExt(cme);
                }
            }

            //ALLERGIES & ADVERSE REACTIONS
            AllergiesAndAdverseReactions[] aaReactArray = patientRec.getAllergiesAndAdverseReactionsArray();
            for (int i = 0; i < aaReactArray.length; i++) {
                String description = "", regionalId = "", reaction = "", severity = "", entryDate = "", startDate = "", typeCode = "", lifeStage = "", alg_extra = "";
                String entryDateFormat = null, startDateFormat = null;
                boolean intolerant = false;
                boolean nonDrug = false;

                reaction = StringUtils.noNull(aaReactArray[i].getReaction());
                description = StringUtils.noNull(aaReactArray[i].getOffendingAgentDescription());
                entryDate = dateFPtoString(aaReactArray[i].getRecordedDate(), timeShiftInDays);
                startDate = dateFPtoString(aaReactArray[i].getStartDate(), timeShiftInDays);
                if (aaReactArray[i].getLifeStage() != null) lifeStage = aaReactArray[i].getLifeStage().toString();

                if (StringUtils.empty(entryDate)) entryDate = null;
                else entryDateFormat = dateFPGetPartial(aaReactArray[i].getRecordedDate());
                if (StringUtils.empty(startDate)) startDate = null;
                else startDateFormat = dateFPGetPartial(aaReactArray[i].getStartDate());

                if (aaReactArray[i].getCode() != null)
                    regionalId = StringUtils.noNull(aaReactArray[i].getCode().getCodeValue());
                //  alg_extra = Util.addLine(alg_extra,"Offending Agent Description: ",aaReactArray[i].getOffendingAgentDescription());
                if (aaReactArray[i].getReactionType() != null) {
                    //alg_extra = Util.addLine(alg_extra,"Reaction Type: ",aaReactArray[i].getReactionType().toString());
                    if (aaReactArray[i].getReactionType() == AdverseReactionType.AR) {
                        intolerant = true;
                    }
                }

                if (typeCode.equals("") && aaReactArray[i].getPropertyOfOffendingAgent() != null) {
                    if (aaReactArray[i].getPropertyOfOffendingAgent() == cdsDt.PropertyOfOffendingAgent.DR)
                        typeCode = "13"; //drug
                    else if (aaReactArray[i].getPropertyOfOffendingAgent() == cdsDt.PropertyOfOffendingAgent.ND) {
                        typeCode = "0"; //non-drug
                        nonDrug = true;
                    } else if (aaReactArray[i].getPropertyOfOffendingAgent() == cdsDt.PropertyOfOffendingAgent.UK)
                        typeCode = "0"; //unknown
                }
                if (aaReactArray[i].getSeverity() != null) {
                    if (aaReactArray[i].getSeverity() == cdsDt.AdverseReactionSeverity.MI) severity = "1"; //mild
                    else if (aaReactArray[i].getSeverity() == cdsDt.AdverseReactionSeverity.MO)
                        severity = "2"; //moderate
                    else if (aaReactArray[i].getSeverity() == cdsDt.AdverseReactionSeverity.LT) severity = "3"; //severe
                    else if (aaReactArray[i].getSeverity() == cdsDt.AdverseReactionSeverity.NO) {
                        severity = "4"; //No reaction, map to unknown
                        alg_extra = Util.addLine(alg_extra, "Severity: No reaction");
                    }
                } else {
                    severity = "4"; //severity unknown
                }

                Date entryDateDate = toDateFromString(entryDate);
                Date startDateDate = toDateFromString(startDate);

                Integer allergyId = saveRxAllergy(Integer.valueOf(demographicNo), entryDateDate, description, "".equals(typeCode) ? 0 : Integer.parseInt(typeCode), reaction, startDateDate, severity, regionalId, lifeStage, intolerant, nonDrug);
                addOneEntry(ALLERGY);

                //write partial dates
                if (entryDateFormat != null)
                    partialDateDao.setPartialDate(PartialDate.ALLERGIES, allergyId.intValue(), PartialDate.ALLERGIES_ENTRYDATE, entryDateFormat);
                if (startDateFormat != null)
                    partialDateDao.setPartialDate(PartialDate.ALLERGIES, allergyId.intValue(), PartialDate.ALLERGIES_STARTDATE, startDateFormat);

                //annotation
                String note = StringUtils.noNull(aaReactArray[i].getNotes());
                CaseManagementNote cmNote = prepareCMNote("2", null);
                cmNote.setNote(note);
                saveLinkNote(cmNote, CaseManagementNoteLink.ALLERGIES, Long.valueOf(allergyId));

                //to dumpsite
                String dump = "imported.cms4.2011.06";
                    /*
                    String summary = aaReactArray[i].getCategorySummaryLine();
                    if (StringUtils.empty(summary)) {
                        err_summ.add("No Summary for Allergies & Adverse Reactions ("+(i+1)+")");
                    }
                    dump = Util.addLine(dump, summary);
                    */
                dump = Util.addLine(dump, alg_extra);
                dump = Util.addLine(dump, getResidual(aaReactArray[i].getResidualInfo()));
                if (aaReactArray[i].getReactionType() != null)
                    dump = Util.addLine(dump, "Reaction Type=" + aaReactArray[i].getReactionType().toString()); //conditional added because some imports are missing this information and results in NPE

                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(cmNote, CaseManagementNoteLink.ALLERGIES, Long.valueOf(allergyId));
                }
            }


            //MEDICATIONS & TREATMENTS
            MedicationsAndTreatments[] medArray = patientRec.getMedicationsAndTreatmentsArray();
            for (int i = 0; i < medArray.length; i++) {
                String duration, quantity, dosage, special;
                Drug drug = new Drug();
                drug.setCreateDate(new Date());
                drug.setWrittenDate(dateTimeFPtoDate(medArray[i].getPrescriptionWrittenDate(), timeShiftInDays));
                String writtenDateFormat = dateFPGetPartial(medArray[i].getPrescriptionWrittenDate());

                drug.setRxDate(dateFPtoDate(medArray[i].getStartDate(), timeShiftInDays));


                if (medArray[i].getStartDate() == null) drug.setRxDate(drug.getWrittenDate());

                duration = medArray[i].getDuration();
                if (StringUtils.filled(duration)) {
                    duration = duration.trim();
                    if (duration.endsWith("days")) duration = Util.leadingNum(duration);
                    if (NumberUtils.isDigits(duration)) {
                        drug.setDuration(duration);
                        drug.setDurUnit("D");
                    } else err_data.add("Error! Invalid Duration [" + medArray[i].getDuration() + "] for Medications");
                }

                quantity = medArray[i].getQuantity();
                if (StringUtils.filled(quantity)) {
                    quantity = Util.leadingNum(quantity.trim());
                    if (NumberUtils.isNumber(quantity)) {
                        drug.setQuantity(quantity);
                    } else err_data.add("Error! Invalid Quantity [" + medArray[i].getQuantity() + "] for Medications");
                }

                Calendar endDate = Calendar.getInstance();
                endDate.setTime(drug.getRxDate());
                if (StringUtils.filled(duration))
                    endDate.add(Calendar.DAY_OF_YEAR, Integer.valueOf(duration) + timeShiftInDays);
                drug.setEndDate(endDate.getTime());

                String freq = StringUtils.noNull(medArray[i].getFrequency());
                int prnPos = freq.toUpperCase().indexOf("PRN");
                if (prnPos >= 0) {
                    drug.setPrn(true);
                    freq = freq.substring(0, prnPos).trim() + " " + freq.substring(prnPos + 3).trim(); //remove "prn" from freq
                }
                drug.setFreqCode(freq);

                drug.setFreqCode(medArray[i].getFrequency());
                if (medArray[i].getFrequency() != null && medArray[i].getFrequency().contains("PRN")) drug.setPrn(true);
                else drug.setPrn(false);

                drug.setRegionalIdentifier(medArray[i].getDrugIdentificationNumber());
                drug.setRoute(medArray[i].getRoute());
                drug.setDrugForm(medArray[i].getForm());
                drug.setLongTerm(getYN(medArray[i].getLongTermMedication()).equals("Yes"));
                drug.setPastMed(getYN(medArray[i].getPastMedications()).equals("Yes"));
                drug.setPatientCompliance(getBoolean(medArray[i].getPatientCompliance()));

                if (NumberUtils.isDigits(medArray[i].getNumberOfRefills()))
                    drug.setRepeat(Integer.valueOf(medArray[i].getNumberOfRefills()));
                duration = medArray[i].getRefillDuration();
                if (StringUtils.filled(duration)) {
                    duration = duration.trim();
                    if (duration.endsWith("days")) duration = Util.leadingNum(duration);
                    if (NumberUtils.isDigits(duration)) drug.setRefillDuration(Integer.valueOf(duration));
                    else
                        err_data.add("Error! Invalid Refill Duration [" + medArray[i].getRefillDuration() + "] for Medications");
                }

                quantity = medArray[i].getRefillQuantity();
                if (StringUtils.filled(quantity)) {
                    quantity = Util.leadingNum(quantity.trim());
                    if (NumberUtils.isNumber(quantity)) drug.setRefillQuantity(Integer.valueOf(quantity));
                    else
                        err_data.add("Error! Invalid Refill Quantity [" + medArray[i].getRefillQuantity() + "] for Medications");
                }

                drug.setETreatmentType(medArray[i].getTreatmentType());
                //no need: DrugReason drugReason = new DrugReason();

                drug.setRxStatus(medArray[i].getPrescriptionStatus());

                String nosub = medArray[i].getSubstitutionNotAllowed();
                if (nosub != null && "Y".equals(nosub)) drug.setNoSubs(nosub.equalsIgnoreCase("Y"));

                String non_auth = medArray[i].getNonAuthoritativeIndicator();
                if (non_auth != null && "Y".equals(non_auth)) drug.setNonAuthoritative(non_auth.equalsIgnoreCase("Y"));
                //  else  err_data.add("Error! No non-authoritative indicator for Medications & Treatments ("+(i+1)+")");

                drug.setDispenseInterval(medArray[i].getDispenseInterval() != null ? medArray[i].getDispenseInterval() : "");
                //  else err_data.add("Error! Invalid Dispense Interval for Medications & Treatments ("+(i+1)+")");

                String protocolIdentifier = medArray[i].getProtocolIdentifier();
                if (protocolIdentifier != null) {
                    drug.setProtocol(protocolIdentifier);
                }

                String priorRxProtocol = medArray[i].getPriorPrescriptionReferenceIdentifier();
                if (priorRxProtocol != null) {
                    drug.setPriorRxProtocol(priorRxProtocol);
                }

                String take = StringUtils.noNull(medArray[i].getDosage()).trim();
                drug.setTakeMin(Util.leadingNumF(take));
                int sep = take.indexOf("-");
                if (sep > 0) drug.setTakeMax(Util.leadingNumF(take.substring(sep + 1)));
                else drug.setTakeMax(drug.getTakeMin());
                drug.setUnit(medArray[i].getDosageUnitOfMeasure());
                if ("table".equalsIgnoreCase(drug.getUnit()) || "tablet".equalsIgnoreCase(drug.getUnit()))
                    drug.setUnit("tab");

                drug.setDemographicId(Integer.valueOf(demographicNo));
                drug.setArchived(false);

                boolean custom = false;
                if (!StringUtils.isNullOrEmpty(medArray[i].getDrugIdentificationNumber())) {
                    drug.setBrandName(medArray[i].getDrugName());
                } else {
                    drug.setCustomName(medArray[i].getDrugName());
                    drug.setSpecialInstruction(medArray[i].getDrugDescription());
                    custom = true;
                }

                if (StringUtils.filled(medArray[i].getPrescriptionInstructions())) {
                    drug.setSpecialInstruction(medArray[i].getPrescriptionInstructions());
                    if (custom) {
                        drug.setSpecialInstruction(medArray[i].getPrescriptionInstructions() + " " + medArray[i].getDrugDescription());
                    }
                }


                special = StringUtils.noNull(drug.getBrandName());
                if (special.equals("")) {
                    special = StringUtils.noNull(drug.getCustomName());
                    drug.setCustomInstructions(true);
                }

                cdsDt.DrugMeasure strength = medArray[i].getStrength();
                if (strength != null) {
                    String dosageValue = StringUtils.noNull(strength.getAmount());
                    String dosageUnit = StringUtils.noNull(strength.getUnitOfMeasure());

                    String[] dValue = new String[0];
                    String[] dUnit = new String[0];
                    // cannot depend on the exporter to send actual values.  ie: cannot split a single "/"
                    StringBuilder stringBuilder = new StringBuilder();

                    if (dosageValue.contains("/")) {
                        dValue = dosageValue.split("/");
                    } else {
                        stringBuilder.append(dosageValue);
                    }

                    if ((dosageUnit.contains("/") && dosageValue.contains("/")) || (!dosageValue.contains("/") && dosageUnit.contains("/"))) {
                        dUnit = dosageUnit.split("/");
                    } else {
                        stringBuilder.append(dosageUnit);
                    }

                    if (dValue.length > 0) {
                        stringBuilder.append(dValue[0].trim());
                    }

                    if (dUnit.length > 0) {
                        stringBuilder.append(dUnit[0].trim());
                    }

                    if (dValue.length > 1) {
                        stringBuilder.append(" / ").append(dValue[1].trim());
                    }

                    if (dUnit.length > 1) {
                        stringBuilder.append(dUnit[1].trim());
                    }

                    drug.setDosage(stringBuilder.toString());
                }

                special = addSpaced(special, medArray[i].getDosage());
                special = addSpaced(special, drug.getRoute());
                special = addSpaced(special, drug.getFreqCode());

                if (drug.getDuration() != null) {
                    special = addSpaced(special, "for " + drug.getDuration() + " days");
                }
                drug.setSpecial(special);


                if (medArray[i].getPrescribedBy() != null) {
                    HashMap<String, String> personName = getPersonName(medArray[i].getPrescribedBy().getName());
                    String personOHIP = medArray[i].getPrescribedBy().getOHIPPhysicianId();
                    // writeProviderData(personName.get("firstname"), personName.get("lastname"), personOHIP, null);
                    ProviderData pd = getProviderByOhip(personOHIP);
                    if (pd != null && Integer.valueOf(pd.getProviderNo()) > -1000)
                        drug.setProviderNo(pd.getProviderNo());
                    else { //outside providers
                        drug.setOutsideProviderName(StringUtils.noNull(personName.get("lastname")) + ", " + StringUtils.noNull(personName.get("firstname")));
                        drug.setOutsideProviderOhip(personOHIP);
                        drug.setProviderNo(writeProviderData(personName.get("firstname"), personName.get("lastname"), personOHIP));
                    }
                } else {
                    drug.setProviderNo(admProviderNo);
                }

                if (drug.getProviderNo() == null) {
                    drug.setProviderNo("-1");
                }

                drug.setPosition(0);

                //use drugref to add more info to the record
                if (!StringUtils.isNullOrEmpty(drug.getRegionalIdentifier())) {
                    try {
                        RxDrugData rxDrugData = new RxDrugData();
                        DrugMonograph dm = rxDrugData.getDrugByDIN(drug.getRegionalIdentifier());
                        if (dm != null) {
                            drug.setAtc(dm.getAtc());
	                    		if(dm.drugId != null) {
	                    			drug.setGcnSeqNo(dm.drugId + "");
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error looking up DIN");
                    }
                }


                drugDao.persist(drug);

                if (!StringUtils.isNullOrEmpty(dateFPGetPartial(medArray[i].getPrescriptionWrittenDate())))
                    partialDateDao.setPartialDate(PartialDate.DRUGS, drug.getId(), PartialDate.DRUGS_WRITTENDATE, dateFPGetPartial(medArray[i].getPrescriptionWrittenDate()));
                if (!StringUtils.isNullOrEmpty(dateFPGetPartial(medArray[i].getStartDate())))
                    partialDateDao.setPartialDate(PartialDate.DRUGS, drug.getId(), PartialDate.DRUGS_STARTDATE, dateFPGetPartial(medArray[i].getStartDate()));

                addOneEntry(MEDICATION);


                if (medArray[i].getProblemCode() != null) {
                    DrugReason drugReason = new DrugReason();
                    drugReason.setCodingSystem("icd9"); //a guess here
                    drugReason.setCode(medArray[i].getProblemCode());
                    drugReason.setDemographicNo(Integer.valueOf(demographicNo));
                    drugReason.setDrugId(drug.getId());
                    drugReason.setProviderNo(drug.getProviderNo());
                    drugReason.setPrimaryReasonFlag(true);
                    drugReason.setArchivedFlag(false);
                    drugReasonDao.persist(drugReason);
                }


                //partial date
                partialDateDao.setPartialDate(PartialDate.DRUGS, drug.getId(), PartialDate.DRUGS_WRITTENDATE, writtenDateFormat);

                //annotation
                CaseManagementNote cmNote = prepareCMNote("2", null);
                String note = StringUtils.noNull(medArray[i].getNotes());
                cmNote.setNote(note);
                saveLinkNote(cmNote, CaseManagementNoteLink.DRUGS, (long) drug.getId());

                //to dumpsite
                String dump = "imported.cms4.2011.06";
                dump = Util.addLine(dump, getResidual(medArray[i].getResidualInfo()));
                dump = Util.addLine(dump, "Prescription Id: ", medArray[i].getPrescriptionIdentifier());

                if (!"imported.cms4.2011.06".equals(dump)) {
                    cmNote = prepareCMNote("2", null);
                    cmNote.setNote(dump);
                    saveLinkNote(cmNote, CaseManagementNoteLink.DRUGS, (long) drug.getId());
                }
            }


            //IMMUNIZATIONS
            Immunizations[] immuArray = patientRec.getImmunizationsArray();
            for (int i = 0; i < immuArray.length; i++) {
                String preventionDate = "", refused = "0";
                String preventionType = null, immExtra = null;
                ArrayList<Map<String, String>> preventionExt = new ArrayList<Map<String, String>>();

                if (StringUtils.filled(immuArray[i].getImmunizationName())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("name", immuArray[i].getImmunizationName());
                    preventionExt.add(ht);
                } else {
                    err_data.add("Error! No Immunization Name (" + (i + 1) + ")");
                }

                if (immuArray[i].getImmunizationType() != null)
                    preventionType = Util.getPreventionType(immuArray[i].getImmunizationType().toString());
//					if (preventionType==null)
//                    	preventionType = mapPreventionTypeByCode(immuArray[i].getImmunizationCode());
                if (preventionType == null) {
                    preventionType = "OtherA";
                    err_note.add("Cannot map Immunization Type, " + immuArray[i].getImmunizationName() + " mapped to Other Layout A");
                }

                Map<String, String> iht = new HashMap<String, String>();
                iht.put("imported", "true");
                preventionExt.add(iht);

                if (StringUtils.filled(immuArray[i].getManufacturer())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("manufacture", immuArray[i].getManufacturer());
                    preventionExt.add(ht);
                }
                if (StringUtils.filled(immuArray[i].getLotNumber())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("lot", immuArray[i].getLotNumber());
                    preventionExt.add(ht);
                }
                if (StringUtils.filled(immuArray[i].getRoute())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("route", tryToMapRoute(immuArray[i].getRoute()));
                    preventionExt.add(ht);
                }
                if (StringUtils.filled(immuArray[i].getSite())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("location", "Other");
                    preventionExt.add(ht);

                    Map<String, String> ht2 = new HashMap<String, String>();
                    ht2.put("location2", immuArray[i].getSite());
                    preventionExt.add(ht2);
                }
                if (StringUtils.filled(immuArray[i].getDose())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("dose", immuArray[i].getDose());
                    preventionExt.add(ht);
                }

                if (StringUtils.filled(immuArray[i].getNotes())) {
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("comments", immuArray[i].getNotes());
                    preventionExt.add(ht);
                }


                preventionDate = dateFPtoString(immuArray[i].getDate(), timeShiftInDays);


                refused = getYN(immuArray[i].getRefusedFlag()).equals("Yes") ? "1" : "0";
                if (immuArray[i].getRefusedFlag() == null)
                    err_data.add("Error! No Refused Flag for Immunizations (" + (i + 1) + ")");

                String din = null;
                if (immuArray[i].getImmunizationCode() != null && "DIN".equals(immuArray[i].getImmunizationCode().getCodingSystem())) {
                    din = immuArray[i].getImmunizationCode().getValue();
                    Map<String, String> ht = new HashMap<String, String>();
                    ht.put("din", din);
                    preventionExt.add(ht);
                }

                immExtra = Util.addLine(immExtra, getCode(immuArray[i].getImmunizationCode(), "Immunization Code"));
                immExtra = Util.addLine(immExtra, "Instructions: ", immuArray[i].getInstructions());
                immExtra = Util.addLine(immExtra, getResidual(immuArray[i].getResidualInfo()));

                if ("".equals(tryToMapRoute(immuArray[i].getRoute()))) {
                    immExtra = Util.addLine(immExtra, "Unmapped Route: ", immuArray[i].getRoute());
                }

                Integer preventionId = PreventionData.insertPreventionData(admProviderNo, demographicNo, preventionDate, defaultProviderNo(), "", preventionType, refused, "", "", preventionExt, null, din);

                if (!StringUtils.isNullOrEmpty(dateFPGetPartial(immuArray[i].getDate())))
                    partialDateDao.setPartialDate(PartialDate.PREVENTION, preventionId, PartialDate.PREVENTION_PREVENTIONDATE, dateFPGetPartial(immuArray[i].getDate()));


                addOneEntry(IMMUNIZATION);

                //to dumpsite: Extra immunization data
                if (StringUtils.filled(immExtra) && preventionId >= 0) {
                    immExtra = Util.addLine("imported.cms4.2011.06", immExtra);
                    CaseManagementNote imNote = prepareCMNote("2", null);
                    imNote.setNote(immExtra);
                    saveLinkNote(imNote, CaseManagementNoteLink.PREVENTIONS, Long.valueOf(preventionId));
                }
            }

            //LABORATORY RESULTS
            LaboratoryResults[] labResultArr = patientRec.getLaboratoryResultsArray();
            importLabs(loggedInInfo, labResultArr);


            //APPOINTMENTS
            Appointments[] appArray = patientRec.getAppointmentsArray();
            //   ApptStatusData asd = new ApptStatusData();
            //   String[] allStatus = asd.getAllStatus();
            //   String[] allTitle = asd.getAllTitle();
            AppointmentStatusDao appointmentStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);
            List<AppointmentStatus> appointmentStatusList = appointmentStatusDao.findAll();

            for (int i = 0; i < appArray.length; i++) {
                Date appointmentDate = null;
                String notes = "", reason = "", status = "", startTime = "", endTime = "", apptProvider = "";


                String apptDateStr = dateFPtoString(appArray[i].getAppointmentDate(), timeShiftInDays);
                if (StringUtils.filled(apptDateStr)) {
                    appointmentDate = UtilDateUtilities.StringToDate(apptDateStr);
                } else {
                    err_data.add("Error! No Appointment Date (" + (i + 1) + ")");
                }
                if (appArray[i].getAppointmentTime() != null) {
                    startTime = getCalTime(appArray[i].getAppointmentTime());
                    if (appArray[i].getDuration() != null) {
                        Date d_startTime = appArray[i].getAppointmentTime().getTime();
                        Date d_endTime = new Date();
                        d_endTime.setTime(d_startTime.getTime() + (appArray[i].getDuration().longValue() - 1) * 60000);
                        endTime = UtilDateUtilities.DateToString(d_endTime, "HH:mm:ss");
                    } else {
                        Date d_startTime = appArray[i].getAppointmentTime().getTime();
                        Date d_endTime = new Date();
                        d_endTime.setTime(d_startTime.getTime() + 14 * 60000);
                        endTime = UtilDateUtilities.DateToString(d_endTime, "HH:mm:ss");
                    }
                } else {
                    err_data.add("Error! No Appointment Time (" + (i + 1) + ")");
                }
                if (StringUtils.filled(appArray[i].getAppointmentNotes())) {
                    notes = appArray[i].getAppointmentNotes();
                }
                String apptStatus = appArray[i].getAppointmentStatus();

                boolean failedToMapStatus = false;

                if (apptStatus != null) {
                    for (AppointmentStatus as : appointmentStatusList) {
                        if (isMappedStatus(as, apptStatus) != null) {
                            status = as.getStatus();
                            break;
                        }
                    }
                    if (StringUtils.empty(status)) {
                        AppointmentStatus tmp = createNewAppointmentStatus(apptStatus);

                        status = tmp.getStatus();
                        //	err_note.add("Cannot map appointment status ["+apptStatus+"]. Appointment Status set to [Imported]");
                        //	failedToMapStatus=true;
                    }
                } else {
                    status = "t";
                }

                reason = StringUtils.noNull(appArray[i].getAppointmentPurpose());
                if (appArray[i].getProvider() != null) {
                    HashMap<String, String> providerName = getPersonName(appArray[i].getProvider().getName());
                    String personOHIP = appArray[i].getProvider().getOHIPPhysicianId();
                    apptProvider = writeProviderData(providerName.get("firstname"), providerName.get("lastname"), personOHIP);
                    if (StringUtils.empty(apptProvider)) {
                        apptProvider = defaultProviderNo();
                        err_note.add("Appointment has no providers; assigned to \"doctor oscardoc\" (" + (i + 1) + ")");
                    }
                }

                Appointment appt = new Appointment();
                appt.setProviderNo(apptProvider);
                appt.setAppointmentDate(appointmentDate);
                appt.setStartTime(ConversionUtils.fromTimeString(startTime));
                appt.setEndTime(ConversionUtils.fromTimeString(endTime));
                appt.setName(patientName);
                appt.setDemographicNo(Integer.parseInt(demographicNo));
                appt.setNotes(notes);
                appt.setReason(reason);
                appt.setStatus(status);
                appt.setImportedStatus(apptStatus);
                appt.setLocation("");
                appt.setCreator(loggedInInfo.getLoggedInProvider().getFormattedName());
                appt.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
                appt.setResources("");
                appt.setType("");
                appointmentDao.persist(appt);

                addOneEntry(APPOINTMENT);

                if (failedToMapStatus) {
                    String dump = Util.addLine("imported.cms4.2011.06", "Appointment Status:", apptStatus);
                    CaseManagementNote imNote = prepareCMNote("2", null);
                    imNote.setNote(dump);
                    saveLinkNote(imNote, CaseManagementNoteLink.APPOINTMENT, Long.valueOf(appt.getId()));
                }

            }

            //REPORTS RECEIVED

            HRMDocumentDao hrmDocDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
            HRMDocumentCommentDao hrmDocCommentDao = (HRMDocumentCommentDao) SpringUtils.getBean(HRMDocumentCommentDao.class);
            HRMDocumentSubClassDao hrmDocSubClassDao = (HRMDocumentSubClassDao) SpringUtils.getBean(HRMDocumentSubClassDao.class);
            HRMDocumentToDemographicDao hrmDocToDemoDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);

            Reports[] repR = patientRec.getReportsArray();
            List<Reports> HRMreports = new ArrayList<Reports>();
            for (int i = 0; i < repR.length; i++) {
                Reports reportItem = repR[i];

                if (repR[i].getHRMResultStatus() != null /*|| repR[i].getOBRContentArray().length>0*/) { //HRM reports   //commenting out the OBR content array check because PDFs might have OBR content as well.
                    String HRMfile = "HRM_" + UtilDateUtilities.getToday("yyyy-MM-dd.HH.mm.ss") + "_" + repR[i].getSendingFacilityId() + "_" + repR[i].getSendingFacilityReport() + ".xml";
                    HRMfile = HRMfile.replace("/", ""); //stripping out / because sometimes the impored data (E.g. getSendingFacilityReport) has / within them and that messes up the file path

                    HRMDocument hrmDoc = new HRMDocument();
                    HRMDocumentComment hrmDocComment = new HRMDocumentComment();
                    HRMDocumentToDemographic hrmDocToDemo = new HRMDocumentToDemographic();

                    hrmDoc.setReportFile(HRMfile);
                    if (repR[i].getSourceFacility() != null) hrmDoc.setSourceFacility(repR[i].getSourceFacility());
                    if (repR[i].getReceivedDateTime() != null) {
                        hrmDoc.setTimeReceived(dateTimeFPtoDate(repR[i].getReceivedDateTime(), timeShiftInDays));
                    } else {
                        hrmDoc.setTimeReceived(new Date());
                    }
                    if (repR[i].getHRMResultStatus() != null) hrmDoc.setReportStatus(repR[i].getHRMResultStatus());
                    if (repR[i].getClass1() != null) hrmDoc.setReportType(repR[i].getClass1().toString());
                    if (repR[i].getEventDateTime() != null)
                        hrmDoc.setReportDate(dateTimeFPtoDate(repR[i].getEventDateTime(), timeShiftInDays));


                    hrmDoc.setSourceFacility(repR[i].getSourceFacility());
                    hrmDoc.setDescription("");
                    hrmDoc.setFormattedName(demographic.getFormattedName());
                    hrmDoc.setDob(demographic.getFormattedDob());
                    hrmDoc.setGender(demographic.getSex());
                    hrmDoc.setHcn(demographic.getHin());

                    hrmDoc.setClassName(reportItem.getClass1().toString());
                    hrmDoc.setSubClassName(reportItem.getSubClass());

                    hrmDoc.setRecipientId("");
                    hrmDoc.setRecipientName("");

                    //    hrmDoc.setHrmCategoryId(hrmCategoryId);

                    //    hrmDoc.setSourceFacilityReportNo(repR[i].getSendingFacilityReport());

                    hrmDocDao.persist(hrmDoc);

                    if (repR[i].getNotes() != null) {
                        hrmDocComment.setHrmDocumentId(hrmDoc.getId());
                        hrmDocComment.setComment(repR[i].getNotes());
                        hrmDocCommentDao.persist(hrmDocComment);
                    }

                    hrmDocToDemo.setDemographicNo(Integer.parseInt(demographicNo));
                    hrmDocToDemo.setHrmDocumentId(hrmDoc.getId());
                    hrmDocToDemoDao.persist(hrmDocToDemo);

                    for (Reports.ReportReviewed reportReviewed : repR[i].getReportReviewedArray()) {

                        HashMap<String, String> reviewerName = getPersonName(reportReviewed.getName());
                        String reviewer = writeProviderData(reviewerName.get("firstname"), reviewerName.get("lastname"), reportReviewed.getReviewingOHIPPhysicianId());
                        String reviewDateTime = dateFPtoString(reportReviewed.getDateTimeReportReviewed(), timeShiftInDays);

                        HRMDocumentToProvider hrmDocProvider = new HRMDocumentToProvider();
                        hrmDocProvider.setHrmDocumentId(hrmDoc.getId());
                        hrmDocProvider.setProviderNo(reviewer);
                        hrmDocProvider.setSignedOff(1);

                        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");

                        try {
                            hrmDocProvider.setSignedOffTimestamp(f.parse(reviewDateTime));
                        } catch (ParseException e) {

                        }

                        HRMDocumentToProviderDao hRMDocumentToProviderDao = SpringUtils.getBean(HRMDocumentToProviderDao.class);
                        hRMDocumentToProviderDao.persist(hrmDocProvider);
                    }

                    if (repR[i].getRecipientName() != null) {
                        PersonNameSimple recipient = repR[i].getRecipientName();
                        recipient.getLastName();
                        recipient.getFirstName();
                    }
                    OBRContent[] obr = repR[i].getOBRContentArray();
                    for (int j = 0; j < obr.length; j++) {
                        HRMDocumentSubClass hrmDocSc = new HRMDocumentSubClass();
                        if (obr[j].getAccompanyingSubClass() != null)
                            hrmDocSc.setSubClass(obr[j].getAccompanyingSubClass());
                        if (obr[j].getAccompanyingDescription() != null)
                            hrmDocSc.setSubClassDescription(obr[j].getAccompanyingDescription());
                        if (obr[j].getAccompanyingMnemonic() != null)
                            hrmDocSc.setSubClassMnemonic(obr[j].getAccompanyingMnemonic());
                        if (obr[j].getObservationDateTime() != null)
                            hrmDocSc.setSubClassDateTime(dateTimeFPtoDate(obr[j].getObservationDateTime(), timeShiftInDays));
                        hrmDocSc.setHrmDocumentId(hrmDoc.getId());
                        hrmDocSc.setActive(j == 0 ? true : false);
                        hrmDocSubClassDao.persist(hrmDocSc);
                    }
                    HRMreports.clear();
                    HRMreports.add(repR[i]);
                    CreateHRMFile.create(demo, HRMreports, HRMfile);

                    HRMReport report = HRMReportParser.parseReport(loggedInInfo, hrmDoc.getReportFile());
                    String reportFileData = report.getFileData();

                    String noMessageIdFileData = reportFileData.replaceAll("<MessageUniqueID>.*?</MessageUniqueID>", "<MessageUniqueID></MessageUniqueID>");
                    String noTransactionInfoFileData = reportFileData.replaceAll("<TransactionInformation>.*?</TransactionInformation>", "<TransactionInformation></TransactionInformation>");
                    String noDemograhpicInfoFileData = reportFileData.replaceAll("<Demographics>.*?</Demographics>", "<Demographics></Demographics").replaceAll("<MessageUniqueID>.*?</MessageUniqueID>", "<MessageUniqueID></MessageUniqueID>");

                    String noMessageIdHash = DigestUtils.md5Hex(noMessageIdFileData);
                    String noTransactionInfoHash = DigestUtils.md5Hex(noTransactionInfoFileData);
                    String noDemographicInfoHash = DigestUtils.md5Hex(noDemograhpicInfoFileData);

                    hrmDoc.setReportHash(noMessageIdHash);
                    hrmDoc.setReportLessTransactionInfoHash(noTransactionInfoHash);
                    hrmDoc.setReportLessDemographicInfoHash(noDemographicInfoHash);

                    hrmDocDao.merge(hrmDoc);
                } else { //non-HRM reports
                    boolean binaryFormat = false;
                    if (repR[i].getFormat() != null) {
                        if (repR[i].getFormat().equals(cdsDt.ReportFormat.BINARY)) binaryFormat = true;
                    } else {
                        err_data.add("Error! No Report Format for Report (" + (i + 1) + ")");
                    }
                    cdsDt.ReportContent repCt = repR[i].getContent();
                    String filePath = repR[i].getFilePath();
                    if (repCt != null || filePath != null) {

                        byte[] b = null;
                        if (repCt != null && repCt.getMedia() != null) b = repCt.getMedia();
                        else if (repCt != null && repCt.getTextContent() != null) {
                            // TextContent may contain base64-encoded binary data (for images, PDFs, etc.)
                            // or actual text content. Check if it's binary format and decode accordingly.
                            if (binaryFormat) {
                                // Binary formats (images, PDFs, etc.) are base64-encoded in TextContent
                                b = Base64.decodeBase64(repCt.getTextContent());
                            } else {
                                // Text formats are stored as-is
                                b = repCt.getTextContent().getBytes();
                            }
                        }
                        if (b == null && filePath == null) {
                            err_othe.add("Error! No report file in xml (" + (i + 1) + ")");
                        } else {
                            String docFileName = "ImportReport" + (i + 1) + "-" + UtilDateUtilities.getToday("yyyy-MM-dd.HH.mm.ss");
                            String docClass = null, docSubClass = null, contentType = "", contentDateTime = null, observationDate = null, updateDateTime = null, docCreator = admProviderNo;
                            String reviewer = null, reviewDateTime = null, source = null, sourceFacility = null, reportExtra = null;
                            Integer docNum = null;
                            String docType = "";
                            if (repR[i].getMedia() != null) docType = repR[i].getMedia().toString();

                            if (StringUtils.filled(repR[i].getFileExtensionAndVersion())) {
                                contentType = repR[i].getFileExtensionAndVersion();
                                docFileName += Util.mimeToExt(contentType);
                            } else {
                                if (binaryFormat) err_data.add("Error! No File Extension for Report (" + (i + 1) + ")");
                            }
                            String docDesc = repR[i].getSubClass();

                            if (StringUtils.empty(docDesc)) { //improving the naming of documents where the document name is in the notes tag of the import
                                String documentNotes = repR[i].getNotes();

                                //setting docDesc assuming the format is usually in the form "Document Health Card created at Mon Mar 05 12:50:17 EST 2018 by Verona V." and naming docDesc = "Health Card" would be desirable in this situation
                                if (documentNotes != null && documentNotes.indexOf("created") > 1)
                                    docDesc = documentNotes.split("created")[0].trim();
                                if (docDesc != null && docDesc.indexOf("Document") == 0 && docDesc.split("Document").length >= 2)
                                    docDesc = docDesc.split("Document")[1].trim();
                            }

                            if (StringUtils.empty(docDesc)) docDesc = "ImportReport" + (i + 1);

                            if (b != null) {
                                FileOutputStream f = new FileOutputStream(docDir + docFileName);
                                f.write(b);
                                f.close();
                            } else {
                                // filePath is already declared at line 2617
                                if (filePath == null || filePath.isEmpty()) {
                                    err_data.add("Error! No file path for Report (" + (i + 1) + ")");
                                    continue;
                                }

                                // Resolve and validate the source file
                                // This returns only regular files (not directories) suitable for FileUtils.copyFile
                                File sourceFile = resolveReportSourceFile(currentDirectory, filePath, contentType);
                                if (sourceFile == null) {
                                    err_data.add("Error! Cannot locate file for Report (" + (i + 1) + ")");
                                    continue;
                                }

                                // Validate the resolved sourceFile is within the allowed extraction directory
                                // This prevents path traversal attacks from malicious XML file paths
                                try {
                                    File allowedRoot = new File(currentDirectory);
                                    PathValidationUtils.validateExistingPath(sourceFile, allowedRoot);
                                } catch (SecurityException e) {
                                    logger.error("SECURITY: Rejecting file copy - resolved path outside allowed directory. FilePath: {}, SourceFile: {}",
                                        Encode.forJava(new File(filePath).getName()),
                                        Encode.forJava(sourceFile.getName()),
                                        e);
                                    err_data.add("Error! Security violation for Report (" + (i + 1) + "): Invalid file path");
                                    continue;
                                }

                                FileUtils.copyFile(sourceFile, new File(docDir + docFileName));
                            }

                            if (repR[i].getClass1() != null) {
                                docClass = repR[i].getClass1().toString();
                            } else {
                                err_data.add("Error! No Class Type for Report (" + (i + 1) + ")");
                            }
                            if (repR[i].getSubClass() != null) {
                                docSubClass = repR[i].getSubClass();
                            }

                            if (repR[i].getSourceFacility() != null) {
                                sourceFacility = repR[i].getSourceFacility();
                            }

                            if (repR[i].getMedia() != null) {
                                reportExtra = Util.addLine(reportExtra, "Media: ", repR[i].getMedia().toString());
                            }

                            SourceAuthorPhysician authorPhysician = repR[i].getSourceAuthorPhysician();
                            if (authorPhysician != null) {
                                if (authorPhysician.getAuthorName() != null) {
                                    HashMap<String, String> author = getPersonName(authorPhysician.getAuthorName());
                                    source = StringUtils.noNull(author.get("firstname")) + " " + StringUtils.noNull(author.get("lastname"));
                                } else if (authorPhysician.getAuthorFreeText() != null) {
                                    source = authorPhysician.getAuthorFreeText();
                                }
                            }

                            Reports.ReportReviewed[] reportReviewed = repR[i].getReportReviewedArray();
                            if (reportReviewed.length > 0) {
                                HashMap<String, String> reviewerName = getPersonName(reportReviewed[0].getName());
                                reviewer = writeProviderData(reviewerName.get("firstname"), reviewerName.get("lastname"), reportReviewed[0].getReviewingOHIPPhysicianId());
                                reviewDateTime = dateFPtoString(reportReviewed[0].getDateTimeReportReviewed(), timeShiftInDays);
                            }

                            observationDate = dateFPtoString(repR[i].getEventDateTime(), timeShiftInDays);
                            if (observationDate == null)
                                observationDate = dateFPtoString(repR[i].getReceivedDateTime(), timeShiftInDays);
                            if ("".equals(observationDate))
                                observationDate = dateFPtoString(repR[i].getReceivedDateTime(), timeShiftInDays);

                            updateDateTime = dateFPtoString(repR[i].getReceivedDateTime(), timeShiftInDays);
                            contentDateTime = dateFPtoString(repR[i].getEventDateTime(), timeShiftInDays);

                            String receivedDate = dateFPtoString(repR[i].getReceivedDateTime(), timeShiftInDays);

                            String responsibleId = admProviderNo;
                            //  DemographicDao dDao = SpringUtils.getBean(DemographicDao.class);
                            //  Demographic demographic =dDao.getDemographic(demographicNo);
                            if (demographic != null && !StringUtils.isNullOrEmpty(demographic.getProviderNo())) {
                                responsibleId = demographic.getProviderNo();
                            }
                            docNum = EDocUtil.addDocument(demographicNo, docFileName, docDesc, docType, docClass, docSubClass, mapContentType(contentType), contentDateTime, observationDate, updateDateTime, docCreator, responsibleId, reviewer, reviewDateTime, source, sourceFacility, receivedDate);
                            if (docNum == null) docNum = 0;
                            if (binaryFormat) addOneEntry(REPORTBINARY);
                            else addOneEntry(REPORTTEXT);


                            String notes = Util.addLine("Report Notes:", repR[i].getNotes());
                            if (!"Report Notes:".equals(notes)) {
                                CaseManagementNote rpNote1 = prepareCMNote("2", null);
                                rpNote1.setNote(notes);
                                saveLinkNote(rpNote1, CaseManagementNoteLink.DOCUMENT, Long.valueOf(docNum));
                            }

                            if (repR[i].getReportReviewedArray() != null && repR[i].getReportReviewedArray().length > 1) {
                                DocumentExtraReviewerDao derDao = SpringUtils.getBean(DocumentExtraReviewerDao.class);
                                for (int x = 1; x < repR[i].getReportReviewedArray().length; x++) {
                                    ReportReviewed rr = repR[i].getReportReviewedArray()[x];

                                    DocumentExtraReviewer der = new DocumentExtraReviewer();
                                    der.setDocumentNo(docNum);

                                    String extraReviewer = writeProviderData(rr.getName().getFirstName(), rr.getName().getLastName(), rr.getReviewingOHIPPhysicianId());
                                    //String extraReviewDateTime = dateFPtoString(rr.getDateTimeReportReviewed(), timeShiftInDays);


                                    der.setReviewDateTime(rr.getDateTimeReportReviewed().getFullDate().getTime());
                                    der.setReviewerProviderNo(extraReviewer);

                                    derDao.persist(der);
                                }
                            }
                            //to dumpsite: Extra report data
                            if (StringUtils.filled(reportExtra)) {
                                reportExtra = Util.addLine("imported.cms4.2011.06", reportExtra);
                                CaseManagementNote rpNote = prepareCMNote("2", null);
                                rpNote.setNote(reportExtra);
                                saveLinkNote(rpNote, CaseManagementNoteLink.DOCUMENT, Long.valueOf(docNum));
                            }
                        }
                    }
                }
            }

            //CARE ELEMENTS
            CareElements[] careElems = patientRec.getCareElementsArray();
            for (int i = 0; i < careElems.length; i++) {
                CareElements ce = careElems[i];
                cdsDt.Height[] heights = ce.getHeightArray();
                for (cdsDt.Height ht : heights) {
                    Date dateObserved = ht.getDate() != null ? ht.getDate().getTime() : null;
                    String dataField = StringUtils.noNull(ht.getHeight());
                    String dataUnit = ht.getHeightUnit() != null ? "in " + ht.getHeightUnit().toString() : "";

                    if (ht.getDate() == null)
                        err_data.add("Error! No Date for Height in Care Element (" + (i + 1) + ")");
                    if (StringUtils.empty(ht.getHeight()))
                        err_data.add("Error! No value for Height in Care Element (" + (i + 1) + ")");
                    if (ht.getHeightUnit() == null)
                        err_data.add("Error! No unit for Height in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("HT", demographicNo, admProviderNo, dataField, dataUnit, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.Weight[] weights = ce.getWeightArray();
                for (cdsDt.Weight wt : weights) {
                    Date dateObserved = wt.getDate() != null ? wt.getDate().getTime() : null;
                    String dataField = StringUtils.noNull(wt.getWeight());
                    String dataUnit = wt.getWeightUnit() != null ? "in " + wt.getWeightUnit().toString() : "";

                    if (wt.getDate() == null)
                        err_data.add("Error! No Date for Weight in Care Element (" + (i + 1) + ")");
                    if (StringUtils.empty(wt.getWeight()))
                        err_data.add("Error! No value for Weight in Care Element (" + (i + 1) + ")");
                    if (wt.getWeightUnit() == null)
                        err_data.add("Error! No unit for Weight in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("WT", demographicNo, admProviderNo, dataField, dataUnit, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.WaistCircumference[] waists = ce.getWaistCircumferenceArray();
                for (cdsDt.WaistCircumference wc : waists) {
                    Date dateObserved = wc.getDate() != null ? wc.getDate().getTime() : null;
                    String dataField = StringUtils.noNull(wc.getWaistCircumference());
                    String dataUnit = wc.getWaistCircumferenceUnit() != null ? "in " + wc.getWaistCircumferenceUnit().toString() : "";

                    if (wc.getDate() == null)
                        err_data.add("Error! No Date for Waist Circumference in Care Element (" + (i + 1) + ")");
                    if (StringUtils.empty(wc.getWaistCircumference()))
                        err_data.add("Error! No value for Waist Circumference in Care Element (" + (i + 1) + ")");
                    if (wc.getWaistCircumferenceUnit() == null)
                        err_data.add("Error! No unit for Waist Circumference in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("WAIS", demographicNo, admProviderNo, dataField, dataUnit, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.BloodPressure[] bloodp = ce.getBloodPressureArray();
                for (cdsDt.BloodPressure bp : bloodp) {
                    Date dateObserved = bp.getDate() != null ? bp.getDate().getTime() : null;
                    String dataField = StringUtils.noNull(bp.getSystolicBP()) + "/" + StringUtils.noNull(bp.getDiastolicBP());
                    String dataUnit = bp.getBPUnit() != null ? "in " + bp.getBPUnit().toString() : "";

                    if (bp.getDate() == null)
                        err_data.add("Error! No Date for Blood Pressure in Care Element (" + (i + 1) + ")");
                    if (StringUtils.empty(bp.getSystolicBP()))
                        err_data.add("Error! No value for Systolic Blood Pressure in Care Element (" + (i + 1) + ")");
                    if (StringUtils.empty(bp.getDiastolicBP()))
                        err_data.add("Error! No value for Diastolic Blood Pressure in Care Element (" + (i + 1) + ")");
                    if (bp.getBPUnit() == null)
                        err_data.add("Error! No unit for Blood Pressure in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("BP", demographicNo, admProviderNo, dataField, dataUnit, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.SmokingStatus[] smoks = ce.getSmokingStatusArray();
                for (cdsDt.SmokingStatus ss : smoks) {
                    Date dateObserved = ss.getDate() != null ? ss.getDate().getTime() : null;
                    String dataField = ss.getStatus() != null ? ss.getStatus().toString() : "";

                    if (ss.getDate() == null)
                        err_data.add("Error! No Date for Smoking Status in Care Element (" + (i + 1) + ")");
                    if (ss.getStatus() == null)
                        err_data.add("Error! No value for Smoking Status in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("SKST", demographicNo, admProviderNo, dataField, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.SmokingPacks[] smokp = ce.getSmokingPacksArray();
                for (cdsDt.SmokingPacks sp : smokp) {
                    Date dateObserved = sp.getDate() != null ? sp.getDate().getTime() : null;
                    String dataField = sp.getPerDay() != null ? sp.getPerDay().toString() : "";

                    if (sp.getDate() == null)
                        err_data.add("Error! No Date for Smoking Packs/Day in Care Element (" + (i + 1) + ")");
                    if (sp.getPerDay() == null)
                        err_data.add("Error! No value for Smoking Packs/Day in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("POSK", demographicNo, admProviderNo, dataField, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.SelfMonitoringBloodGlucose[] smbg = ce.getSelfMonitoringBloodGlucoseArray();
                for (cdsDt.SelfMonitoringBloodGlucose sg : smbg) {
                    Date dateObserved = sg.getDate() != null ? sg.getDate().getTime() : null;
                    String dataField = sg.getSelfMonitoring() != null ? sg.getSelfMonitoring().toString() : "";

                    if (sg.getDate() == null)
                        err_data.add("Error! No Date for Self-monitoring Blood Glucose in Care Element (" + (i + 1) + ")");
                    if (sg.getSelfMonitoring() == null)
                        err_data.add("Error! No value for Self-monitoring Blood Glucose in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("SMBG", demographicNo, admProviderNo, dataField, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.DiabetesEducationalSelfManagement[] desm = ce.getDiabetesEducationalSelfManagementArray();
                for (cdsDt.DiabetesEducationalSelfManagement dm : desm) {
                    Date dateObserved = dm.getDate() != null ? dm.getDate().getTime() : null;
                    String dataField = dm.getEducationalTrainingPerformed() != null ? dm.getEducationalTrainingPerformed().toString() : null;

                    if (dm.getDate() == null)
                        err_data.add("Error! No Date for Diabetes Educational/Self-management Training in Care Element (" + (i + 1) + ")");
                    if (dm.getEducationalTrainingPerformed() == null)
                        err_data.add("Error! No value for Diabetes Educational/Self-management Training in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("DMME", demographicNo, admProviderNo, dataField, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.DiabetesSelfManagementChallenges[] dsmc = ce.getDiabetesSelfManagementChallengesArray();
                for (cdsDt.DiabetesSelfManagementChallenges dc : dsmc) {
                    Date dateObserved = dc.getDate().getTime();
                    String dataField = dc.getChallengesIdentified().toString();
                    String dataCode = dc.getCodeValue() != null ? "LOINC=" + dc.getCodeValue().toString() : "";

                    if (dc.getDate() == null)
                        err_data.add("Error! No Date for Diabetes Self-management Challenges in Care Element (" + (i + 1) + ")");
                    if (dc.getChallengesIdentified() == null)
                        err_data.add("Error! No Challenges Identified for Diabetes Self-management Challenges in Care Element (" + (i + 1) + ")");
                    if (dc.getCodeValue() == null)
                        err_data.add("Error! No Code value for Diabetes Self-management Challenges in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("SMCD", demographicNo, admProviderNo, dataField, dataCode, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.DiabetesMotivationalCounselling[] dmc = ce.getDiabetesMotivationalCounsellingArray();
                for (cdsDt.DiabetesMotivationalCounselling dc : dmc) {
                    Date dateObserved = dc.getDate() != null ? dc.getDate().getTime() : null;
                    String dataField = "Yes";
                    if (dc.getCounsellingPerformed() != null) {
                        if (dc.getCounsellingPerformed().equals(CounsellingPerformed.NUTRITION)) {
                            ImportExportMeasurements.saveMeasurements("MCCN", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        } else if (dc.getCounsellingPerformed().equals(CounsellingPerformed.EXERCISE)) {
                            ImportExportMeasurements.saveMeasurements("MCCE", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        } else if (dc.getCounsellingPerformed().equals(CounsellingPerformed.SMOKING_CESSATION)) {
                            ImportExportMeasurements.saveMeasurements("MCCS", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        } else if (dc.getCounsellingPerformed().equals(CounsellingPerformed.OTHER)) {
                            ImportExportMeasurements.saveMeasurements("MCCO", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        }
                    }
                    if (dc.getDate() == null)
                        err_data.add("Error! No Date for Diabetes Motivational Counselling in Care Element (" + (i + 1) + ")");
                    if (dc.getCounsellingPerformed() == null)
                        err_data.add("Error! No Diabetes Motivational Counselling Performed value for Diabetes Self-management Challenges in Care Element (" + (i + 1) + ")");
                }
                cdsDt.DiabetesComplicationScreening[] dcs = ce.getDiabetesComplicationsScreeningArray();
                for (cdsDt.DiabetesComplicationScreening ds : dcs) {
                    Date dateObserved = ds.getDate() != null ? ds.getDate().getTime() : null;
                    String dataField = "Yes";
                    if (ds.getExamCode() != null) {
                        if (ds.getExamCode().equals(ExamCode.X_32468_1)) {
                            ImportExportMeasurements.saveMeasurements("EYEE", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        } else if (ds.getExamCode().equals(ExamCode.X_11397_7)) {
                            ImportExportMeasurements.saveMeasurements("FTE", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        } else if (ds.getExamCode().equals(ExamCode.X_67536_3)) {
                            ImportExportMeasurements.saveMeasurements("FTLS", demographicNo, admProviderNo, dataField, dateObserved);
                            addOneEntry(CAREELEMENTS);
                        }
                    }
                    if (ds.getDate() == null)
                        err_data.add("Error! No Date for Diabetes Complications Screening in Care Element (" + (i + 1) + ")");
                    if (ds.getExamCode() == null)
                        err_data.add("Error! No Exam Code for Diabetes Complications Screening in Care Element (" + (i + 1) + ")");
                }
                cdsDt.DiabetesSelfManagementCollaborative[] dsco = ce.getDiabetesSelfManagementCollaborativeArray();
                for (cdsDt.DiabetesSelfManagementCollaborative dc : dsco) {
                    Date dateObserved = dc.getDate() != null ? dc.getDate().getTime() : null;
                    String dataField = StringUtils.noNull(dc.getDocumentedGoals());
                    if (dc.getDate() == null)
                        err_data.add("Error! No Date for Diabetes Self-management/Collaborative Goal Setting in Care Element (" + (i + 1) + ")");
                    if (StringUtils.empty(dc.getDocumentedGoals()))
                        err_data.add("Error! No Documented Goal for Diabetes Self-management/Collaborative Goal Setting in Care Element (" + (i + 1) + ")");
                    if (dc.getCodeValue() == null)
                        err_data.add("Error! No Code Value for Diabetes Self-management/Collaborative Goal Setting in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("CGSD", demographicNo, admProviderNo, dataField, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
                cdsDt.HypoglycemicEpisodes[] hype = ce.getHypoglycemicEpisodesArray();
                for (cdsDt.HypoglycemicEpisodes he : hype) {
                    Date dateObserved = he.getDate() != null ? he.getDate().getTime() : null;
                    String dataField = he.getNumOfReportedEpisodes() != null ? he.getNumOfReportedEpisodes().toString() : "";

                    if (he.getDate() == null)
                        err_data.add("Error! No Date for Hypoglycemic Episodes in Care Element (" + (i + 1) + ")");
                    if (he.getNumOfReportedEpisodes() == null)
                        err_data.add("Error! No Reported Episodes value for Hypoglycemic Episodes in Care Element (" + (i + 1) + ")");
                    ImportExportMeasurements.saveMeasurements("HYPE", demographicNo, admProviderNo, dataField, dateObserved);
                    addOneEntry(CAREELEMENTS);
                }
            }

            //NEW CATEGORY - data that doesn't fit into other categories
            NewCategory[] newCategories = patientRec.getNewCategoryArray();
            String extraCategoryData = "";
            for (int i = 0; i < newCategories.length; i++) {
                NewCategory ce = newCategories[i];

                Util.addLine("Uncategorized Data: ", ce.getCategoryName() + " : " + ce.getCategoryDescription());
                for (int x = 0; x < ce.getResidualInfoArray().length; x++) {
                    Util.addLine(extraCategoryData, getResidual(ce.getResidualInfoArray(x)));
                }
            }
            if (StringUtils.filled(extraCategoryData)) {
                extra = Util.addLine("imported.cms4.2011.06", extra);
                CaseManagementNote dmNote = prepareCMNote("2", null);
                dmNote.setNote(extra);
                saveLinkNote(dmNote, CaseManagementNoteLink.DEMOGRAPHIC, Long.valueOf(demographicNo));
            }

            //CLINICAL NOTES
            ClinicalNotes[] cNotes = patientRec.getClinicalNotesArray();
            Date observeDate = new Date(), createDate = new Date();
            for (int i = 0; i < cNotes.length; i++) {
                //encounter note
                String encounter = cNotes[i].getMyClinicalNotesContent();
                if (StringUtils.empty(encounter)) {
                    err_data.add("Empty clinical note (" + (i + 1) + ")");
                    //continue;
                    encounter = org.apache.commons.lang3.StringUtils.trimToEmpty(encounter);
                }


                //create date
                    /*
                    if (cNotes[i].getEnteredDateTime()!=null) {
                    	createDate = dateTimeFPtoDate(cNotes[i].getEnteredDateTime(),timeShiftInDays);
                    	observeDate = createDate;
                    }
                    */

                //observation date
                if (cNotes[i].getEventDateTime() != null) {
                    observeDate = dateTimeFPtoDate(cNotes[i].getEventDateTime(), timeShiftInDays);
                    //if (cNotes[i].getEnteredDateTime()==null) createDate = observeDate;

                }
                //NOTE: sets the createdate and observationdate to current datetime if they are not set in XML.
                CaseManagementNote cmNote = prepareCMNote("1", null);
                cmNote.setCreate_date(createDate);
                cmNote.setObservation_date(observeDate);
                cmNote.setNote(encounter);

                String uuid = null;
                ClinicalNotes.ParticipatingProviders[] participatingProviders = cNotes[i].getParticipatingProvidersArray();
                ClinicalNotes.NoteReviewer[] noteReviewers = cNotes[i].getNoteReviewerArray();

                int p_total = participatingProviders.length + noteReviewers.length;
                for (int p = 0; p < p_total; p++) {
                    if (p > 0) {
                        cmNote = prepareCMNote("1", uuid);
                        cmNote.setObservation_date(observeDate);
                        cmNote.setCreate_date(createDate);
                        cmNote.setNote(encounter);
                    }

                    //participating providers
                    if (p < participatingProviders.length) {
                        // Participating providers are editors, not signers
                        cmNote.setSigned(false);

                        if (participatingProviders[p].getDateTimeNoteCreated() == null)
                            cmNote.setUpdate_date(new Date());
                        else
                            cmNote.setUpdate_date(dateTimeFPtoDate(participatingProviders[p].getDateTimeNoteCreated(), timeShiftInDays));

                        if (participatingProviders[p].getName() != null) {
                            HashMap<String, String> authorName = getPersonName(participatingProviders[p].getName());
                            String authorOHIP = participatingProviders[p].getOHIPPhysicianId();
                            String authorProvider = writeProviderData(authorName.get("firstname"), authorName.get("lastname"), authorOHIP);
                            if (StringUtils.empty(authorProvider)) {
                                authorProvider = defaultProviderNo();
                                err_note.add("Clinical notes have no author; assigned to \"doctor oscardoc\" (" + (i + 1) + ")");
                            }
                            cmNote.setProviderNo(authorProvider);
                            cmNote.setSigning_provider_no(authorProvider);
                        }
                    } else {

                        //note reviewers
                        int r = p - participatingProviders.length;
                        if (noteReviewers[r].getName() != null) {
                            if (noteReviewers[r].getDateTimeNoteReviewed() == null) cmNote.setUpdate_date(new Date());
                            else
                                cmNote.setUpdate_date(dateTimeFPtoDate(noteReviewers[r].getDateTimeNoteReviewed(), timeShiftInDays));

                            HashMap<String, String> authorName = getPersonName(noteReviewers[r].getName());
                            String reviewerOHIP = noteReviewers[r].getOHIPPhysicianId();
                            String reviewer = writeProviderData(authorName.get("firstname"), authorName.get("lastname"), reviewerOHIP);

                            cmNote.setProviderNo(reviewer);
                            cmNote.setSigning_provider_no(reviewer);
                            Util.writeVerified(cmNote);
                        }
                    }

                    if (cmNote.getProviderNo() == null) {
                        cmNote.setProviderNo(defaultProviderNo());
                    }

                    if (cmNote.getSigning_provider_no() == null) {
                        cmNote.setSigning_provider_no(defaultProviderNo());
                    }

                    caseManagementManager.saveNoteSimple(cmNote);

                    //prepare for extra notes
                    if (p == 0) {
                        addOneEntry(CLINICALNOTE);
                        uuid = cmNote.getUuid();

                        //create "header", cms4 only
                        CaseManagementNote headNote = prepareCMNote("2", null);
                        headNote.setCreate_date(createDate);
                        headNote.setUpdate_date(createDate);
                        headNote.setObservation_date(observeDate);
                        headNote.setNote("imported.cms4.2011.06" + uuid);
                        caseManagementManager.saveNoteSimple(headNote);
                    }
                }
                if (p_total == 0) {
                    err_note.add("Clinical notes have no author; assigned to \"doctor oscardoc\" (" + (i + 1) + ")");
                    caseManagementManager.saveNoteSimple(cmNote);
                }

                //to dumpsite
                String noteType = cNotes[i].getNoteType();
                if (StringUtils.filled(noteType)) {
                    noteType = Util.addLine("imported.cms4.2011.06", "Note Type: ", noteType);
                }

                CaseManagementNote dumpNote = prepareCMNote("2", null);
                dumpNote.setNote(noteType);
                saveLinkNote(cmNote.getId(), dumpNote);
            }

        }
        if (demoRes != null) {
            err_demo.addAll(demoRes.getWarningsCollection());
        }
        if (cleanFile) {
            Util.cleanFile(xmlFile);
        }

        err_summ.addAll(importErrors);

        return packMsgs(err_demo, err_data, err_summ, err_othe, err_note, warnings);
    }


    protected String mapContentType(String contentType) {

        if (".jpeg".equals(contentType) || ".jpg".equals(contentType)) {
            return "image/jpeg";
        }
        if (".doc".equals(contentType)) {
            return "application/msword";
        }
        if (".pdf".equals(contentType)) {
            return "application/pdf";
        }
        if (".txt".equals(contentType)) {
            return "text/plain";
        }
        if (".html".equals(contentType)) {
            return "text/html";
        }
        if (".rtf".equals(contentType)) {
            return "application/rtf";
        }
        return contentType;
    }

    /**
     * Resolves the source file for a report by trying multiple strategies.
     *
     * @param currentDirectory String the current directory to search from
     * @param filePath String the file path from the report data
     * @param contentType String the content type/extension for the file
     * @return File the resolved File if found, null otherwise
     */
    private File resolveReportSourceFile(String currentDirectory, String filePath, String contentType) {
        // Defensive null/blank guard for currentDirectory
        if (currentDirectory == null || currentDirectory.trim().isEmpty()) {
            logger.warn("resolveReportSourceFile: currentDirectory is null or empty, cannot resolve file path");
            return null;
        }

        // Defensive null/blank guard for filePath
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("resolveReportSourceFile: filePath is null or empty, cannot resolve file");
            return null;
        }

        String normalizedPath = filePath
            .replace("\\", File.separator)
            .replace("/", File.separator);

        if (isAbsoluteReportPath(normalizedPath)) {
            logger.error("SECURITY: Rejecting absolute file path from XML");
            return null;
        }

        String fileName = new File(normalizedPath).getName();
        File currentDir = new File(currentDirectory);

        List<File> candidates = new ArrayList<>();

        // Strategy 1: path relative to current directory
        candidates.add(new File(currentDir, normalizedPath));

        // Strategy 2: filename only in current directory
        candidates.add(new File(currentDir, fileName));

        // Try to resolve and validate each candidate
        for (File candidate : candidates) {
            File resolved = tryResolveCandidate(candidate, contentType, currentDir, filePath);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    /**
     * Attempts to resolve and validate a file candidate, optionally with a content type extension.
     *
     * @param candidate the base candidate file to try
     * @param contentType optional extension to append to the candidate path
     * @param allowedRoot the allowed root directory for path validation
     * @param originalPath the original file path from XML for logging
     * @return the validated File if found and valid, null otherwise
     */
    private File tryResolveCandidate(File candidate, String contentType, File allowedRoot, String originalPath) {
        // Try base candidate first
        File resolved = tryValidateExisting(candidate, allowedRoot, originalPath);
        if (resolved != null) {
            return resolved;
        }

        // Try candidate with extension
        if (contentType != null && !contentType.isEmpty()) {
            File withExt = new File(candidate.getPath() + contentType);
            return tryValidateExisting(withExt, allowedRoot, originalPath);
        }

        return null;
    }

    /**
     * Validates an existing regular file against the allowed root directory.
     * Only returns files that exist AND are regular files (not directories or special files).
     * This ensures the returned File can be used with FileUtils.copyFile without errors.
     *
     * @param file the file to validate
     * @param allowedRoot the allowed root directory
     * @param originalPath the original file path from XML for logging
     * @return the file if it exists as a regular file and passes validation, null otherwise
     */
    private File tryValidateExisting(File file, File allowedRoot, String originalPath) {
        if (!file.exists()) {
            return null;
        }

        // Only accept regular files, reject directories and other file types
        // This prevents FileUtils.copyFile from failing when given a directory
        if (!file.isFile()) {
            return null;
        }

        // CRITICAL SECURITY: Validate the resolved path is within the allowed directory
        // This prevents path traversal attacks from malicious XML (e.g., "../../../etc/passwd")
        try {
            PathValidationUtils.validateExistingPath(file, allowedRoot);
            return file;
        } catch (SecurityException e) {
            logger.error("SECURITY: Rejecting malicious file path from XML. originalPath='{}', resolvedPath='{}'",
                    Encode.forJava(originalPath), Encode.forJava(file.getPath()), e);
            return null;
        }
    }

    /**
     * Checks if a normalized path is absolute.
     *
     * @param normalizedPath the normalized path to check
     * @return true if the path is absolute, false otherwise
     */
    private boolean isAbsoluteReportPath(String normalizedPath) {
        if (normalizedPath == null) {
            return false;
        }

        File f = new File(normalizedPath);
        // Covers Unix and most Windows absolute cases
        if (f.isAbsolute()) {
            return true;
        }

        // Explicit drive-letter pattern for extra defensive checking
        return normalizedPath.matches("^[A-Za-z]:[/\\\\].*");
    }

    private File makeImportLog(ArrayList<String[]> demo, String dir) throws IOException {
        String[][] keyword = new String[2][16];
        keyword[0][0] = PATIENTID;
        keyword[1][0] = "ID";
        keyword[0][1] = " " + PERSONALHISTORY;
        keyword[1][1] = " History";
        keyword[0][2] = " " + FAMILYHISTORY;
        keyword[1][2] = " History";
        keyword[0][3] = " " + PASTHEALTH;
        keyword[1][3] = " Health";
        keyword[0][4] = " " + PROBLEMLIST;
        keyword[1][4] = " List";
        keyword[0][5] = " " + RISKFACTOR;
        keyword[1][5] = " Factor";
        keyword[0][6] = " " + ALLERGY;
        keyword[0][7] = " " + MEDICATION;
        keyword[0][8] = " " + IMMUNIZATION;
        keyword[0][9] = " " + LABS;
        keyword[0][10] = " " + APPOINTMENT;
        keyword[0][11] = " " + CLINICALNOTE;
        keyword[1][11] = " Note";
        keyword[0][12] = "    Report    ";
        keyword[1][12] = " " + REPORTTEXT;
        keyword[1][13] = " " + REPORTBINARY;
        keyword[0][14] = " " + CAREELEMENTS;
        keyword[1][14] = " Elements";
        keyword[0][15] = " " + ALERT;

        for (int i = 0; i < keyword[0].length; i++) {
            if (keyword[0][i].contains("Report")) {
                keyword[0][i + 1] = "Report2";
                i++;
                continue;
            }
            if (keyword[1][i] == null) keyword[1][i] = " ";
            if (keyword[0][i].length() > keyword[1][i].length())
                keyword[1][i] = fillUp(keyword[1][i], ' ', keyword[0][i].length());
            if (keyword[0][i].length() < keyword[1][i].length())
                keyword[0][i] = fillUp(keyword[0][i], ' ', keyword[1][i].length());
        }

        File importLog = new File(dir, "ImportEvent-" + UtilDateUtilities.getToday("yyyy-MM-dd.HH.mm.ss") + ".log");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(importLog))) {
            int tableWidth = 0;
            for (int i = 0; i < keyword.length; i++) {
                for (int j = 0; j < keyword[i].length; j++) {
                    out.write(keyword[i][j] + " |");
                    if (keyword[i][j].trim().equals("Report")) j++;
                    if (i == 1) tableWidth += keyword[i][j].length() + 2;
                }
                out.newLine();
            }
            out.write(fillUp("", '-', tableWidth));
            out.newLine();

            //general log data
            if (importNo == 0) importNo = 1;
            for (int i = 0; i < importNo; i++) {
                for (int j = 0; j < keyword[0].length; j++) {
                    String category = keyword[0][j].trim();
                    if (category.contains("Report")) category = keyword[1][j].trim();
                    Integer occurs = entries.get(category + i);
                    if (occurs == null) occurs = 0;
                    out.write(fillUp(occurs.toString(), ' ', keyword[1][j].length()));
                    out.write(" |");
                }
                out.newLine();
                out.write(fillUp("", '-', tableWidth));
                out.newLine();
            }
            out.newLine();
            out.newLine();
            out.newLine();

            //error log
            String column1 = "Patient ID";
            out.write(column1 + " |");
            out.write("Errors/Notes");
            out.newLine();
            out.write(fillUp("", '-', tableWidth));
            out.newLine();

            for (int i = 0; i < importNo; i++) {
                Integer id = entries.get(PATIENTID + i);
                if (id == null) id = 0;
                out.write(fillUp(id.toString(), ' ', column1.length()));
                out.write(" |");

                try {
                    String[] info = demo.get(i);

                    if (info != null && info.length > 0) {
                        String[] text = info[info.length - 1].split("\n");
                        out.write(text[0]);
                        out.newLine();
                        for (int j = 1; j < text.length; j++) {
                            out.write(fillUp("", ' ', column1.length()));
                            out.write(" |");
                            out.write(text[j]);
                            out.newLine();
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    MiscUtils.getLogger().error("Error", e);
                }

                out.write(fillUp("", '-', tableWidth));
                out.newLine();
            }
            out.close();
            importNo = 0;
            entries.clear();
        }
        return importLog;
    }


    boolean matchFileExt(String filename, String ext) {
        if (StringUtils.empty(filename) || StringUtils.empty(ext)) return false;
        if (filename.length() < ext.length() + 2) return false;
        if (filename.charAt(filename.length() - ext.length() - 1) != '.') return false;

        if (filename.toLowerCase().substring(filename.length() - ext.length()).equals(ext.toLowerCase())) return true;
        else return false;
    }

    String fillUp(String filled, char c, int size) {
        if (size >= filled.length()) {
            int fill = size - filled.length();
            for (int i = 0; i < fill; i++) filled += c;
        }
        return filled;
    }

    String getCalDate(Calendar c) {
        if (c == null) return "";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        return f.format(c.getTime());
    }

    String getCalDate(Calendar c, int timeShiftInDays) {
        if (c == null) return "";

        c.add(Calendar.DAY_OF_YEAR, timeShiftInDays);
        return getCalDate(c);
    }

    String getCalDateTime(Calendar c) {
        if (c == null) return "";

        Calendar c1 = Calendar.getInstance();
        c1.setTime(new Date());

        //Cancel out timezone difference
        int diff = c.getTimeZone().getRawOffset() - c1.getTimeZone().getRawOffset();
        c.add(Calendar.MILLISECOND, diff);

        //Cancel out daylight saving
        diff = c.getTimeZone().useDaylightTime() && c.getTimeZone().inDaylightTime(c.getTime()) ? 1 : 0;
        diff -= c1.getTimeZone().useDaylightTime() && c1.getTimeZone().inDaylightTime(c.getTime()) ? 1 : 0;
        c.add(Calendar.HOUR, diff);

        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return f.format(c.getTime());
    }

    String getCalTime(Calendar c) {
        if (c == null) return "";
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss");
        return f.format(c.getTime());
    }


    Map<String, List<JSONObject>> map = null;

    boolean verifyISO3661(String value) {
        if (map == null) {
            JSONObject obj = null;
            try {
                InputStream in = getClass().getClassLoader()
                        .getResourceAsStream("iso-3166-2.json");
                String theString = IOUtils.toString(in, "UTF-8");
                obj = new org.codehaus.jettison.json.JSONObject(theString);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                return false;
            }

            map = new HashMap<String, List<JSONObject>>();
            try {
                if (obj != null) {
                    Iterator iter = obj.keys();
                    while (iter.hasNext()) {
                        String countryCode = (String) iter.next();
                        //String countryName = ((org.codehaus.jettison.json.JSONObject)obj.get(countryCode)).getString("name");
                        //org.codehaus.jettison.json.JSONObject divisions = ((org.codehaus.jettison.json.JSONObject)obj.get(countryCode)).get("divisions");
                        JSONObject divisions = obj.getJSONObject(countryCode).getJSONObject("divisions");
                        Iterator iter2 = divisions.keys();
                        List<JSONObject> rList = new ArrayList<JSONObject>();
                        while (iter2.hasNext()) {
                            String divisionCode = (String) iter2.next();
                            String divisionName = divisions.getString(divisionCode);
                            org.codehaus.jettison.json.JSONObject r = new org.codehaus.jettison.json.JSONObject();
                            r.put("value", divisionCode);
                            r.put("label", divisionName);
                            rList.add(r);
                        }
                        map.put(countryCode, rList);
                    }
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
                return false;
            }
        }

        if (map == null) {
            return false;
        }

        String[] csdc = value.split("-");

        if (csdc.length == 2) {
            String country = csdc[0];
            //	String province = csdc[1];

            List<JSONObject> divisions =
                    map.get(country);

            if (divisions == null) {
                return false;
            }

            for (JSONObject rI : divisions) {
                try {
                    if (rI.has("value") && value.equals(rI.getString("value"))) {
                        return true;
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }
        } else {
            return false;
        }

        return false;

    }

    String getCountrySubDivCode(String countrySubDivCode) {
        if (StringUtils.empty(countrySubDivCode)) return "";


        //use full ISO 3661-2
        if (!verifyISO3661(countrySubDivCode)) {
            return "OT";
        }

        String[] csdc = countrySubDivCode.split("-");
        if (csdc.length == 2) {
            if (csdc[0].trim().equals("CA")) return csdc[1].trim(); //return w/o "CA-"
            return countrySubDivCode;
        }
        return "OT"; //Other
    }

    private String dateFPtoString(cdsDt.DateTimeFullOrPartial dtfp, int timeshiftInDays) {
        try {
            if (dtfp == null) {
                return "";
            }
            if (dtfp.getFullDateTime() != null) {
                dtfp.getFullDateTime().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDateTime(dtfp.getFullDateTime());
            }
            if (dtfp.getFullDate() != null) {
                dtfp.getFullDate().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDate(dtfp.getFullDate());
            } else if (dtfp.getYearMonth() != null) {
                dtfp.getYearMonth().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDate(dtfp.getYearMonth());
            } else if (dtfp.getYearOnly() != null) {
                dtfp.getYearOnly().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDate(dtfp.getYearOnly());
            } else {
                return "";
            }
        } catch (Exception e) {
            // cannot depend on export source sending well formatted dates.
            logger.warn("Invalid date. Returning empty value " + dtfp);
            return "";
        }
    }

    private String dateFPtoString(cdsDt.DateFullOrPartial dfp, int timeshiftInDays) {
        try {
            if (dfp == null) return "";

            if (dfp.getFullDate() != null) {
                dfp.getFullDate().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDate(dfp.getFullDate());
            } else if (dfp.getYearMonth() != null) {
                dfp.getYearMonth().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDate(dfp.getYearMonth());
            } else if (dfp.getYearOnly() != null) {
                dfp.getYearOnly().add(Calendar.DAY_OF_YEAR, timeshiftInDays);
                return getCalDate(dfp.getYearOnly());
            } else {
                return "";
            }
        } catch (Exception e) {
            // cannot depend on export source sending well formatted dates.
            logger.warn("Invalid date. Returning empty value " + dfp);
            return "";
        }
    }

    String dateFPGetPartial(cdsDt.DateFullOrPartial dfp) {
        try {
            if (dfp == null) return "";

            if (dfp.getYearMonth() != null) return PartialDate.YEARMONTH;
            else if (dfp.getYearOnly() != null) return PartialDate.YEARONLY;
            else return "";
        } catch (Exception e) {
            // cannot depend on export source sending well formatted dates.
            logger.warn("Invalid date. Returning empty value " + dfp);
            return "";
        }
    }

    String dateFPGetPartial(cdsDt.DateTimeFullOrPartial dfp) {
        try {
            if (dfp == null) return "";

            if (dfp.getYearMonth() != null) return PartialDate.YEARMONTH;
            else if (dfp.getYearOnly() != null) return PartialDate.YEARONLY;
            else return "";
        } catch (Exception e) {
            // cannot depend on export source sending well formatted dates.
            logger.warn("Invalid date. Returning empty value " + dfp);
            return "";
        }
    }

    Date dateTimeFPtoDate(cdsDt.DateTimeFullOrPartial dtfp, int timeShiftInDays) {
        String sdate = dateFPtoString(dtfp, timeShiftInDays);
        Date dDate = UtilDateUtilities.StringToDate(sdate, "yyyy-MM-dd HH:mm:ss");
        if (dDate == null)
            dDate = UtilDateUtilities.StringToDate(sdate, "yyyy-MM-dd");
        if (dDate == null)
            dDate = UtilDateUtilities.StringToDate(sdate, "HH:mm:ss");

        return dDate;
    }

    Date dateFPtoDate(cdsDt.DateFullOrPartial dfp, int timeShiftInDays) {
        String sdate = dateFPtoString(dfp, timeShiftInDays);
        return UtilDateUtilities.StringToDate(sdate, "yyyy-MM-dd");
    }

    String dateOnly(String d) {
        return UtilDateUtilities.DateToString(UtilDateUtilities.StringToDate(d), "yyyy-MM-dd");
    }

    HashMap<String, String> getPersonName(cdsDt.PersonNameSimple person) {
        HashMap<String, String> name = new HashMap<String, String>();
        if (person != null) {
            name.put("firstname", StringUtils.noNull(person.getFirstName()).trim());
            name.put("lastname", StringUtils.noNull(person.getLastName()).trim());
        }
        return name;
    }

    HashMap<String, String> getPersonName(cdsDt.PersonNameSimpleWithMiddleName person) {
        HashMap<String, String> name = new HashMap<String, String>();
        if (person != null) {
            name.put("firstname", StringUtils.noNull(person.getFirstName()).trim() + " " + StringUtils.noNull(person.getMiddleName()).trim());
            name.put("lastname", StringUtils.noNull(person.getLastName()).trim());
        }
        return name;
    }

    String defaultProviderNo() {
        ProviderData pd = getProviderByNames("doctor", "oscardoc", true);
        if (pd != null) return pd.getProviderNo();

        return writeProviderData("doctor", "oscardoc", "");
    }

    boolean isICD9(cdsDt.StandardCoding diagCode) {
        if (diagCode == null) return false;

        String codingSystem = StringUtils.noNull(diagCode.getStandardCodingSystem()).toLowerCase();

        return codingSystem.equals("icd-9");
        //	return (codingSystem.contains("icd") && codingSystem.contains("9"));
    }

    boolean isICD9(cdsDt.Code diagCode) {
        if (diagCode == null) return false;

        String codingSystem = StringUtils.noNull(diagCode.getCodingSystem()).toLowerCase();
        return codingSystem.equals("icd-9");
        //return (codingSystem.contains("icd") && codingSystem.contains("9"));
    }


    boolean isICD10(cdsDt.StandardCoding diagCode) {
        if (diagCode == null) return false;

        String codingSystem = StringUtils.noNull(diagCode.getStandardCodingSystem()).toLowerCase();
        //return (codingSystem.contains("icd") && codingSystem.contains("10"));
        return codingSystem.equals("icd-10");
    }

    boolean isICD10(cdsDt.Code diagCode) {
        if (diagCode == null) return false;

        String codingSystem = StringUtils.noNull(diagCode.getCodingSystem()).toLowerCase();
        //return (codingSystem.contains("icd") && codingSystem.contains("10"));
        return codingSystem.equals("icd-10");
    }

    boolean isICD9CM(cdsDt.StandardCoding diagCode) {
        if (diagCode == null) return false;

        String codingSystem = StringUtils.noNull(diagCode.getStandardCodingSystem()).toLowerCase();
        //return (codingSystem.contains("icd") && codingSystem.contains("10"));
        return codingSystem.equals("icd-9-cm");
    }

    boolean isICD9CM(cdsDt.Code diagCode) {
        if (diagCode == null) return false;

        String codingSystem = StringUtils.noNull(diagCode.getCodingSystem()).toLowerCase();
        //return (codingSystem.contains("icd") && codingSystem.contains("10"));
        return codingSystem.equals("icd-9-cm");
    }


    Set<CaseManagementIssue> getCMIssue(String code) {
        CaseManagementIssue cmIssu = new CaseManagementIssue();
        cmIssu.setDemographic_no(Integer.valueOf(demographicNo));
        Issue isu = caseManagementManager.getIssueInfoByCode(StringUtils.noNull(code));
        cmIssu.setIssue_id(isu.getId());
        cmIssu.setType(isu.getType());
        caseManagementManager.saveCaseIssue(cmIssu);

        Set<CaseManagementIssue> sCmIssu = new HashSet<CaseManagementIssue>();
        sCmIssu.add(cmIssu);
        return sCmIssu;
    }

    Set<CaseManagementIssue> getCMIssue(String issueCode, cdsDt.StandardCoding diagCode, boolean resolved) {
        Set<CaseManagementIssue> sCmIssu = new HashSet<CaseManagementIssue>();
        Issue isu = caseManagementManager.getIssueInfoByCode(StringUtils.noNull(issueCode));
        if (isu != null) {
            CaseManagementIssue cmIssu = new CaseManagementIssue();
            cmIssu.setDemographic_no(Integer.valueOf(demographicNo));
            cmIssu.setIssue_id(isu.getId());
            cmIssu.setType(isu.getType());
            cmIssu.setResolved(resolved);
            caseManagementManager.saveCaseIssue(cmIssu);
            sCmIssu.add(cmIssu);
        }
        if (isICD9(diagCode) || isICD10(diagCode) || isICD9CM(diagCode)) {
            //TODO: find issue by the code AND the type!!!!!!!!!!!!!!!!
            String type = null;
            if (isICD9(diagCode)) {
                type = "icd9";
            }
            if (isICD9CM(diagCode)) {
                type = "icd9cm";
            }
            if (isICD10(diagCode)) {
                type = "icd10";
            }
            isu = caseManagementManager.getIssueInfoByTypeAndCode(type, noDot(diagCode.getStandardCode()));
            if (isu == null) {
                isu = new Issue();
                isu.setCode(noDot(diagCode.getStandardCode()));
                isu.setDescription(diagCode.getStandardCodeDescription());
                isu.setRole("doctor");

                isu.setType(type);
                isu.setUpdate_date(new Date());
                caseManagementManager.saveIssue(isu);
            }
            if (isu != null && isu.getId() != null) {
                CaseManagementIssue cmIssu = new CaseManagementIssue();
                cmIssu.setDemographic_no(Integer.valueOf(demographicNo));
                cmIssu.setIssue_id(isu.getId());
                cmIssu.setType(isu.getType());
                cmIssu.setResolved(resolved);
                caseManagementManager.saveCaseIssue(cmIssu);
                cmIssu.setIssue(isu);
                sCmIssu.add(cmIssu);
            }
        }

        return sCmIssu;
    }

    Set<CaseManagementIssue> getCMIssue(String cppName, cdsDt.Code diagCode) {
        Set<CaseManagementIssue> sCmIssu = new HashSet<CaseManagementIssue>();
        Issue isu = caseManagementManager.getIssueInfoByCode(StringUtils.noNull(cppName));
        if (isu != null) {
            CaseManagementIssue cmIssu = new CaseManagementIssue();
            cmIssu.setDemographic_no(Integer.valueOf(demographicNo));
            cmIssu.setIssue_id(isu.getId());
            cmIssu.setType(isu.getType());
            caseManagementManager.saveCaseIssue(cmIssu);
            sCmIssu.add(cmIssu);
        }
        if (isICD9(diagCode) || isICD10(diagCode) || isICD9CM(diagCode)) {
            isu = caseManagementManager.getIssueInfoByCode(noDot(diagCode.getValue()));
            if (isu == null) {
                isu = new Issue();
                isu.setCode(noDot(diagCode.getValue()));
                isu.setDescription(diagCode.getDescription());
                isu.setRole("doctor");
                String type = null;
                if (isICD9(diagCode)) {
                    type = "icd9";
                }
                if (isICD9CM(diagCode)) {
                    type = "icd9cm";
                }
                if (isICD10(diagCode)) {
                    type = "icd10";
                }
                isu.setType(type);
                isu.setUpdate_date(new Date());
                caseManagementManager.saveIssue(isu);
            }
            if (isu != null && isu.getId() != null) {
                CaseManagementIssue cmIssu = new CaseManagementIssue();
                cmIssu.setDemographic_no(Integer.valueOf(demographicNo));
                cmIssu.setIssue_id(isu.getId());
                cmIssu.setType(isu.getType());
                //cmIssu.setResolved(resolved);
                caseManagementManager.saveCaseIssue(cmIssu);
                cmIssu.setIssue(isu);
                sCmIssu.add(cmIssu);
            }
        }

        return sCmIssu;
    }

    String getCode(cdsDt.StandardCoding dCode, String dTitle) {
        if (dCode == null) return "";

        String ret = StringUtils.filled(dTitle) ? dTitle + " -" : "";
        ret = Util.addLine(ret, "Coding System: ", dCode.getStandardCodingSystem());
        ret = Util.addLine(ret, "Value: ", dCode.getStandardCode());
        ret = Util.addLine(ret, "Description: ", dCode.getStandardCodeDescription());

        return ret;
    }

    String getCode(cdsDt.Code dCode, String dTitle) {
        if (dCode == null) return "";

        String ret = StringUtils.filled(dTitle) ? dTitle + " -" : "";
        ret = Util.addLine(ret, "Coding System: ", dCode.getCodingSystem());
        ret = Util.addLine(ret, "Value: ", dCode.getValue());
        ret = Util.addLine(ret, "Description: ", dCode.getDescription());

        return ret;
    }

    void addMeasurementsExt(Long measurementId, String key, String val, List<MeasurementsExt> exts) {
        if (measurementId != null && StringUtils.filled(key) && StringUtils.filled(val)) {
            MeasurementsExt mx = new MeasurementsExt(measurementId.intValue());
            mx.setKeyVal(key);
            mx.setVal(StringUtils.noNull(val));
            exts.add(mx);
        }
    }

    ProviderData getProviderByNames(String firstName, String lastName, boolean matchAll) {
        ProviderData pd = new ProviderData();
        if (matchAll) {
            pd.getProviderWithNames(firstName, lastName);
        } else {
            pd.getExternalProviderWithNames(firstName, lastName);
        }
        if (StringUtils.filled(pd.getProviderNo())) {
            pd.getProvider(pd.getProviderNo());
            return pd;
        } else return null;
    }

    ProviderData getProviderByOhip(String OhipNo) {
        ProviderData pd = new ProviderData();
        pd.getProviderWithOHIP(OhipNo);
        if (StringUtils.filled(pd.getProviderNo())) {
            pd.getProvider(pd.getProviderNo());
            return pd;
        } else return null;
    }

    String getProvinceCode(cdsDt.HealthCardProvinceCode.Enum provinceCode) {
        String pcStr = provinceCode.toString();
        return getCountrySubDivCode(pcStr);
    }

    String getResidual(cdsDt.ResidualInformation resInfo) {
        String ret = "";
        if (resInfo == null) return ret;

        cdsDt.ResidualInformation.DataElement[] resData = resInfo.getDataElementArray();
        if (resData.length > 0) ret = "- Residual Information -";
        for (int i = 0; i < resData.length; i++) {
            if (StringUtils.filled(resData[i].getName())) {
                ret = Util.addLine(ret, "Data Name: ", resData[i].getName());
                ret = Util.addLine(ret, "Data Type: ", resData[i].getDataType());
                ret = Util.addLine(ret, "Content: ", resData[i].getContent());
            }
        }
        return ret;
    }

    ArrayList<String> getUniques(String[] arr) {
        ArrayList<String> uniques = new ArrayList<String>();
        for (int i = 0; i < arr.length; i++) {
            boolean match = false;
            for (String x : uniques) {
                if (arr[i].equals(x)) {
                    match = true;
                    break;
                }
            }
            if (!match) uniques.add(arr[i]);
        }
        return uniques;
    }

    String getYN(cdsDt.YnIndicator yn) {
        if (yn == null) return "";

        String ret = "No";
        if (yn.getYnIndicatorsimple() == cdsDt.YnIndicatorsimple.Y || yn.getYnIndicatorsimple() == cdsDt.YnIndicatorsimple.Y_2) {
            ret = "Yes";
        } else if (yn.getBoolean()) {
            ret = "Yes";
        }
        return ret;
    }

    Boolean getBoolean(cdsDt.YnIndicator yn) {
        if (yn == null) return null;

        boolean ret = false;
        if (yn.getYnIndicatorsimple() == cdsDt.YnIndicatorsimple.Y || yn.getYnIndicatorsimple() == cdsDt.YnIndicatorsimple.Y_2) {
            ret = true;
        } else if (yn.getBoolean()) {
            ret = true;
        }
        return ret;
    }


    Boolean getYN(cdsDt.YnIndicatorAndBlank yn) {
        if (yn == null) return null;

        boolean ret = false;
        if (yn.getYnIndicatorsimple() == cdsDt.YnIndicatorsimple.Y || yn.getYnIndicatorsimple() == cdsDt.YnIndicatorsimple.Y_2) {
            ret = true;
        } else if (yn.getBoolean()) {
            ret = true;
        }
        return ret;
    }

    /**
     * Extracts the string value from a ResultNormalAbnormalFlag complex type.
     * Per the XSD schema, this is defined as xs:choice so valid XML should only have one child element set.
     * If both are unexpectedly set, enum takes precedence for consistency with HL7CreateFile.java.
     *
     * @param flag the ResultNormalAbnormalFlag object to extract from
     * @return the flag value as a string (e.g., "H", "L", "N"), or null if the flag is null
     */
    String getResultNormalAbnormalFlag(cdsDt.ResultNormalAbnormalFlag flag) {
        if (flag == null) return null;

        if (flag.getResultNormalAbnormalFlagAsEnum() != null) {
            // Using toString() to match HL7CreateFile.java pattern; returns HL7 abnormal flag codes (e.g., "H", "L", "A")
            return flag.getResultNormalAbnormalFlagAsEnum().toString();
        }
        if (flag.getResultNormalAbnormalFlagAsPlainText() != null) {
            return flag.getResultNormalAbnormalFlagAsPlainText();
        }
        return null;
    }

    String mapPreventionTypeByCode(cdsDt.Code imCode) {
        if (imCode == null) return null;
        if (!imCode.getCodingSystem().equalsIgnoreCase("DIN")) return null;

        ArrayList<String> dinFlu = new ArrayList<String>();
        ArrayList<String> dinHebAB = new ArrayList<String>();
        ArrayList<String> dinCHOLERA = new ArrayList<String>();

        dinFlu.add("01914510");
        dinFlu.add("02015986");
        dinFlu.add("02269562");
        dinFlu.add("02223929");

        dinHebAB.add("02230578");
        dinHebAB.add("02237548");

        dinCHOLERA.add("00074969");
        dinCHOLERA.add("02247208");

        if (dinFlu.contains(StringUtils.noNull(imCode.getValue()))) return "Flu";
        if (dinHebAB.contains(StringUtils.noNull(imCode.getValue()))) return "HebAB";
        if (dinCHOLERA.contains(StringUtils.noNull(imCode.getValue()))) return "CHOLERA";
        return null;
    }

    String[] packMsgs(ArrayList<String> err_demo, ArrayList<String> err_data, ArrayList<String> err_summ, ArrayList<String> err_othe, ArrayList<String> err_note, ArrayList<String> warnings) {
        if (!(err_demo.isEmpty() && err_data.isEmpty() && err_summ.isEmpty() && err_othe.isEmpty() && err_note.isEmpty())) {
            String title = "Fail to import patient " + patientName;
            if (StringUtils.filled(demographicNo)) {
                title = "Patient " + patientName + " (Demographic no=" + demographicNo + ")";
            }
            warnings.add(fillUp("---- " + title, '-', 100));
        }
        warnings.addAll(err_demo);
        warnings.addAll(err_data);
        warnings.addAll(err_summ);
        warnings.addAll(err_othe);
        warnings.addAll(err_note);

        String err_all = aListToMsg(err_demo);
        err_all = Util.addLine(err_all, aListToMsg(err_data));
        err_all = Util.addLine(err_all, aListToMsg(err_summ));
        err_all = Util.addLine(err_all, aListToMsg(err_othe));
        err_all = Util.addLine(err_all, aListToMsg(err_note));

        String[] msgs = {demographicNo, err_data.isEmpty() ? "Yes" : "No",
                err_summ.isEmpty() ? "Yes" : "No",
                err_othe.isEmpty() ? "Yes" : "No", err_all};
        return msgs;
    }

    CaseManagementNote prepareCMNote(String caisi_role, String uuid) {
        CaseManagementNote cmNote = new CaseManagementNote();
        cmNote.setUpdate_date(new Date());
        cmNote.setObservation_date(new Date());
        cmNote.setDemographic_no(demographicNo);
        cmNote.setProviderNo(admProviderNo);
        cmNote.setSigning_provider_no(admProviderNo);
        cmNote.setSigned(true);
        cmNote.setHistory("");
        cmNote.setReporter_program_team("0");
        cmNote.setProgram_no(programId);
        if (StringUtils.filled(uuid)) cmNote.setUuid(uuid);
        else cmNote.setUuid(UUID.randomUUID().toString());

        if (caisi_role == null || (!caisi_role.equals("1") && !caisi_role.equals("2"))) caisi_role = "1";
        cmNote.setReporter_caisi_role(caisi_role);  //"1" for doctor, "2" for nurse - note hidden in echart

        return cmNote;
    }

    void saveLinkNote(Long hostId, CaseManagementNote cmn) {
        saveLinkNote(cmn, CaseManagementNoteLink.CASEMGMTNOTE, hostId);
    }

    void saveLinkNote(CaseManagementNote cmn, Integer tableName, Long tableId) {
        saveLinkNote(cmn, tableName, tableId, null);
    }

    void saveLinkNote(CaseManagementNote cmn, Integer tableName, Long tableId, String otherId) {
        if (StringUtils.filled(cmn.getNote())) {
            caseManagementManager.saveNoteSimple(cmn);    //new note id created

            CaseManagementNoteLink cml = new CaseManagementNoteLink();
            cml.setTableName(tableName);
            cml.setTableId(tableId);
            cml.setNoteId(cmn.getId()); //new note id
            cml.setOtherId(otherId);
            caseManagementManager.saveNoteLink(cml);
        }
    }

    void saveMeasurementsExt(Long measurementId, String key, String val) {
        if (measurementId != null && StringUtils.filled(key) && StringUtils.filled(val)) {
            MeasurementsExt mx = new MeasurementsExt(measurementId.intValue());
            mx.setKeyVal(key);
            mx.setVal(StringUtils.noNull(val));
            ImportExportMeasurements.saveMeasurementsExt(mx);
        }
    }

    String updateExternalProvider(String firstName, String lastName, String ohipNo, String cpsoNo, ProviderData pd) {
        // For external providers only
        if (pd == null) return null;
        if (pd.getProviderNo().charAt(0) != '-') return pd.getProviderNo();

        ca.openosp.openo.commn.model.ProviderData newpd = providerDataDao.findByProviderNo(pd.getProviderNo());
        if (StringUtils.empty(pd.getFirst_name()))
            newpd.setFirstName(StringUtils.noNull(firstName));
        if (StringUtils.empty(pd.getLast_name()))
            newpd.setLastName(StringUtils.noNull(lastName));
        if (StringUtils.empty(pd.getOhip_no()))
            newpd.setOhipNo(ohipNo);
        if (StringUtils.empty(pd.getPractitionerNo()))
            newpd.setPractitionerNo(cpsoNo);

        providerDataDao.merge(newpd);
        return newpd.getId();
    }

    String writeProviderData(String firstName, String lastName, String ohipNo) {
        return writeProviderData(firstName, lastName, ohipNo, null);
    }

    String writeProviderData(String firstName, String lastName, String ohipNo, String cpsoNo) {
        ProviderData pd = getProviderByOhip(ohipNo);

        if (pd == null) pd = getProviderByNames(firstName, lastName, matchProviderNames);

        if (pd != null) return updateExternalProvider(firstName, lastName, ohipNo, cpsoNo, pd);

        //Write as a new providers
        if (StringUtils.empty(firstName) && StringUtils.empty(lastName) && StringUtils.empty(ohipNo))
            return ""; //no information at all!
        pd = new ProviderData();
        MiscUtils.getLogger().debug("ADD EXTERNAL");
        pd.addExternalProvider(firstName, lastName, ohipNo, cpsoNo);
        return pd.getProviderNo();
    }

    String aListToMsg(ArrayList<String> alist) {
        String msgs = "";
        for (int i = 0; i < alist.size(); i++) {
            String msg = alist.get(i);
            if (i > 0) msgs += "\n";
            msgs += msg;
        }
        return msgs;
    }

    void insertIntoAdmission(String demoNo) {
        Admission admission = new Admission();
        admission.setClientId(Integer.valueOf(demoNo));
        admission.setProviderNo(admProviderNo);
        admission.setProgramId(Integer.valueOf(programId));
        admission.setAdmissionDate(new Date());
        admission.setAdmissionFromTransfer(false);
        admission.setDischargeFromTransfer(false);
        admission.setAdmissionStatus("current");
        admission.setTeamId(null);
        admission.setTemporaryAdmission(false);
        admission.setRadioDischargeReason("0");
        admission.setClientStatusId(null);
        admission.setAutomaticDischarge(false);

        admissionDao.saveAdmission(admission);
    }

    String getLabDline(LaboratoryResults labRes, int timeShiftInDays) {
        StringBuilder s = new StringBuilder();
        appendIfNotNull(s, "LaboratoryName", labRes.getLaboratoryName());
        appendIfNotNull(s, "TestNameReportedByLab", labRes.getTestNameReportedByLab());
        appendIfNotNull(s, "LabTestCode", labRes.getLabTestCode());
        appendIfNotNull(s, "TestName", labRes.getTestName());
        appendIfNotNull(s, "AccessionNumber", labRes.getAccessionNumber());

        if (labRes.getResult() != null) {
            appendIfNotNull(s, "Value", labRes.getResult().getValue());
            appendIfNotNull(s, "UnitOfMeasure", labRes.getResult().getUnitOfMeasure());
        }
        if (labRes.getReferenceRange() != null) {
            LaboratoryResults.ReferenceRange ref = labRes.getReferenceRange();
            appendIfNotNull(s, "LowLimit", ref.getLowLimit());
            appendIfNotNull(s, "HighLimit", ref.getHighLimit());
            appendIfNotNull(s, "ReferenceRangeText", ref.getReferenceRangeText());
        }

        appendIfNotNull(s, "LabRequisitionDateTime", dateFPtoString(labRes.getLabRequisitionDateTime(), timeShiftInDays));
        appendIfNotNull(s, "CollectionDateTime", dateFPtoString(labRes.getCollectionDateTime(), timeShiftInDays));

        LaboratoryResults.ResultReviewer[] resultReviewers = labRes.getResultReviewerArray();
        if (resultReviewers.length > 0) {
            appendIfNotNull(s, "DateTimeResultReviewed", dateFPtoString(resultReviewers[0].getDateTimeResultReviewed(), timeShiftInDays));
            appendIfNotNull(s, "OHIP ID :", resultReviewers[0].getOHIPPhysicianId());
            cdsDt.PersonNameSimple reviewerName = resultReviewers[0].getName();
            if (reviewerName != null) {
                appendIfNotNull(s, "Reviewer First Name:", reviewerName.getFirstName());
                appendIfNotNull(s, "Reviewer Last Name:", reviewerName.getLastName());
            }
        }

        appendIfNotNull(s, "ResultNormalAbnormalFlag", getResultNormalAbnormalFlag(labRes.getResultNormalAbnormalFlag()));
        appendIfNotNull(s, "TestResultsInformationreportedbytheLaboratory", labRes.getTestResultsInformationReportedByTheLab());
        appendIfNotNull(s, "NotesFromLab", labRes.getNotesFromLab());
        appendIfNotNull(s, "PhysiciansNotes", labRes.getPhysiciansNotes());

        return s.toString();
    }

    void appendIfNotNull(StringBuilder s, String name, String object) {
        if (object != null) {
            s.append(name);
            s.append(": ");
            s.append(object);
            s.append("\n");
        }
    }

    void addOneEntry(String category) {
        if (StringUtils.empty(category)) return;

        Integer n = entries.get(category + importNo);
        n = n == null ? 1 : n + 1;
        entries.put(category + importNo, n);
    }

    DemographicArchive archiveDemographic(Demographic d) {
        DemographicArchive da = new DemographicArchive();

        da.setDemographicNo(Integer.valueOf(d.getDemographicNo()));
        da.setFirstName(d.getFirstName());
        da.setLastName(d.getLastName());
        da.setTitle(d.getTitle());
        da.setSex(d.getSex());
        da.setYearOfBirth(d.getYearOfBirth());
        da.setMonthOfBirth(d.getMonthOfBirth());
        da.setDateOfBirth(d.getDateOfBirth());
        da.setAddress(d.getAddress());
        da.setCity(d.getCity());
        da.setProvince(d.getProvince());
        da.setPostal(d.getPostal());
        da.setAlias(d.getAlias());
        da.setEmail(d.getEmail());
        da.setAnonymous(d.getAnonymous());
        da.setChartNo(d.getChartNo());
        da.setChildren(d.getChildren());
        da.setCitizenship(d.getCitizenship());
        da.setCountryOfOrigin(d.getCountryOfOrigin());
        da.setDateJoined(d.getDateJoined());
        da.setEndDate(d.getEndDate());
        da.setFamilyDoctor(d.getFamilyDoctor());
        da.setHin(d.getHin());
        da.setVer(d.getVer());
        da.setHcType(d.getHcType());
        da.setEffDate(d.getEffDate());
        da.setHcRenewDate(d.getHcRenewDate());
        da.setNewsletter(d.getNewsletter());
        da.setOfficialLanguage(d.getOfficialLanguage());
        da.setSpokenLanguage(d.getSpokenLanguage());
        da.setPatientStatus(d.getPatientStatus());
        da.setPatientStatusDate(d.getPatientStatusDate());
        da.setPcnIndicator(d.getPcnIndicator());
        da.setPhone(d.getPhone());
        da.setPhone2(d.getPhone2());
        da.setPreviousAddress(d.getPreviousAddress());
        da.setProviderNo(d.getProviderNo());
        da.setRosterStatus(d.getRosterStatus());
        da.setRosterDate(d.getRosterDate());
        da.setRosterTerminationDate(d.getRosterTerminationDate());
        da.setRosterTerminationReason(d.getRosterTerminationReason());
        da.setSin(d.getSin());
        da.setSourceOfIncome(d.getSourceOfIncome());
        da.setLastUpdateDate(d.getLastUpdateDate());
        da.setLastUpdateUser(d.getLastUpdateUser());

        return da;
    }

    String mapNamePurpose(cdsDt.PersonNamePurposeCode.Enum namePurpose) {
        if (namePurpose.equals(cdsDt.PersonNamePurposeCode.HC))
            return "Health Card Name";
        if (namePurpose.equals(cdsDt.PersonNamePurposeCode.L))
            return "Legal Name";
        if (namePurpose.equals(cdsDt.PersonNamePurposeCode.AL))
            return "Alias Name";
        if (namePurpose.equals(cdsDt.PersonNamePurposeCode.C))
            return "License Name";
        return "";
    }

    String noDot(String s) {
        if (s == null) return null;
        s = s.trim();

        for (int i = 0; i < s.length(); i++) {
            if (".".contains(s.substring(i, i + 1))) {
                s = s.substring(0, i) + s.substring(i + 1, s.length());
                i--;
            }
        }
        return s;
    }

    String addSpaced(String s, String ss) {
        s = StringUtils.noNull(s).trim();

        if (!s.equals("") && StringUtils.filled(ss)) s += " " + ss.trim();
        else s += StringUtils.noNull(ss).trim();

        return s;
    }

    private static Integer saveRxAllergy(Integer demographicNo, Date entryDate, String description, Integer typeCode, String reaction, Date startDate, String severity, String regionalId, String lifeStage, boolean intolerance, boolean nonDrug) {

        AllergyDao allergyDao = (AllergyDao) SpringUtils.getBean(AllergyDao.class);

        Allergy allergy = new Allergy();
        allergy.setDemographicNo(demographicNo);
        allergy.setEntryDate(entryDate);
        allergy.setDescription(description);
        allergy.setTypeCode(typeCode);
        allergy.setReaction(reaction);
        allergy.setStartDate(startDate);
        allergy.setSeverityOfReaction(severity);
        allergy.setRegionalIdentifier(regionalId);
        allergy.setLifeStage(lifeStage);
        allergy.setNonDrug(nonDrug);
        allergyDao.persist(allergy);
        return (allergy.getId());
    }

    /**
     * Terrible method.
     * Not my fault, you should have used a Date object to begin with not a String. Now I have to undo your mess.
     */
    private static Date toDateFromString(String s) {
        if (s == null) {
            return null;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return (sdf.parse(s));
        } catch (Exception e) {
            // okay we couldn't parse it, we'll try another format
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");
            return (sdf.parse(s));
        } catch (Exception e) {
            // okay we couldn't parse it, we'll try another format
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DateFormatUtils.ISO_DATETIME_FORMAT.getPattern());
            return (sdf.parse(s));
        } catch (Exception e) {
            // okay we couldn't parse it, we'll try another format
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DateFormatUtils.ISO_DATE_FORMAT.getPattern());
            return (sdf.parse(s));
        } catch (Exception e) {
            // okay we couldn't parse it, we'll try another format
        }

        // no more formats to try, we lose :(
        logger.warn("UnParsable date string : " + s);

        return (null);

    }

    /*
     * MSH segment for a dummy GDML record
     */


    /*
     * Get a new array of only the results which have a matching accessing number
     */
    private LaboratoryResults[] filterByAccession(LaboratoryResults[] labResultArr, String accession) {
        List<LaboratoryResults> filteredList = new ArrayList<LaboratoryResults>();

        for (LaboratoryResults result : labResultArr) {
            if (accession.equals(result.getAccessionNumber())) {
                filteredList.add(result);
            }
        }
        return filteredList.toArray(new LaboratoryResults[filteredList.size()]);
    }


	/*
    String[] _accession = new String[labResultArr.length]; //accessionNumber
    String[] _coll_date = new String[labResultArr.length]; //collectionDateTime
    String[] _title	    = new String[labResultArr.length]; //same as _testName
    String[] _testName  = new String[labResultArr.length]; //testName + "/" + getTestNameReportedByLab
    String[] _abn	    = new String[labResultArr.length]; // A or N
    String[] _minimum   = new String[labResultArr.length]; //if(getReferenceRangeText)getReferenceRangeText , getReferenceRange.getLowLimit
    String[] _maximum   = new String[labResultArr.length]; //getReferenceRange.getHighLimit
    String[] _result    = new String[labResultArr.length]; //result.getValue
    String[] _unit	    = new String[labResultArr.length]; //result.getUnitOfMeasure
    String[] _labnotes  = new String[labResultArr.length]; //"Notes: "+getNotesFromLab
    String[] _location  = new String[labResultArr.length]; //laboratoryName
    String[] _reviewer  = new String[labResultArr.length]; //getResultReviewer->getPersonName(getName) + ohip + names
    String[] _lab_ppid  = new String[labResultArr.length];
    String[] _rev_date  = new String[labResultArr.length]; //getDateTimeResultReviewed
    String[] _req_date  = new String[labResultArr.length]; //getLabRequisitionDateTime (set to collectionDateTime if null)

*/

    private Long findMeasurementId(Integer labNo, String testName) {
        Integer measId = measurementsExtDao.getMeasurementIdByLabNoAndTestName(labNo.toString(), testName);
        if (measId != null) {
            return Long.valueOf(measId);
        } else {
            return null;
        }
    }

    private void importLabs(LoggedInInfo loggedInInfo, LaboratoryResults[] labResultArr) {
        List<String> accessionsDone = new ArrayList<String>();

        for (LaboratoryResults labResult : labResultArr) {
            if (StringUtils.filled(labResult.getAccessionNumber()) && accessionsDone.contains(labResult.getAccessionNumber())) {
                continue;
            }

            try {
                //find others with same accession number
                LaboratoryResults[] reportResults = null;
                if (StringUtils.filled(labResult.getAccessionNumber())) {
                    reportResults = filterByAccession(labResultArr, labResult.getAccessionNumber());
                    accessionsDone.add(labResult.getAccessionNumber());
                } else {
                    reportResults = new LaboratoryResults[]{labResult};
                }

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddkkmmssSS");
                String filename = "Lab." + sdf.format(new Date()) + ".import.hl7";
                HL7CreateFile hl7CreateFile = new HL7CreateFile(demographic);
                String observationMsg = hl7CreateFile.generateHL7(Arrays.asList(reportResults));

                InputStream formFileIs = null;
                InputStream localFileIs = null;

                Integer labNo = null;
                try {
                    String type = hl7CreateFile.LAB_TYPE;

                    InputStream stream = new ByteArrayInputStream(observationMsg.replace("\r", "\r\n").getBytes(StandardCharsets.UTF_8));
                    String filePath = Utilities.saveFile(stream, filename);
                    File file = new File(filePath);

                    localFileIs = new FileInputStream(filePath);

                    int checkFileUploadedSuccessfully = FileUploadCheck.addFile(file.getName(), localFileIs, admProviderNo);

                    if (checkFileUploadedSuccessfully != FileUploadCheck.UNSUCCESSFUL_SAVE) {
                        logger.debug("File uploaded successfully");
                        logger.debug("Type: {}", type);
                        MessageHandler msgHandler = HandlerClassFactory.getHandler(type);
                        if (msgHandler != null) {
                            logger.debug("MESSAGE HANDLER " + msgHandler.getClass().getName());
                        }

                        if (msgHandler instanceof CMLHandler && ((CMLHandler) msgHandler).parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, "") != null) {
                            labNo = ((CMLHandler) msgHandler).getLastLabNo();
                            logger.info("successfully added lab");
                            addOneEntry(LABS);
                        } else if (msgHandler instanceof GDMLHandler && ((GDMLHandler) msgHandler).parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, "") != null) {
                            labNo = ((GDMLHandler) msgHandler).getLastLabNo();
                            logger.info("successfully added lab");
                            addOneEntry(LABS);
                        } else if (msgHandler instanceof MDSHandler && ((MDSHandler) msgHandler).parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, "") != null) {
                            labNo = ((MDSHandler) msgHandler).getLastLabNo();
                            logger.info("successfully added lab");
                            addOneEntry(LABS);
                        } else if (msgHandler instanceof ExcellerisOntarioHandler && ((ExcellerisOntarioHandler) msgHandler).parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, "") != null) {
                            labNo = ((ExcellerisOntarioHandler) msgHandler).getLastLabNo();
                            logger.info("successfully added lab");
                            addOneEntry(LABS);
                        } else if (msgHandler instanceof PATHL7Handler && ((PATHL7Handler) msgHandler).parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, "") != null) {
                            labNo = ((PATHL7Handler) msgHandler).getLastLabNo();
                            logger.info("successfully added lab");
                            addOneEntry(LABS);
                        } else {
                            importErrors.add("Unregcognized lab facility: " + type);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error: ", e);
                    importErrors.add("Error adding lab");
                } finally {
                    IOUtils.closeQuietly(formFileIs);
                    IOUtils.closeQuietly(localFileIs);
                }


                if (labNo != null) {
                    Hl7textResultsData.populateMeasurementsTable(labNo.toString(), demographicNo);

                    patientLabRoutingDao.persist(new PatientLabRouting(labNo, "HL7", Integer.parseInt(demographicNo)));

                    DateTimeFullOrPartial dt = labResult.getLabRequisitionDateTime();
                    if (dt == null) {
                        dt = labResult.getCollectionDateTime();
                    }

                    LabRequestReportLink.save(null, null, dateFPtoString(dt, 0), "labPatientPhysicianInfo", labNo.longValue());

                    List<ProviderLabRoutingModel> providerLabRoutingQueue = new ArrayList<>();

                    if (StringUtils.filled(demographic.getProviderNo())) {
                        providerLabRoutingQueue.add(new ProviderLabRoutingModel(demographic.getProviderNo(), labNo, "N", "", new Date(), "HL7"));
                    }

                    for (ResultReviewer resultReviewer : labResult.getResultReviewerArray()) {
                        Date reviewDate = dateTimeFPtoDate(resultReviewer.getDateTimeResultReviewed(), 0);

                        String reviewer = writeProviderData(resultReviewer.getName().getFirstName(), resultReviewer.getName().getLastName(), resultReviewer.getOHIPPhysicianId(), null);

                        String reviewerComment = "";
                        if (StringUtils.filled(labResult.getPhysiciansNotes())) {
                            reviewerComment = labResult.getPhysiciansNotes();
                        }

                        String status = StringUtils.filled(reviewer) ? "A" : "N";
                        reviewer = status.equals("A") ? reviewer : "0";

                        providerLabRoutingQueue.add(new ProviderLabRoutingModel(reviewer, labNo, status, reviewerComment, reviewDate, "HL7"));
                    }

                    providerLabRoutingDao.batchPersist(providerLabRoutingQueue);

                    List<MeasurementsExt> measurementsExtsToSave = new ArrayList<>();
                    for (int x = 0; x < reportResults.length; x++) {
                        LaboratoryResults result = reportResults[x];
                        Long measId = findMeasurementId(labNo, result.getTestNameReportedByLab());
                        HashMap<String, MeasurementsExt> measurementsExtMap = new HashMap<String, MeasurementsExt>();

                        if (measId != null) {
                            measurementsExtMap = measurementsExtDao.getMeasurementsExtMapByMeasurementId(measId.intValue());

                            if (StringUtils.filled(result.getNotesFromLab()) && measurementsExtMap.get("comments") == null) {
                                addMeasurementsExt(measId, "comments", result.getNotesFromLab(), measurementsExtsToSave);
                            }

                            String annotation = labResult.getPhysiciansNotes();
                            if (StringUtils.filled(annotation)) {
                                if (measurementsExtMap.get("other_id") == null) {
                                    addMeasurementsExt(measId, "other_id", "0-0", measurementsExtsToSave);
                                }
                                CaseManagementNote cmNote = prepareCMNote("2", null);
                                cmNote.setNote(annotation);
                                saveLinkNote(cmNote, CaseManagementNoteLink.LABTEST, labNo.longValue(), "0-0");
                            }

                            String olis_status = result.getTestResultStatus();
                            if (StringUtils.filled(olis_status)) {
                                if (measId != null && measurementsExtMap.get("olis_status") == null) {
                                    addMeasurementsExt(measId, "olis_status", olis_status, measurementsExtsToSave);
                                }
                            }

                            if (result.getBlockedTestResult() != null && "Y".equals(result.getBlockedTestResult().toString()) && measurementsExtMap.get("reportBlocked") == null) {
                                addMeasurementsExt(measId, "reportBlocked", "Y", measurementsExtsToSave);
                            }

                            if (result.isSetTestName() && measurementsExtMap.get("name_internal") == null) {
                                addMeasurementsExt(measId, "name_internal", result.getTestName(), measurementsExtsToSave);
                            }

                            if (result.isSetReferenceRange()) {
                                if (StringUtils.filled(result.getReferenceRange().getReferenceRangeText()) && measurementsExtMap.get("range") == null) {
                                    addMeasurementsExt(measId, "range", result.getReferenceRange().getReferenceRangeText(), measurementsExtsToSave);
                                }

                                if (StringUtils.filled(result.getReferenceRange().getLowLimit()) && StringUtils.filled(result.getReferenceRange().getHighLimit())
                                        && measurementsExtMap.get("range") == null && measurementsExtMap.get("minimum") == null && measurementsExtMap.get("maximum") == null) {
                                    addMeasurementsExt(measId, "range", result.getReferenceRange().getLowLimit() + "-" + result.getReferenceRange().getHighLimit(), measurementsExtsToSave);
                                    addMeasurementsExt(measId, "minimum", result.getReferenceRange().getLowLimit(), measurementsExtsToSave);
                                    addMeasurementsExt(measId, "maximum", result.getReferenceRange().getHighLimit(), measurementsExtsToSave);
                                } else {
                                    if (StringUtils.filled(result.getReferenceRange().getLowLimit()) && measurementsExtMap.get("minimum") == null) {
                                        addMeasurementsExt(measId, "minimum", result.getReferenceRange().getLowLimit(), measurementsExtsToSave);
                                    }

                                    if (StringUtils.filled(result.getReferenceRange().getHighLimit()) && measurementsExtMap.get("maximum") == null) {
                                        addMeasurementsExt(measId, "maximum", result.getReferenceRange().getHighLimit(), measurementsExtsToSave);
                                    }

                                    if (StringUtils.filled(result.getReferenceRange().getLowLimit()) && StringUtils.filled(result.getReferenceRange().getHighLimit()) && measurementsExtMap.get("range") == null) {
                                        addMeasurementsExt(measId, "range", result.getReferenceRange().getLowLimit() + "-" + result.getReferenceRange().getHighLimit(), measurementsExtsToSave);
                                    }
                                }
                            }
                        }
                    }

                    measurementsExtDao.batchPersist(measurementsExtsToSave, 50);

                    String labInfo = getLabDline(labResult, 0);
                    if (StringUtils.filled(labInfo)) {
                        String dump = Util.addLine("imported.cms5.2017.06", labInfo);
                        CaseManagementNote cmNote = prepareCMNote("2", null);
                        cmNote.setNote(dump);
                        saveLinkNote(cmNote, CaseManagementNoteLink.LABTEST, labNo.longValue(), "0-0");
                    }
                }

            } catch (Exception e) {
                logger.error("error", e);
                importErrors.add("Error processing lab data");
            }
        }
    }

    //"Confirmed, Cancelled, No-Show, No-Cancellation Allowed and other descriptors possible"
    protected String isMappedStatus(AppointmentStatus as, String status) {

        if (status.equals(as.getDescription())) {
            return as.getStatus();
        }
/*
		if("Confirmed".equals(status) && "Here".equals(as.getDescription())) {
			return as.getStatus();
		}
*/
        if ("No-Show".equals(status) && "No Show".equals(as.getDescription())) {
            return as.getStatus();
        }

        return null;
    }

    protected AppointmentStatus createNewAppointmentStatus(String description) {
        AppointmentStatusDao appointmentStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);
        List<String> usedStatusCodes = new ArrayList<String>();
        for (AppointmentStatus as : appointmentStatusDao.findAll()) {
            if (description.equals(as.getDescription())) {
                return as;
            }
            usedStatusCodes.add(as.getStatus());
        }

        AppointmentStatus importedStatus = new AppointmentStatus();
        importedStatus.setActive(1);
        importedStatus.setDescription(description);
        importedStatus.setEditable(1);
        importedStatus.setIcon("5.gif");


        importedStatus.setStatus(findStatus(usedStatusCodes));
        importedStatus.setColor(generateRandomColor());

        appointmentStatusDao.persist(importedStatus);

        return importedStatus;
    }

    String findStatus(List<String> usedStatusCodes) {
        String alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (char c : alpha.toCharArray()) {
            if (!usedStatusCodes.contains(String.valueOf(c))) {
                return String.valueOf(c);
            }
        }
        return null;
    }

    String generateRandomColor() {
        int R = (int) (Math.random() * 256);
        int G = (int) (Math.random() * 256);
        int B = (int) (Math.random() * 256);
        Color color = new Color(R, G, B);
        return getHTMLColorString(color);
    }

    public String getHTMLColorString(Color color) {
        String red = Integer.toHexString(color.getRed());
        String green = Integer.toHexString(color.getGreen());
        String blue = Integer.toHexString(color.getBlue());

        return "#" +
                (red.length() == 1 ? "0" + red : red) +
                (green.length() == 1 ? "0" + green : green) +
                (blue.length() == 1 ? "0" + blue : blue);
    }

//	protected String getImportStatus() {
//		//create if necessary
//		 AppointmentStatusDao appointmentStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);
//		 AppointmentStatus importedStatus = null;
//		 for(AppointmentStatus as : appointmentStatusDao.findAll()) {
//			 if(as.getDescription().equals("Imported")) {
//				 importedStatus = as;
//				 break;
//			 }
//		 }
//		 if(importedStatus == null) {
//			 importedStatus = new AppointmentStatus();
//			 importedStatus.setActive(1);
//			 importedStatus.setColor("#DDDDDD");
//			 importedStatus.setDescription("Imported");
//			 importedStatus.setEditable(1);
//			 importedStatus.setIcon("5.gif");
//			 if(appointmentStatusDao.findByStatus("i") == null) {
//				 importedStatus.setStatus("i");
//			 } else {
//				 importedStatus.setStatus("I");
//			 }
//			 appointmentStatusDao.persist(importedStatus);
//		 }
//		return importedStatus.getStatus();
//	}

    String tryToMapRoute(String route) {
        String ret = "";

        if ("Oral".equals(route)) {
            ret = "PO";
        } else if ("PO".equals(route)) {
            ret = "PO";
        } else if ("Intramuscular".equals(route)) {
            ret = "IM";
        } else if ("IM".equals(route)) {
            ret = "IM";
        } else if ("Intradermal".equals(route)) {
            ret = "ID";
        } else if ("ID".equals(route)) {
            ret = "ID";
        } else if ("Intranasal".equals(route)) {
            ret = "IN";
        } else if ("IN".equals(route)) {
            ret = "IN";
        } else if ("Subcutaneous".equals(route)) {
            ret = "SC";
        } else if ("SC".equals(route)) {
            ret = "SC";
        }

        return ret;
    }

    private File importFile = null;
    private String importFileFileName;
    private boolean matchProviderNames = true;
    private int timeshiftInDays;
    private String courseId;

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }

    public String getImportFileFileName() {
        return importFileFileName;
    }

    public void setImportFileFileName(String importFileFileName) {
        this.importFileFileName = importFileFileName;
    }

    public boolean isMatchProviderNames() {
        return matchProviderNames;
    }

    public void setMatchProviderNames(boolean matchProviderNames) {
        this.matchProviderNames = matchProviderNames;
    }

    public int getTimeshiftInDays() {
        return timeshiftInDays;
    }

    public void setTimeshiftInDays(int timeshiftInDays) {
        this.timeshiftInDays = timeshiftInDays;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }
}
