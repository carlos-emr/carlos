package io.github.carlos_emr.carlos.caisi_integrator.ws;

import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;
import javax.xml.namespace.QName;

public final class DemographicWs_DemographicWsPort_Client
{
    private static final QName SERVICE_NAME;
    
    private DemographicWs_DemographicWsPort_Client() {
    }
    
    public static void main(final String[] array) throws Exception {
        URL url = DemographicWsService.WSDL_LOCATION;
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
        final DemographicWs demographicWsPort = new DemographicWsService(url, DemographicWs_DemographicWsPort_Client.SERVICE_NAME).getDemographicWsPort();
        System.out.println("Invoking getCachedDemographicLabResult...");
        System.out.println("getCachedDemographicLabResult.result=" + demographicWsPort.getCachedDemographicLabResult(null));
        System.out.println("Invoking setCachedMeasurements...");
        demographicWsPort.setCachedMeasurements(null);
        System.out.println("Invoking getLinkedCachedDemographicAllergies...");
        System.out.println("getLinkedCachedDemographicAllergies.result=" + demographicWsPort.getLinkedCachedDemographicAllergies(null));
        System.out.println("Invoking setCachedDemographicAllergies...");
        demographicWsPort.setCachedDemographicAllergies(null);
        System.out.println("Invoking getLinkedCachedDemographicNotes...");
        System.out.println("getLinkedCachedDemographicNotes.result=" + demographicWsPort.getLinkedCachedDemographicNotes(null));
        System.out.println("Invoking setCachedMeasurementMaps...");
        demographicWsPort.setCachedMeasurementMaps(null);
        System.out.println("Invoking addCachedDemographicDocumentContents...");
        demographicWsPort.addCachedDemographicDocumentContents(0, new byte[0]);
        System.out.println("Invoking getLinkedCachedDemographicIssuesByDemographicId...");
        System.out.println("getLinkedCachedDemographicIssuesByDemographicId.result=" + demographicWsPort.getLinkedCachedDemographicIssuesByDemographicId(null));
        System.out.println("Invoking setCachedDemographicHL7Labs...");
        demographicWsPort.setCachedDemographicHL7Labs(null);
        System.out.println("Invoking getLinkedCachedDemographicPreventionsByDemographicId...");
        System.out.println("getLinkedCachedDemographicPreventionsByDemographicId.result=" + demographicWsPort.getLinkedCachedDemographicPreventionsByDemographicId(null));
        System.out.println("Invoking setDemographic...");
        demographicWsPort.setDemographic(null);
        System.out.println("Invoking getLinkedCachedAppointments...");
        System.out.println("getLinkedCachedAppointments.result=" + demographicWsPort.getLinkedCachedAppointments(null));
        System.out.println("Invoking getDirectlyLinkedDemographicsByDemographicId...");
        System.out.println("getDirectlyLinkedDemographicsByDemographicId.result=" + demographicWsPort.getDirectlyLinkedDemographicsByDemographicId(null));
        System.out.println("Invoking getLinkedCachedDemographicDrugsByDemographicId...");
        System.out.println("getLinkedCachedDemographicDrugsByDemographicId.result=" + demographicWsPort.getLinkedCachedDemographicDrugsByDemographicId(null));
        System.out.println("Invoking setCachedMeasurementExts...");
        demographicWsPort.setCachedMeasurementExts(null);
        System.out.println("Invoking setCachedAdmissions...");
        demographicWsPort.setCachedAdmissions(null);
        System.out.println("Invoking getCachedDemographicDocument...");
        System.out.println("getCachedDemographicDocument.result=" + demographicWsPort.getCachedDemographicDocument(null));
        System.out.println("Invoking getCachedDemographicPreventionsByPreventionId...");
        System.out.println("getCachedDemographicPreventionsByPreventionId.result=" + demographicWsPort.getCachedDemographicPreventionsByPreventionId(null));
        System.out.println("Invoking addCachedDemographicHL7LabResult...");
        demographicWsPort.addCachedDemographicHL7LabResult(null);
        System.out.println("Invoking setCachedDemographicPreventions...");
        demographicWsPort.setCachedDemographicPreventions(null);
        System.out.println("Invoking setCachedBillingOnItem...");
        demographicWsPort.setCachedBillingOnItem(null);
        System.out.println("Invoking linkDemographics...");
        demographicWsPort.linkDemographics("", null, null, null);
        System.out.println("Invoking getLinkedCachedDemographicMeasurementByDemographicId...");
        System.out.println("getLinkedCachedDemographicMeasurementByDemographicId.result=" + demographicWsPort.getLinkedCachedDemographicMeasurementByDemographicId(null));
        System.out.println("Invoking getLinkedCachedDemographicDocuments...");
        System.out.println("getLinkedCachedDemographicDocuments.result=" + demographicWsPort.getLinkedCachedDemographicDocuments(null));
        System.out.println("Invoking deleteCachedDemographicPreventions...");
        demographicWsPort.deleteCachedDemographicPreventions(null, null);
        System.out.println("Invoking setCachedDxresearch...");
        demographicWsPort.setCachedDxresearch(null);
        System.out.println("Invoking getLinkedCachedAdmissionsByDemographicId...");
        System.out.println("getLinkedCachedAdmissionsByDemographicId.result=" + demographicWsPort.getLinkedCachedAdmissionsByDemographicId(null));
        System.out.println("Invoking getCachedDemographicForm...");
        System.out.println("getCachedDemographicForm.result=" + demographicWsPort.getCachedDemographicForm(null));
        System.out.println("Invoking getLinkedCachedDemographicLabResults...");
        System.out.println("getLinkedCachedDemographicLabResults.result=" + demographicWsPort.getLinkedCachedDemographicLabResults(null));
        System.out.println("Invoking setCachedAppointments...");
        demographicWsPort.setCachedAppointments(null);
        System.out.println("Invoking setCachedEformValues...");
        demographicWsPort.setCachedEformValues(null);
        System.out.println("Invoking setCachedDemographicNotes...");
        demographicWsPort.setCachedDemographicNotes(null);
        System.out.println("Invoking getLinkedCachedDemographicNotesByIds...");
        System.out.println("getLinkedCachedDemographicNotesByIds.result=" + demographicWsPort.getLinkedCachedDemographicNotesByIds(null));
        System.out.println("Invoking setCachedDemographicDrugs...");
        demographicWsPort.setCachedDemographicDrugs(null);
        System.out.println("Invoking getConsentState...");
        System.out.println("getConsentState.result=" + demographicWsPort.getConsentState(null));
        System.out.println("Invoking getCachedDemographicDocumentContents...");
        System.out.println("getCachedDemographicDocumentContents.result=" + demographicWsPort.getCachedDemographicDocumentContents(null));
        System.out.println("Invoking setCachedDemographicConsent...");
        demographicWsPort.setCachedDemographicConsent(null);
        System.out.println("Invoking setCachedMeasurementTypes...");
        demographicWsPort.setCachedMeasurementTypes(null);
        System.out.println("Invoking deleteCachedDemographicIssues...");
        demographicWsPort.deleteCachedDemographicIssues(null, null);
        System.out.println("Invoking getLinkedCachedDemographicNoteMetaData...");
        System.out.println("getLinkedCachedDemographicNoteMetaData.result=" + demographicWsPort.getLinkedCachedDemographicNoteMetaData(null));
        System.out.println("Invoking getLinkedDemographicsByDemographicId...");
        System.out.println("getLinkedDemographicsByDemographicId.result=" + demographicWsPort.getLinkedDemographicsByDemographicId(null));
        System.out.println("Invoking setLastPushDate...");
        demographicWsPort.setLastPushDate(0);
        System.out.println("Invoking unLinkDemographics...");
        demographicWsPort.unLinkDemographics(null, null, null);
        System.out.println("Invoking getDemographicsPushedAfterDate...");
        System.out.println("getDemographicsPushedAfterDate.result=" + demographicWsPort.getDemographicsPushedAfterDate(null));
        System.out.println("Invoking getDemographicByFacilityIdAndDemographicId...");
        System.out.println("getDemographicByFacilityIdAndDemographicId.result=" + demographicWsPort.getDemographicByFacilityIdAndDemographicId(null, null));
        System.out.println("Invoking addCachedDemographicForm...");
        demographicWsPort.addCachedDemographicForm(null);
        System.out.println("Invoking setCachedDemographicIssues...");
        demographicWsPort.setCachedDemographicIssues(null);
        System.out.println("Invoking getMatchingDemographics...");
        System.out.println("getMatchingDemographics.result=" + demographicWsPort.getMatchingDemographics(new MatchingDemographicParameters()));
        System.out.println("Invoking addCachedDemographicDocument...");
        demographicWsPort.addCachedDemographicDocument(null);
        System.out.println("Invoking setCachedEformData...");
        demographicWsPort.setCachedEformData(null);
        System.out.println("Invoking addCachedDemographicLabResult...");
        demographicWsPort.addCachedDemographicLabResult(null);
        System.out.println("Invoking getDemographicIdPushedAfterDateByRequestingFacility...");
        System.out.println("getDemographicIdPushedAfterDateByRequestingFacility.result=" + demographicWsPort.getDemographicIdPushedAfterDateByRequestingFacility(null));
        System.out.println("Invoking getLinkedCachedDemographicForms...");
        System.out.println("getLinkedCachedDemographicForms.result=" + demographicWsPort.getLinkedCachedDemographicForms(null, ""));
        System.out.println("Invoking addCachedDemographicDocumentAndContents...");
        demographicWsPort.addCachedDemographicDocumentAndContents(null, new byte[0]);
        System.exit(0);
    }
    
    static {
        SERVICE_NAME = new QName("http://ws.caisi_integrator.oscarehr.org/", "DemographicWsService");
    }
}
