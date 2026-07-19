package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import javax.xml.namespace.QName;

public final class ReferralWs_ReferralWsPort_Client
{
    private static final QName SERVICE_NAME;
    
    private ReferralWs_ReferralWsPort_Client() {
    }
    
    public static void main(final String[] array) throws Exception {
        URL url = ReferralWsService.WSDL_LOCATION;
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
        final ReferralWs referralWsPort = new ReferralWsService(url, ReferralWs_ReferralWsPort_Client.SERVICE_NAME).getReferralWsPort();
        System.out.println("Invoking getReferral...");
        System.out.println("getReferral.result=" + referralWsPort.getReferral(null));
        System.out.println("Invoking getLinkedReferrals...");
        System.out.println("getLinkedReferrals.result=" + referralWsPort.getLinkedReferrals(null));
        System.out.println("Invoking getReferralsToProgram...");
        System.out.println("getReferralsToProgram.result=" + referralWsPort.getReferralsToProgram(null));
        System.out.println("Invoking makeReferral...");
        referralWsPort.makeReferral(null);
        System.out.println("Invoking removeReferral...");
        referralWsPort.removeReferral(null);
        System.exit(0);
    }
    
    static {
        SERVICE_NAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "ReferralWsService");
    }
}
