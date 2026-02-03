package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import javax.xml.namespace.QName;

public final class FacilityWs_FacilityWsPort_Client
{
    private static final QName SERVICE_NAME;
    
    private FacilityWs_FacilityWsPort_Client() {
    }
    
    public static void main(final String[] array) throws Exception {
        URL url = FacilityWsService.WSDL_LOCATION;
        if (array.length > 0 && array[0] != null && !"".equals(array[0])) {
            final File file = new File(array[0]);
            try {
                if (file.exists()) {
                    url = file.toURI().toURL();
                }
                else {
                    url = new URL(array[0]);
                }
            }
            catch (final MalformedURLException ex) {
                ex.printStackTrace();
            }
        }
        final FacilityWs facilityWsPort = new FacilityWsService(url, FacilityWs_FacilityWsPort_Client.SERVICE_NAME).getFacilityWsPort();
        System.out.println("Invoking createImportLogWithStatus...");
        System.out.println("createImportLogWithStatus.result=" + facilityWsPort.createImportLogWithStatus("", "", null, null, "", ""));
        System.out.println("Invoking getMyFacility...");
        System.out.println("getMyFacility.result=" + facilityWsPort.getMyFacility());
        System.out.println("Invoking errorImportLog...");
        System.out.println("errorImportLog.result=" + facilityWsPort.errorImportLog(null));
        System.out.println("Invoking updateMyFacilityLastUpdateDate...");
        facilityWsPort.updateMyFacilityLastUpdateDate(null);
        System.out.println("Invoking completeImportLog...");
        System.out.println("completeImportLog.result=" + facilityWsPort.completeImportLog(null));
        System.out.println("Invoking setMyFacility...");
        facilityWsPort.setMyFacility(null);
        System.out.println("Invoking getAllFacility...");
        System.out.println("getAllFacility.result=" + facilityWsPort.getAllFacility());
        System.out.println("Invoking canProcessFile...");
        System.out.println("canProcessFile.result=" + facilityWsPort.canProcessFile("", "", null, null, ""));
        System.out.println("Invoking getImportLogByFilenameAndChecksum...");
        System.out.println("getImportLogByFilenameAndChecksum.result=" + facilityWsPort.getImportLogByFilenameAndChecksum("", ""));
        System.out.println("Invoking createImportLog...");
        System.out.println("createImportLog.result=" + facilityWsPort.createImportLog("", "", null, null, ""));
        System.out.println("Invoking getImportLogsSince...");
        System.out.println("getImportLogsSince.result=" + facilityWsPort.getImportLogsSince(null));
        System.exit(0);
    }
    
    static {
        SERVICE_NAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "FacilityWsService");
    }
}
