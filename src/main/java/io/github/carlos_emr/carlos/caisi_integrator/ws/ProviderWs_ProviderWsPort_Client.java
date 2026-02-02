package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.util.List;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import javax.xml.namespace.QName;

public final class ProviderWs_ProviderWsPort_Client
{
    private static final QName SERVICE_NAME;
    
    private ProviderWs_ProviderWsPort_Client() {
    }
    
    public static void main(final String[] array) throws Exception {
        URL url = ProviderWsService.WSDL_LOCATION;
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
        final ProviderWs providerWsPort = new ProviderWsService(url, ProviderWs_ProviderWsPort_Client.SERVICE_NAME).getProviderWsPort();
        System.out.println("Invoking setCachedProviders...");
        providerWsPort.setCachedProviders(null);
        System.out.println("Invoking deactivateProviderCommunication...");
        providerWsPort.deactivateProviderCommunication(null);
        System.out.println("Invoking addProviderComunication...");
        providerWsPort.addProviderComunication(null);
        System.out.println("Invoking getProviderRoles...");
        System.out.println("getProviderRoles.result=" + providerWsPort.getProviderRoles(null));
        System.out.println("Invoking getAllProviders...");
        System.out.println("getAllProviders.result=" + providerWsPort.getAllProviders());
        System.out.println("Invoking getProviderCommunications...");
        System.out.println("getProviderCommunications.result=" + providerWsPort.getProviderCommunications("", "", null));
        System.exit(0);
    }
    
    static {
        SERVICE_NAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "ProviderWsService");
    }
}
