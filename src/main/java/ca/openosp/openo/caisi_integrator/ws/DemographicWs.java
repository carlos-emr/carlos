package ca.openosp.openo.caisi_integrator.ws;

import java.util.Calendar;
import java.util.List;
import javax.jws.WebResult;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.RequestWrapper;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.jws.WebService;

@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "DemographicWs")
@XmlSeeAlso({ ObjectFactory.class })
public interface DemographicWs
{
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicLabResult", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicLabResult")
    @ResponseWrapper(localName = "getCachedDemographicLabResultResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicLabResultResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicLabResult getCachedDemographicLabResult(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdLabResultCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurements", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurements")
    @ResponseWrapper(localName = "setCachedMeasurementsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementsResponse")
    void setCachedMeasurements(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurement> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicAllergies", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicAllergies")
    @ResponseWrapper(localName = "getLinkedCachedDemographicAllergiesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicAllergiesResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicAllergy> getLinkedCachedDemographicAllergies(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicAllergies", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicAllergies")
    @ResponseWrapper(localName = "setCachedDemographicAllergiesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicAllergiesResponse")
    void setCachedDemographicAllergies(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicAllergy> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicNotes", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotes")
    @ResponseWrapper(localName = "getLinkedCachedDemographicNotesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotesResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicNote> getLinkedCachedDemographicNotes(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurementMaps", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementMaps")
    @ResponseWrapper(localName = "setCachedMeasurementMapsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementMapsResponse")
    void setCachedMeasurementMaps(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurementMap> p0);
    
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicDocumentContents", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentContents")
    @ResponseWrapper(localName = "addCachedDemographicDocumentContentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentContentsResponse")
    void addCachedDemographicDocumentContents(@WebParam(name = "arg0", targetNamespace = "") final int p0, @WebParam(name = "arg1", targetNamespace = "") final byte[] p1);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicIssuesByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicIssuesByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicIssuesByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicIssuesByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicIssue> getLinkedCachedDemographicIssuesByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicHL7Labs", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicHL7Labs")
    @ResponseWrapper(localName = "setCachedDemographicHL7LabsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicHL7LabsResponse")
    void setCachedDemographicHL7Labs(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicHL7LabResult> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicPreventionsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicPreventionsByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicPreventionsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicPreventionsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicPrevention> getLinkedCachedDemographicPreventionsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setDemographic", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetDemographic")
    @ResponseWrapper(localName = "setDemographicResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetDemographicResponse")
    void setDemographic(@WebParam(name = "arg0", targetNamespace = "") final DemographicTransfer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedAppointments", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAppointments")
    @ResponseWrapper(localName = "getLinkedCachedAppointmentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAppointmentsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedAppointment> getLinkedCachedAppointments(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getDirectlyLinkedDemographicsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDirectlyLinkedDemographicsByDemographicId")
    @ResponseWrapper(localName = "getDirectlyLinkedDemographicsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDirectlyLinkedDemographicsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<DemographicTransfer> getDirectlyLinkedDemographicsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicDrugsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDrugsByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicDrugsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDrugsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicDrug> getLinkedCachedDemographicDrugsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurementExts", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementExts")
    @ResponseWrapper(localName = "setCachedMeasurementExtsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementExtsResponse")
    void setCachedMeasurementExts(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurementExt> p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedAdmissions", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAdmissions")
    @ResponseWrapper(localName = "setCachedAdmissionsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAdmissionsResponse")
    void setCachedAdmissions(@WebParam(name = "arg0", targetNamespace = "") final List<CachedAdmission> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicDocument", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocument")
    @ResponseWrapper(localName = "getCachedDemographicDocumentResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocumentResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicDocument getCachedDemographicDocument(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdIntegerCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicPreventionsByPreventionId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicPreventionsByPreventionId")
    @ResponseWrapper(localName = "getCachedDemographicPreventionsByPreventionIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicPreventionsByPreventionIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicPrevention getCachedDemographicPreventionsByPreventionId(@WebParam(name = "preventionId", targetNamespace = "") final FacilityIdIntegerCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicHL7LabResult", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicHL7LabResult")
    @ResponseWrapper(localName = "addCachedDemographicHL7LabResultResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicHL7LabResultResponse")
    void addCachedDemographicHL7LabResult(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicHL7LabResult p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicPreventions", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicPreventions")
    @ResponseWrapper(localName = "setCachedDemographicPreventionsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicPreventionsResponse")
    void setCachedDemographicPreventions(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicPrevention> p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedBillingOnItem", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedBillingOnItem")
    @ResponseWrapper(localName = "setCachedBillingOnItemResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedBillingOnItemResponse")
    void setCachedBillingOnItem(@WebParam(name = "arg0", targetNamespace = "") final List<CachedBillingOnItem> p0);
    
    @WebMethod
    @RequestWrapper(localName = "linkDemographics", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.LinkDemographics")
    @ResponseWrapper(localName = "linkDemographicsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.LinkDemographicsResponse")
    void linkDemographics(@WebParam(name = "creatorCaisiProviderId", targetNamespace = "") final String p0, @WebParam(name = "caisiDemographicIdAtCurrentFacility", targetNamespace = "") final Integer p1, @WebParam(name = "integratorDemographicFacilityIdOnIntegrator", targetNamespace = "") final Integer p2, @WebParam(name = "caisiDemographicIdOnIntegrator", targetNamespace = "") final Integer p3);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicMeasurementByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicMeasurementByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicMeasurementByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicMeasurementByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedMeasurement> getLinkedCachedDemographicMeasurementByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicDocuments", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDocuments")
    @ResponseWrapper(localName = "getLinkedCachedDemographicDocumentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDocumentsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicDocument> getLinkedCachedDemographicDocuments(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "deleteCachedDemographicPreventions", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicPreventions")
    @ResponseWrapper(localName = "deleteCachedDemographicPreventionsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicPreventionsResponse")
    void deleteCachedDemographicPreventions(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final List<Integer> p1);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDxresearch", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDxresearch")
    @ResponseWrapper(localName = "setCachedDxresearchResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDxresearchResponse")
    void setCachedDxresearch(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDxresearch> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedAdmissionsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAdmissionsByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedAdmissionsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAdmissionsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedAdmission> getLinkedCachedAdmissionsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicForm", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicForm")
    @ResponseWrapper(localName = "getCachedDemographicFormResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicFormResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicForm getCachedDemographicForm(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdIntegerCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicLabResults", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicLabResults")
    @ResponseWrapper(localName = "getLinkedCachedDemographicLabResultsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicLabResultsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicLabResult> getLinkedCachedDemographicLabResults(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedAppointments", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAppointments")
    @ResponseWrapper(localName = "setCachedAppointmentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAppointmentsResponse")
    void setCachedAppointments(@WebParam(name = "arg0", targetNamespace = "") final List<CachedAppointment> p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedEformValues", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformValues")
    @ResponseWrapper(localName = "setCachedEformValuesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformValuesResponse")
    void setCachedEformValues(@WebParam(name = "arg0", targetNamespace = "") final List<CachedEformValue> p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicNotes", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicNotes")
    @ResponseWrapper(localName = "setCachedDemographicNotesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicNotesResponse")
    void setCachedDemographicNotes(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicNote> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicNotesByIds", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotesByIds")
    @ResponseWrapper(localName = "getLinkedCachedDemographicNotesByIdsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotesByIdsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicNote> getLinkedCachedDemographicNotesByIds(@WebParam(name = "cachedDemographicNoteCompositePk", targetNamespace = "") final List<CachedDemographicNoteCompositePk> p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicDrugs", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicDrugs")
    @ResponseWrapper(localName = "setCachedDemographicDrugsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicDrugsResponse")
    void setCachedDemographicDrugs(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicDrug> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getConsentState", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetConsentState")
    @ResponseWrapper(localName = "getConsentStateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetConsentStateResponse")
    @WebResult(name = "return", targetNamespace = "")
    GetConsentTransfer getConsentState(@WebParam(name = "integratorFacilityAndDemographicId", targetNamespace = "") final FacilityIdIntegerCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicDocumentContents", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocumentContents")
    @ResponseWrapper(localName = "getCachedDemographicDocumentContentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocumentContentsResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicDocumentContents getCachedDemographicDocumentContents(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdIntegerCompositePk p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicConsent", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicConsent")
    @ResponseWrapper(localName = "setCachedDemographicConsentResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicConsentResponse")
    void setCachedDemographicConsent(@WebParam(name = "consentTransfer", targetNamespace = "") final SetConsentTransfer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurementTypes", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementTypes")
    @ResponseWrapper(localName = "setCachedMeasurementTypesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementTypesResponse")
    void setCachedMeasurementTypes(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurementType> p0);
    
    @WebMethod
    @RequestWrapper(localName = "deleteCachedDemographicIssues", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicIssues")
    @ResponseWrapper(localName = "deleteCachedDemographicIssuesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicIssuesResponse")
    void deleteCachedDemographicIssues(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final List<FacilityIdDemographicIssueCompositePk> p1);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicNoteMetaData", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNoteMetaData")
    @ResponseWrapper(localName = "getLinkedCachedDemographicNoteMetaDataResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNoteMetaDataResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicNote> getLinkedCachedDemographicNoteMetaData(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedDemographicsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedDemographicsByDemographicId")
    @ResponseWrapper(localName = "getLinkedDemographicsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedDemographicsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<DemographicTransfer> getLinkedDemographicsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    @WebMethod
    @RequestWrapper(localName = "setLastPushDate", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetLastPushDate")
    @ResponseWrapper(localName = "setLastPushDateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetLastPushDateResponse")
    void setLastPushDate(@WebParam(name = "arg0", targetNamespace = "") final int p0);
    
    @WebMethod
    @RequestWrapper(localName = "unLinkDemographics", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.UnLinkDemographics")
    @ResponseWrapper(localName = "unLinkDemographicsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.UnLinkDemographicsResponse")
    void unLinkDemographics(@WebParam(name = "caisiDemographicIdAtCurrentFacility", targetNamespace = "") final Integer p0, @WebParam(name = "integratorDemographicFacilityIdOnIntegrator", targetNamespace = "") final Integer p1, @WebParam(name = "caisiDemographicIdOnIntegrator", targetNamespace = "") final Integer p2);
    
    @WebMethod
    @RequestWrapper(localName = "getDemographicsPushedAfterDate", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicsPushedAfterDate")
    @ResponseWrapper(localName = "getDemographicsPushedAfterDateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicsPushedAfterDateResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<DemographicPushDate> getDemographicsPushedAfterDate(@WebParam(name = "arg0", targetNamespace = "") final Calendar p0);
    
    @WebMethod
    @RequestWrapper(localName = "getDemographicByFacilityIdAndDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicByFacilityIdAndDemographicId")
    @ResponseWrapper(localName = "getDemographicByFacilityIdAndDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicByFacilityIdAndDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    DemographicTransfer getDemographicByFacilityIdAndDemographicId(@WebParam(name = "integratorFacilityId", targetNamespace = "") final Integer p0, @WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p1);
    
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicForm", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicForm")
    @ResponseWrapper(localName = "addCachedDemographicFormResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicFormResponse")
    void addCachedDemographicForm(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicForm p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicIssues", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicIssues")
    @ResponseWrapper(localName = "setCachedDemographicIssuesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicIssuesResponse")
    void setCachedDemographicIssues(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicIssue> p0);
    
    @WebMethod
    @RequestWrapper(localName = "getMatchingDemographics", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMatchingDemographics")
    @ResponseWrapper(localName = "getMatchingDemographicsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMatchingDemographicsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<MatchingDemographicTransferScore> getMatchingDemographics(@WebParam(name = "arg0", targetNamespace = "") final MatchingDemographicParameters p0);
    
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicDocument", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocument")
    @ResponseWrapper(localName = "addCachedDemographicDocumentResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentResponse")
    void addCachedDemographicDocument(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicDocument p0);
    
    @WebMethod
    @RequestWrapper(localName = "setCachedEformData", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformData")
    @ResponseWrapper(localName = "setCachedEformDataResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformDataResponse")
    void setCachedEformData(@WebParam(name = "arg0", targetNamespace = "") final List<CachedEformData> p0);
    
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicLabResult", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicLabResult")
    @ResponseWrapper(localName = "addCachedDemographicLabResultResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicLabResultResponse")
    void addCachedDemographicLabResult(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicLabResult p0);
    
    @WebMethod
    @RequestWrapper(localName = "getDemographicIdPushedAfterDateByRequestingFacility", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicIdPushedAfterDateByRequestingFacility")
    @ResponseWrapper(localName = "getDemographicIdPushedAfterDateByRequestingFacilityResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicIdPushedAfterDateByRequestingFacilityResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<Integer> getDemographicIdPushedAfterDateByRequestingFacility(@WebParam(name = "arg0", targetNamespace = "") final Calendar p0);
    
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicForms", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicForms")
    @ResponseWrapper(localName = "getLinkedCachedDemographicFormsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicFormsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicForm> getLinkedCachedDemographicForms(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final String p1);
    
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicDocumentAndContents", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentAndContents")
    @ResponseWrapper(localName = "addCachedDemographicDocumentAndContentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentAndContentsResponse")
    void addCachedDemographicDocumentAndContents(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicDocument p0, @WebParam(name = "arg1", targetNamespace = "") final byte[] p1);
}
