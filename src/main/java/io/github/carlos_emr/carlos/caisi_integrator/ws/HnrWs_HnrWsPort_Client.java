package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.Calendar;
import io.github.carlos_emr.carlos.ws.MatchingClientParameters;
import io.github.carlos_emr.carlos.ws.Client;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import javax.xml.namespace.QName;

public final class HnrWs_HnrWsPort_Client
{
    private static final QName SERVICE_NAME;
    
    private HnrWs_HnrWsPort_Client() {
    }
    
    public static void main(final String[] array) throws Exception {
        URL url = HnrWsService.WSDL_LOCATION;
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
        final HnrWs hnrWsPort = new HnrWsService(url, HnrWs_HnrWsPort_Client.SERVICE_NAME).getHnrWsPort();
        System.out.println("Invoking getHnrClient...");
        final Integer n = null;
        try {
            System.out.println("getHnrClient.result=" + hnrWsPort.getHnrClient(n));
        }
        catch (final ConnectException_Exception ex2) {
            System.out.println("Expected exception: ConnectException has occurred.");
            System.out.println(ex2.toString());
        }
        System.out.println("Invoking setHnrClientData...");
        final Client hnrClientData = new Client();
        try {
            System.out.println("setHnrClientData.result=" + hnrWsPort.setHnrClientData(hnrClientData));
        }
        catch (final DuplicateHinExceptionException ex3) {
            System.out.println("Expected exception: DuplicateHinException_Exception has occurred.");
            System.out.println(ex3.toString());
        }
        catch (final ConnectException_Exception ex4) {
            System.out.println("Expected exception: ConnectException has occurred.");
            System.out.println(ex4.toString());
        }
        catch (final InvalidHinExceptionException ex5) {
            System.out.println("Expected exception: InvalidHinException_Exception has occurred.");
            System.out.println(ex5.toString());
        }
        System.out.println("Invoking setHnrClientHidden...");
        final Integer n2 = null;
        final boolean b = false;
        final Calendar calendar = null;
        try {
            hnrWsPort.setHnrClientHidden(n2, b, calendar);
        }
        catch (final ConnectException_Exception ex6) {
            System.out.println("Expected exception: ConnectException has occurred.");
            System.out.println(ex6.toString());
        }
        System.out.println("Invoking getMatchingHnrClients...");
        final MatchingClientParameters matchingClientParameters = new MatchingClientParameters();
        try {
            System.out.println("getMatchingHnrClients.result=" + hnrWsPort.getMatchingHnrClients(matchingClientParameters));
        }
        catch (final ConnectException_Exception ex7) {
            System.out.println("Expected exception: ConnectException has occurred.");
            System.out.println(ex7.toString());
        }
        System.exit(0);
    }
    
    static {
        SERVICE_NAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "HnrWsService");
    }
}
