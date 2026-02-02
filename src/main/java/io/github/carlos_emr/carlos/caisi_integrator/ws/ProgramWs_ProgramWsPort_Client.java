package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.List;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import javax.xml.namespace.QName;

public final class ProgramWs_ProgramWsPort_Client
{
    private static final QName SERVICE_NAME;
    
    private ProgramWs_ProgramWsPort_Client() {
    }
    
    public static void main(final String[] array) throws Exception {
        URL url = ProgramWsService.WSDL_LOCATION;
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
        final ProgramWs programWsPort = new ProgramWsService(url, ProgramWs_ProgramWsPort_Client.SERVICE_NAME).getProgramWsPort();
        System.out.println("Invoking setCachedPrograms...");
        programWsPort.setCachedPrograms(null);
        System.out.println("Invoking getAllProgramsAllowingIntegratedReferrals...");
        System.out.println("getAllProgramsAllowingIntegratedReferrals.result=" + programWsPort.getAllProgramsAllowingIntegratedReferrals());
        System.out.println("Invoking deleteCachedProgramsMissingFromList...");
        programWsPort.deleteCachedProgramsMissingFromList(null);
        System.out.println("Invoking getAllPrograms...");
        System.out.println("getAllPrograms.result=" + programWsPort.getAllPrograms());
        System.exit(0);
    }
    
    static {
        SERVICE_NAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "ProgramWsService");
    }
}
