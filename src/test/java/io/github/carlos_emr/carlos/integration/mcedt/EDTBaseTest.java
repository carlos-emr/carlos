package io.github.carlos_emr.carlos.integration.mcedt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.integration.mcedt.DelegateFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.logging.log4j.Logger;
import org.apache.xml.security.Init;

import org.apache.xml.security.utils.resolver.ResourceResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import io.github.carlos_emr.carlos.commn.dao.utils.ConfigUtils;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilderConfig;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.CidPrefixResourceResolver;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.DownloadData;
import ca.ontario.health.edt.DownloadResult;
import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.Faultexception;
import ca.ontario.health.edt.ResourceResult;
import ca.ontario.health.edt.ResponseResult;
import ca.ontario.health.edt.TypeListData;
import ca.ontario.health.edt.TypeListResult;
import ca.ontario.health.edt.UpdateRequest;
import ca.ontario.health.edt.UploadData;
import io.github.carlos_emr.OscarProperties;

public abstract class EDTBaseTest {
    protected static Logger logger = MiscUtils.getLogger();

    protected enum FilePath {
        MCEDT_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Claim_File.txt"),
        MCEDT_STALE_DATED_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Stale_Dated_Claim_File.txt"),
        MCEDT_OBEC_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/OBEC_FILE.txt"),
        MCEDT_LARGE_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MOH_LARGE_CLAIMS.txt"),
        VENDOR_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Vendor_Claim_File.txt"),
        VENDOR_STALE_DATED_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Vendor_Stale_Dated_Claim_File.txt"),
        VENDOR_OBEC_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Vendor_OBEC_File.TXT"),
        VENDOR_LARGE_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Vendor_Large_Claim_File.txt"),
        UPDATED_MCEDT_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Updated_Claim_File.txt"),
        UPDATED_MCEDT_STALE_DATED_CLAIMS_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Updated_Stale_Dated_Claim_File.txt"),
        UPDATED_MCEDT_OBEC_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/Updated_OBEC_File.txt"),
        MAL_FORMED_HEADER_MCEDT_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MAL_FORMED_HEADER_CLAIM_FILE.txt"),
        MAL_FORMED_HEADER_MCEDT_STALE_DATED_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MAL_FORMED_HEADER_MCEDT_STALE_DATED_CLAIM_FILE.txt"),
        MISSING_BILL_NUMBER_MCEDT_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MISSING_BILL_NUMBER_MCEDT_CLAIM_FILE.txt"),
        MISMATCH_HEADER_COUNT_MCEDT_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MISMATCH_HEADER_COUNT_MCEDT_CLAIM_FILE.txt"),
        MISMATCH_2_HEADER_COUNT_MCEDT_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MISMATCH_2_HEADER_COUNT_MCEDT_CLAIM_FILE.txt"),
        MISMATCH_RECORD_COUNT_MCEDT_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/MISMATCH_RECORD_COUNT_MCEDT_CLAIM_FILE.txt"),
        LESS_THAN_79_BYTES_MCEDT_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/LESS_THAN_79_BYTES_MCEDT_CLAIM_FILE.txt"),
        LESS_THAN_79_BYTES_MCEDT_STALE_DATED_CLAIM_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/LESS_THAN_79_BYTES_MCEDT_CLAIM_FILE.txt"),
        INVALID_TRANSACTION_CODE_MCEDT_OBEC_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/INVALID_TRANSACTION_CODE_MCEDT_OBEC_FILE.txt"),
        INVALID_LENGTH_HEALTH_NUMBER_MCEDT_OBEC_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/INVALID_LENGTH_HEALTH_NUMBER_MCEDT_OBEC_FILE.txt"),
        NON_NUMERIC_HEALTH_NUMBER_MCEDT_OBEC_FILE("src/test/resources/io/github/carlos_emr/carlos/integration/mcedt/NON_NUMERIC_HEALTH_NUMBER_MCEDT_OBEC_FILE.txt");

        private final String path;

        FilePath(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    protected enum ResourceType {
        UPLOAD_CLAIM_FILE("CL"),
        UPLOAD_OBEC_INBOUND_FILE("OB"),
        UPLOAD_ESUBMIT_SUBMISSION("ESU"),
        UPLOAD_STALE_DATED_CLAIM_FILE("SDC"),
        DOWNLOAD_OBEC_RESPONSE("OO"),
        DOWNLOAD_ESUBMIT_SUBMISSION_CONFIRMATION("ESC"),
        DOWNLOAD_COMPENSATION_INCREASE_REPORT("CIR"),
        DOWNLOAD_REMITTANCE_ADVICE_EXTRACT("RS"),
        DOWNLOAD_MLPR_PDF_LETTERS("MLP"),
        DOWNLOAD_DETAILED_CAT_PDF("DCP"),
        DOWNLOAD_ESUBMIT_FORM_RETURN("EFR"),
        DOWNLOAD_ERROR_REPORTS_EXTRACT("ES"),
        DOWNLOAD_SECURE_MESSAGING_PDF("SMP"),
        DOWNLOAD_TARGET_POPULATION_PDF("TPP"),
        DOWNLOAD_DETAILED_CAT_CSV("DCT"),
        DOWNLOAD_TARGET_POPULATION_TAB("TPT"),
        DOWNLOAD_OBEC_MAIL_FILE_REJECT_MESSAGE("OR"),
        DOWNLOAD_REMITTANCE_ADVICE("RA"),
        DOWNLOAD_ROSTER_CAPITANION_REPORT_XML("RCX"),
        DOWNLOAD_PAYMENT_SUMMARY_REPORT_PDF("PSP"),
        DOWNLOAD_CLAIMS_MAIL_FILE_REJECT_MESSAGE("MR"),
        DOWNLOAD_GENERAL_COMMUNICATIONS("GCM"),
        DOWNLOAD_PAYMENT_SUMMARY_REPORT_XML("PSX"),
        DOWNLOAD_BATCH_EDIT("BE"),
        DOWNLOAD_EC_OUTSIDE_USE_REPORT("CO"),
        DOWNLOAD_ERROR_REPORTS("ER"),
        DOWNLOAD_EC_SUMMARY_REPORT("CS"),
        DOWNLOAD_ROSTER_CAPITANION_REPORT_PDF("RCP");

        private final String type;

        ResourceType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    protected static EDTDelegate edtDelegate;

    @BeforeClass
    public static void setUpBeforeClass() {
        Init.init();
        ResourceResolver.register(new CidPrefixResourceResolver(), true);
        // Check if the Spring context (bean factory) has been initialized yet
        // Set up the context if it's null
        if(SpringUtils.getBeanFactory() == null) {
            io.github.carlos_emr.OscarProperties p = io.github.carlos_emr.OscarProperties.getInstance();
            // Set the properties
            p.setProperty("db_name", ConfigUtils.getProperty("db_schema") + ConfigUtils.getProperty("db_schema_properties"));
            p.setProperty("db_username", ConfigUtils.getProperty("db_user"));
            p.setProperty("db_password", ConfigUtils.getProperty("db_password"));
            p.setProperty("db_uri", ConfigUtils.getProperty("db_url_prefix"));
            p.setProperty("db_driver", ConfigUtils.getProperty("db_driver"));
            // Load the Spring application context to initialize beans and other components
            // It loads the configurations defined in the specified XML files
            ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext();
            // Set the configuration files for the application context
            context.setConfigLocations(new String[]{"/applicationContext.xml"});
            context.refresh();
            // Set the application context in the Spring utility class, so it can be accessed throughout the application
            SpringUtils.setBeanFactory(context);
        }
    }

    @Before
    public void setUp() {
        edtDelegate = DelegateFactory.getEDTDelegateInstance();
    }

    @After
    public void tearDown() {
        edtDelegate = null;
    }

    protected List<BigInteger> getResourceIds(ResourceResult resourceResult) {
        if (resourceResult == null) {
            return Collections.emptyList();
        }
        List<BigInteger> ids = new ArrayList<BigInteger>();
        for (ResponseResult edtResponse : resourceResult.getResponse()) {
            ids.add(edtResponse.getResourceID());
        }
        return ids;
    }

    protected String printResourceResult(ResourceResult resourceResult) {
        String code = "";
        if (resourceResult == null) {
            return code;
        }
        List<ResponseResult> responseResultList = resourceResult.getResponse();

        System.out.println("Audit ID: " + resourceResult.getAuditID());
        for (ResponseResult responseResult : responseResultList) {
            System.out.println("Description: " + responseResult.getDescription());
            System.out.println("Resource ID: " + responseResult.getResourceID());
            System.out.println("Code: " + responseResult.getResult().getCode());
            code = responseResult.getResult().getCode();
            System.out.println("Message: " + responseResult.getResult().getMsg());
            System.out.println("Status: " + responseResult.getStatus());
            System.out.println();
        }
        return code;
    }

    protected void printDetailList(Detail detail) {
        if (detail == null) {
            return;
        }
        System.out.println("Audit ID: " + detail.getAuditID());
        System.out.println("Result Size(pages): " + detail.getResultSize());

        System.out.println("Total Resources (returned): " + detail.getData().size());
        for (DetailData data : detail.getData()) {
            System.out.print("Description: " + data.getDescription());
            System.out.print(" Resource Type: " + data.getResourceType());
            System.out.print(" Resource ID: " + data.getResourceID());
            System.out.print(" Code: " + data.getResult().getCode());
            System.out.print(" Message: " + data.getResult().getMsg());
            System.out.print(" Timestamp: " + data.getModifyTimestamp());
            System.out.print(" Status: " + data.getStatus());
            System.out.println();
        }
        System.out.println();
    }

    protected void printTypeList(TypeListResult typeListData) {
        System.out.println("Audit ID: " + typeListData.getAuditID());

        for (TypeListData data : typeListData.getData()) {
            assertNotNull(data);
            System.out.print("Access: " + data.getAccess());
            System.out.print(" Description: " + data.getDescriptionEn());
            System.out.print(" Resource Type: " + data.getResourceType());
            System.out.print(" Code: " + data.getResult().getCode());
            System.out.print(" Message: " + data.getResult().getMsg());
            System.out.println();
        }
    }

    protected void printDownloadResult(DownloadResult downloadResult) {
        System.out.println("Audit ID: " + downloadResult.getAuditID());
        for (DownloadData data : downloadResult.getData()) {
            assertNotNull(data);
            System.out.println("Resource ID: " + data.getResourceID());
            System.out.println("Resource Type: " + data.getResourceType());
            System.out.println("Description: " + data.getDescription());
            //System.out.println("Content:\n" + new String(data.getContent()));
            System.out.println("Code: " + data.getResult().getCode());
            System.out.println("Message: " + data.getResult().getMsg());
            System.out.println();
        }
    }

    protected void printFaultException(Faultexception e) {
        System.out.println("Audit ID: null");
        System.out.println("Resource ID: null");
        System.out.println("Code: " + e.getFaultInfo().getCode());
        System.out.println("Message: " + e.getFaultInfo().getMessage());
        System.out.println("Status: FAILED");
        System.out.println();
    }

    protected void assertEqualsOnResponseCode(String expected, ResourceResult resourceResult) {
        if (resourceResult == null) {
            return;
        }
        List<ResponseResult> responseResultList = resourceResult.getResponse();
        assertNotNull(responseResultList);
        assertFalse(responseResultList.isEmpty());
        for (ResponseResult responseResult : responseResultList) {
            assertEquals(expected, responseResult.getResult().getCode());
        }
    }

    protected UploadData createUploadData(FilePath filePath, ResourceType resourceType) {
        String filePathString = filePath.getPath();
        UploadData uploadData = new UploadData();
        uploadData.setContent(readFileAsBytes(filePathString));
        uploadData.setDescription(filePathString.substring(filePathString.lastIndexOf("/") + 1));
        uploadData.setResourceType(resourceType.getType());
        return uploadData;
    }

    protected List<UpdateRequest> createUpdateRequestList(FilePath filePath, ResourceResult resourceResult) {
        if (resourceResult == null) {
            return Collections.emptyList();
        }
        String filePathString = filePath.getPath();
        List<UpdateRequest> updateRequests = new ArrayList<>();
        for (ResponseResult responseResult : resourceResult.getResponse()) {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setContent(readFileAsBytes(filePathString));
            updateRequest.setResourceID(responseResult.getResourceID());
            updateRequests.add(updateRequest);
        }
        return updateRequests;
    }

    protected List<UpdateRequest> createUpdateRequestList(FilePath filePath, List<BigInteger> resourceIds) {
        if (resourceIds == null) {
            return Collections.emptyList();
        }
        String filePathString = filePath.getPath();
        List<UpdateRequest> updateRequests = new ArrayList<>();
        for (BigInteger id : resourceIds) {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.setContent(readFileAsBytes(filePathString));
            updateRequest.setResourceID(id);
            updateRequests.add(updateRequest);
        }
        return updateRequests;
    }

    protected byte[] readFileAsBytes(String filePathString) {
        Path path = Paths.get(filePathString);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            logger.error("Error reading file: " + e.getMessage(), e);
            return null;
        }
    }

    protected void setClientTimeouts(EDTDelegate edtDelegate) {
        Client client = ClientProxy.getClient(edtDelegate);
        HTTPConduit httpConduit = (HTTPConduit) client.getConduit();
        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        httpClientPolicy.setConnectionTimeout(5000); // 5 Seconds
        httpClientPolicy.setReceiveTimeout(5000); // 5 Seconds
        httpConduit.setClient(httpClientPolicy);
    }

    protected static EDTDelegate newDelegate(String serviceId) {
        OscarProperties props = OscarProperties.getInstance();
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setLoggingRequired(!Boolean.valueOf(props.getProperty("mcedt.logging.skip")));
        config.setKeystoreUser(props.getProperty("mcedt.keystore.user"));
        config.setKeystorePassword(props.getProperty("mcedt.keystore.pass"));
        config.setUserNameTokenUser(props.getProperty("mcedt.service.user"));
        config.setUserNameTokenPassword(props.getProperty("mcedt.service.pass"));
        config.setServiceUrl(props.getProperty("mcedt.service.url"));
        config.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config.setServiceId(serviceId == null ? props.getProperty("mcedt.service.id") : serviceId);
        config.setMtomEnabled(true);
        EdtClientBuilder builder = new EdtClientBuilder(config);
        setExternalClientKeystoreFilename(builder, props.getProperty("mcedt.service.clientKeystore.properties"));
        EDTDelegate edtDelegate = builder.build(EDTDelegate.class);
        if (logger.isInfoEnabled()) {
            logger.info("Created new EDT delegate " + edtDelegate);
        }
        return edtDelegate;
    }

    /**
     * Set an external client keystore properties file for the EDT client builder in test context.
     * This method configures a custom keystore properties file path for MCEDT service
     * client certificate authentication during testing. If the provided path is null or the
     * file does not exist, the default keystore at src/main/resources/clientKeystore.properties
     * will be used.
     *
     * @param builder EdtClientBuilder the EDT client builder instance to configure
     * @param clientKeystorePropertiesPath String the absolute path to the client keystore properties file, or null to use default
     * @since 2026-01-29
     */
    protected static void setExternalClientKeystoreFilename(EdtClientBuilder builder, String clientKeystorePropertiesPath) {
        if (clientKeystorePropertiesPath == null) {
            return;
        }
        Path signaturePropFile = Paths.get(clientKeystorePropertiesPath);
        if (Files.exists(signaturePropFile)) {
            File file = new File(clientKeystorePropertiesPath);
            try {
                builder.setClientKeystoreFilename(file.toURI().toURL().toString());
            } catch (MalformedURLException e) {
                logger.error("Malformed URL: " + clientKeystorePropertiesPath, e);
            }
        }
    }

}
