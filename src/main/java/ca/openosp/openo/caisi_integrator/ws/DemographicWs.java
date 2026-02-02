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

/**
 * Demographic Web Service interface for the CAISI Integrator system.
 *
 * This SOAP web service provides comprehensive inter-facility demographic data exchange capabilities
 * for the OpenO EMR CAISI (Client Access to Integrated Services and Information) Integrator.
 * The service enables sharing of patient demographic records, clinical data, and administrative
 * information across multiple healthcare facilities within a community health network.
 *
 * Key capabilities include:
 * - Demographic record synchronization and linking across facilities
 * - Clinical data caching and retrieval (lab results, medications, allergies, preventions)
 * - Administrative data exchange (appointments, billing, consents)
 * - Document and form management
 * - Patient consent state management
 *
 * All methods in this service operate on cached data to improve performance and reduce
 * real-time database load. The service uses composite primary keys (FacilityIdIntegerCompositePk)
 * to uniquely identify records across multiple facilities.
 *
 * Security: All service methods require appropriate authentication and authorization.
 * PHI (Protected Health Information) transmitted through this service must be protected
 * according to HIPAA/PIPEDA requirements.
 *
 * @see DemographicTransfer
 * @see FacilityIdIntegerCompositePk
 * @see ObjectFactory
 * @since 2026-01-24
 */
@WebService(targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", name = "DemographicWs")
@XmlSeeAlso({ ObjectFactory.class })
public interface DemographicWs
{
    /**
     * Retrieves a cached demographic lab result by composite primary key.
     *
     * @param p0 FacilityIdLabResultCompositePk the composite primary key containing facility ID and lab result ID
     * @return CachedDemographicLabResult the cached lab result data, or null if not found
     */
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicLabResult", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicLabResult")
    @ResponseWrapper(localName = "getCachedDemographicLabResultResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicLabResultResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicLabResult getCachedDemographicLabResult(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdLabResultCompositePk p0);
    
    /**
     * Stores or updates a list of cached clinical measurements.
     *
     * @param p0 List&lt;CachedMeasurement&gt; the list of clinical measurements to cache (vital signs, lab values, etc.)
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurements", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurements")
    @ResponseWrapper(localName = "setCachedMeasurementsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementsResponse")
    void setCachedMeasurements(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurement> p0);
    
    /**
     * Retrieves all cached allergies for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve allergies for
     * @return List&lt;CachedDemographicAllergy&gt; list of cached allergy records from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicAllergies", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicAllergies")
    @ResponseWrapper(localName = "getLinkedCachedDemographicAllergiesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicAllergiesResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicAllergy> getLinkedCachedDemographicAllergies(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    /**
     * Stores or updates a list of cached demographic allergies.
     *
     * @param p0 List&lt;CachedDemographicAllergy&gt; the list of allergy records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicAllergies", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicAllergies")
    @ResponseWrapper(localName = "setCachedDemographicAllergiesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicAllergiesResponse")
    void setCachedDemographicAllergies(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicAllergy> p0);

    /**
     * Retrieves all cached clinical notes for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve notes for
     * @return List&lt;CachedDemographicNote&gt; list of cached clinical notes from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicNotes", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotes")
    @ResponseWrapper(localName = "getLinkedCachedDemographicNotesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotesResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicNote> getLinkedCachedDemographicNotes(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Stores or updates a list of cached measurement mappings.
     *
     * @param p0 List&lt;CachedMeasurementMap&gt; the list of measurement mapping records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurementMaps", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementMaps")
    @ResponseWrapper(localName = "setCachedMeasurementMapsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementMapsResponse")
    void setCachedMeasurementMaps(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurementMap> p0);

    /**
     * Adds binary content for a cached demographic document.
     *
     * @param p0 int the document ID
     * @param p1 byte[] the binary document content
     */
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicDocumentContents", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentContents")
    @ResponseWrapper(localName = "addCachedDemographicDocumentContentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentContentsResponse")
    void addCachedDemographicDocumentContents(@WebParam(name = "arg0", targetNamespace = "") final int p0, @WebParam(name = "arg1", targetNamespace = "") final byte[] p1);
    
    /**
     * Retrieves all cached clinical issues for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve issues for
     * @return List&lt;CachedDemographicIssue&gt; list of cached clinical issues from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicIssuesByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicIssuesByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicIssuesByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicIssuesByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicIssue> getLinkedCachedDemographicIssuesByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Stores or updates a list of cached HL7 lab results.
     *
     * @param p0 List&lt;CachedDemographicHL7LabResult&gt; the list of HL7 lab result records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicHL7Labs", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicHL7Labs")
    @ResponseWrapper(localName = "setCachedDemographicHL7LabsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicHL7LabsResponse")
    void setCachedDemographicHL7Labs(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicHL7LabResult> p0);

    /**
     * Retrieves all cached prevention records (immunizations, screening) for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve prevention records for
     * @return List&lt;CachedDemographicPrevention&gt; list of cached prevention records from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicPreventionsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicPreventionsByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicPreventionsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicPreventionsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicPrevention> getLinkedCachedDemographicPreventionsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Stores or updates a demographic record in the integrator.
     *
     * @param p0 DemographicTransfer the demographic data to store or update
     */
    @WebMethod
    @RequestWrapper(localName = "setDemographic", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetDemographic")
    @ResponseWrapper(localName = "setDemographicResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetDemographicResponse")
    void setDemographic(@WebParam(name = "arg0", targetNamespace = "") final DemographicTransfer p0);
    
    /**
     * Retrieves all cached appointments for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve appointments for
     * @return List&lt;CachedAppointment&gt; list of cached appointments from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedAppointments", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAppointments")
    @ResponseWrapper(localName = "getLinkedCachedAppointmentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAppointmentsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedAppointment> getLinkedCachedAppointments(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Retrieves demographics directly linked to the specified demographic (one-hop relationships).
     *
     * @param p0 Integer the CAISI demographic ID to retrieve directly linked demographics for
     * @return List&lt;DemographicTransfer&gt; list of directly linked demographic records
     */
    @WebMethod
    @RequestWrapper(localName = "getDirectlyLinkedDemographicsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDirectlyLinkedDemographicsByDemographicId")
    @ResponseWrapper(localName = "getDirectlyLinkedDemographicsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDirectlyLinkedDemographicsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<DemographicTransfer> getDirectlyLinkedDemographicsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Retrieves all cached medication/drug records for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve drugs for
     * @return List&lt;CachedDemographicDrug&gt; list of cached drug/medication records from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicDrugsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDrugsByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicDrugsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDrugsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicDrug> getLinkedCachedDemographicDrugsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Stores or updates a list of cached measurement extension data.
     *
     * @param p0 List&lt;CachedMeasurementExt&gt; the list of measurement extension records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurementExts", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementExts")
    @ResponseWrapper(localName = "setCachedMeasurementExtsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementExtsResponse")
    void setCachedMeasurementExts(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurementExt> p0);

    /**
     * Stores or updates a list of cached hospital/program admissions.
     *
     * @param p0 List&lt;CachedAdmission&gt; the list of admission records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedAdmissions", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAdmissions")
    @ResponseWrapper(localName = "setCachedAdmissionsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAdmissionsResponse")
    void setCachedAdmissions(@WebParam(name = "arg0", targetNamespace = "") final List<CachedAdmission> p0);
    
    /**
     * Retrieves a cached demographic document by composite primary key.
     *
     * @param p0 FacilityIdIntegerCompositePk the composite primary key containing facility ID and document ID
     * @return CachedDemographicDocument the cached document metadata, or null if not found
     */
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicDocument", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocument")
    @ResponseWrapper(localName = "getCachedDemographicDocumentResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocumentResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicDocument getCachedDemographicDocument(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdIntegerCompositePk p0);

    /**
     * Retrieves a cached prevention record by prevention ID.
     *
     * @param p0 FacilityIdIntegerCompositePk the composite primary key containing facility ID and prevention ID
     * @return CachedDemographicPrevention the cached prevention record, or null if not found
     */
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicPreventionsByPreventionId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicPreventionsByPreventionId")
    @ResponseWrapper(localName = "getCachedDemographicPreventionsByPreventionIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicPreventionsByPreventionIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicPrevention getCachedDemographicPreventionsByPreventionId(@WebParam(name = "preventionId", targetNamespace = "") final FacilityIdIntegerCompositePk p0);

    /**
     * Adds a single cached HL7 lab result record.
     *
     * @param p0 CachedDemographicHL7LabResult the HL7 lab result to cache
     */
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicHL7LabResult", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicHL7LabResult")
    @ResponseWrapper(localName = "addCachedDemographicHL7LabResultResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicHL7LabResultResponse")
    void addCachedDemographicHL7LabResult(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicHL7LabResult p0);

    /**
     * Stores or updates a list of cached prevention records.
     *
     * @param p0 List&lt;CachedDemographicPrevention&gt; the list of prevention records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicPreventions", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicPreventions")
    @ResponseWrapper(localName = "setCachedDemographicPreventionsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicPreventionsResponse")
    void setCachedDemographicPreventions(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicPrevention> p0);

    /**
     * Stores or updates a list of cached Ontario billing items.
     *
     * @param p0 List&lt;CachedBillingOnItem&gt; the list of Ontario billing item records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedBillingOnItem", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedBillingOnItem")
    @ResponseWrapper(localName = "setCachedBillingOnItemResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedBillingOnItemResponse")
    void setCachedBillingOnItem(@WebParam(name = "arg0", targetNamespace = "") final List<CachedBillingOnItem> p0);

    /**
     * Creates a link between demographics at different facilities, establishing a cross-facility patient identity.
     *
     * @param p0 String the CAISI provider ID of the user creating the link
     * @param p1 Integer the demographic ID at the current facility
     * @param p2 Integer the facility ID on the integrator for the linked demographic
     * @param p3 Integer the demographic ID on the integrator
     */
    @WebMethod
    @RequestWrapper(localName = "linkDemographics", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.LinkDemographics")
    @ResponseWrapper(localName = "linkDemographicsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.LinkDemographicsResponse")
    void linkDemographics(@WebParam(name = "creatorCaisiProviderId", targetNamespace = "") final String p0, @WebParam(name = "caisiDemographicIdAtCurrentFacility", targetNamespace = "") final Integer p1, @WebParam(name = "integratorDemographicFacilityIdOnIntegrator", targetNamespace = "") final Integer p2, @WebParam(name = "caisiDemographicIdOnIntegrator", targetNamespace = "") final Integer p3);
    
    /**
     * Retrieves all cached clinical measurements for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve measurements for
     * @return List&lt;CachedMeasurement&gt; list of cached measurement records from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicMeasurementByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicMeasurementByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedDemographicMeasurementByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicMeasurementByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedMeasurement> getLinkedCachedDemographicMeasurementByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Retrieves all cached documents for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve documents for
     * @return List&lt;CachedDemographicDocument&gt; list of cached document metadata from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicDocuments", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDocuments")
    @ResponseWrapper(localName = "getLinkedCachedDemographicDocumentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicDocumentsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicDocument> getLinkedCachedDemographicDocuments(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Deletes specific cached prevention records for a demographic.
     *
     * @param p0 Integer the facility ID
     * @param p1 List&lt;Integer&gt; the list of prevention IDs to delete
     */
    @WebMethod
    @RequestWrapper(localName = "deleteCachedDemographicPreventions", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicPreventions")
    @ResponseWrapper(localName = "deleteCachedDemographicPreventionsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicPreventionsResponse")
    void deleteCachedDemographicPreventions(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final List<Integer> p1);

    /**
     * Stores or updates a list of cached diagnosis research records (dxresearch).
     *
     * @param p0 List&lt;CachedDxresearch&gt; the list of diagnosis research records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDxresearch", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDxresearch")
    @ResponseWrapper(localName = "setCachedDxresearchResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDxresearchResponse")
    void setCachedDxresearch(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDxresearch> p0);

    /**
     * Retrieves all cached hospital/program admissions for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve admissions for
     * @return List&lt;CachedAdmission&gt; list of cached admission records from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedAdmissionsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAdmissionsByDemographicId")
    @ResponseWrapper(localName = "getLinkedCachedAdmissionsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedAdmissionsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedAdmission> getLinkedCachedAdmissionsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);
    
    /**
     * Retrieves a cached demographic form by composite primary key.
     *
     * @param p0 FacilityIdIntegerCompositePk the composite primary key containing facility ID and form ID
     * @return CachedDemographicForm the cached form data, or null if not found
     */
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicForm", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicForm")
    @ResponseWrapper(localName = "getCachedDemographicFormResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicFormResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicForm getCachedDemographicForm(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdIntegerCompositePk p0);

    /**
     * Retrieves all cached lab results for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID to retrieve lab results for
     * @return List&lt;CachedDemographicLabResult&gt; list of cached lab results from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicLabResults", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicLabResults")
    @ResponseWrapper(localName = "getLinkedCachedDemographicLabResultsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicLabResultsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicLabResult> getLinkedCachedDemographicLabResults(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Stores or updates a list of cached appointments.
     *
     * @param p0 List&lt;CachedAppointment&gt; the list of appointment records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedAppointments", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAppointments")
    @ResponseWrapper(localName = "setCachedAppointmentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedAppointmentsResponse")
    void setCachedAppointments(@WebParam(name = "arg0", targetNamespace = "") final List<CachedAppointment> p0);

    /**
     * Stores or updates a list of cached electronic form values.
     *
     * @param p0 List&lt;CachedEformValue&gt; the list of eform value records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedEformValues", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformValues")
    @ResponseWrapper(localName = "setCachedEformValuesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformValuesResponse")
    void setCachedEformValues(@WebParam(name = "arg0", targetNamespace = "") final List<CachedEformValue> p0);

    /**
     * Stores or updates a list of cached demographic clinical notes.
     *
     * @param p0 List&lt;CachedDemographicNote&gt; the list of clinical note records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicNotes", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicNotes")
    @ResponseWrapper(localName = "setCachedDemographicNotesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicNotesResponse")
    void setCachedDemographicNotes(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicNote> p0);
    
    /**
     * Retrieves specific cached clinical notes by their composite primary keys.
     *
     * @param p0 List&lt;CachedDemographicNoteCompositePk&gt; the list of composite primary keys identifying the notes
     * @return List&lt;CachedDemographicNote&gt; list of matching cached notes
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicNotesByIds", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotesByIds")
    @ResponseWrapper(localName = "getLinkedCachedDemographicNotesByIdsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNotesByIdsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicNote> getLinkedCachedDemographicNotesByIds(@WebParam(name = "cachedDemographicNoteCompositePk", targetNamespace = "") final List<CachedDemographicNoteCompositePk> p0);

    /**
     * Stores or updates a list of cached demographic drug/medication records.
     *
     * @param p0 List&lt;CachedDemographicDrug&gt; the list of drug records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicDrugs", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicDrugs")
    @ResponseWrapper(localName = "setCachedDemographicDrugsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicDrugsResponse")
    void setCachedDemographicDrugs(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicDrug> p0);

    /**
     * Retrieves the consent state for a demographic at a specific facility.
     *
     * @param p0 FacilityIdIntegerCompositePk the composite primary key containing integrator facility ID and demographic ID
     * @return GetConsentTransfer the consent state information
     */
    @WebMethod
    @RequestWrapper(localName = "getConsentState", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetConsentState")
    @ResponseWrapper(localName = "getConsentStateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetConsentStateResponse")
    @WebResult(name = "return", targetNamespace = "")
    GetConsentTransfer getConsentState(@WebParam(name = "integratorFacilityAndDemographicId", targetNamespace = "") final FacilityIdIntegerCompositePk p0);

    /**
     * Retrieves the binary content of a cached demographic document.
     *
     * @param p0 FacilityIdIntegerCompositePk the composite primary key containing facility ID and document ID
     * @return CachedDemographicDocumentContents the document content with binary data
     */
    @WebMethod
    @RequestWrapper(localName = "getCachedDemographicDocumentContents", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocumentContents")
    @ResponseWrapper(localName = "getCachedDemographicDocumentContentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetCachedDemographicDocumentContentsResponse")
    @WebResult(name = "return", targetNamespace = "")
    CachedDemographicDocumentContents getCachedDemographicDocumentContents(@WebParam(name = "arg0", targetNamespace = "") final FacilityIdIntegerCompositePk p0);

    /**
     * Stores or updates a demographic consent record.
     *
     * @param p0 SetConsentTransfer the consent data to store
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicConsent", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicConsent")
    @ResponseWrapper(localName = "setCachedDemographicConsentResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicConsentResponse")
    void setCachedDemographicConsent(@WebParam(name = "consentTransfer", targetNamespace = "") final SetConsentTransfer p0);

    /**
     * Stores or updates a list of cached measurement type definitions.
     *
     * @param p0 List&lt;CachedMeasurementType&gt; the list of measurement type records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedMeasurementTypes", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementTypes")
    @ResponseWrapper(localName = "setCachedMeasurementTypesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedMeasurementTypesResponse")
    void setCachedMeasurementTypes(@WebParam(name = "arg0", targetNamespace = "") final List<CachedMeasurementType> p0);
    
    /**
     * Deletes specific cached demographic clinical issues.
     *
     * @param p0 Integer the facility ID
     * @param p1 List&lt;FacilityIdDemographicIssueCompositePk&gt; the list of composite keys identifying issues to delete
     */
    @WebMethod
    @RequestWrapper(localName = "deleteCachedDemographicIssues", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicIssues")
    @ResponseWrapper(localName = "deleteCachedDemographicIssuesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.DeleteCachedDemographicIssuesResponse")
    void deleteCachedDemographicIssues(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final List<FacilityIdDemographicIssueCompositePk> p1);

    /**
     * Retrieves metadata for cached clinical notes without full content (lightweight query).
     *
     * @param p0 Integer the CAISI demographic ID to retrieve note metadata for
     * @return List&lt;CachedDemographicNote&gt; list of cached note metadata from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicNoteMetaData", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNoteMetaData")
    @ResponseWrapper(localName = "getLinkedCachedDemographicNoteMetaDataResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicNoteMetaDataResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicNote> getLinkedCachedDemographicNoteMetaData(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Retrieves all demographics linked to the specified demographic (includes multi-hop relationships).
     *
     * @param p0 Integer the CAISI demographic ID to retrieve linked demographics for
     * @return List&lt;DemographicTransfer&gt; list of all linked demographic records across all facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedDemographicsByDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedDemographicsByDemographicId")
    @ResponseWrapper(localName = "getLinkedDemographicsByDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedDemographicsByDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<DemographicTransfer> getLinkedDemographicsByDemographicId(@WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p0);

    /**
     * Records the timestamp of the last data push for a facility.
     *
     * @param p0 int the facility ID
     */
    @WebMethod
    @RequestWrapper(localName = "setLastPushDate", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetLastPushDate")
    @ResponseWrapper(localName = "setLastPushDateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetLastPushDateResponse")
    void setLastPushDate(@WebParam(name = "arg0", targetNamespace = "") final int p0);

    /**
     * Removes a link between demographics at different facilities.
     *
     * @param p0 Integer the demographic ID at the current facility
     * @param p1 Integer the integrator facility ID on the integrator
     * @param p2 Integer the demographic ID on the integrator
     */
    @WebMethod
    @RequestWrapper(localName = "unLinkDemographics", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.UnLinkDemographics")
    @ResponseWrapper(localName = "unLinkDemographicsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.UnLinkDemographicsResponse")
    void unLinkDemographics(@WebParam(name = "caisiDemographicIdAtCurrentFacility", targetNamespace = "") final Integer p0, @WebParam(name = "integratorDemographicFacilityIdOnIntegrator", targetNamespace = "") final Integer p1, @WebParam(name = "caisiDemographicIdOnIntegrator", targetNamespace = "") final Integer p2);
    
    /**
     * Retrieves demographics that have been pushed to the integrator after a specified date.
     *
     * @param p0 Calendar the cutoff date - returns demographics pushed after this date
     * @return List&lt;DemographicPushDate&gt; list of demographics with their push timestamps
     */
    @WebMethod
    @RequestWrapper(localName = "getDemographicsPushedAfterDate", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicsPushedAfterDate")
    @ResponseWrapper(localName = "getDemographicsPushedAfterDateResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicsPushedAfterDateResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<DemographicPushDate> getDemographicsPushedAfterDate(@WebParam(name = "arg0", targetNamespace = "") final Calendar p0);

    /**
     * Retrieves a specific demographic record by facility ID and demographic ID.
     *
     * @param p0 Integer the integrator facility ID
     * @param p1 Integer the CAISI demographic ID
     * @return DemographicTransfer the demographic record, or null if not found
     */
    @WebMethod
    @RequestWrapper(localName = "getDemographicByFacilityIdAndDemographicId", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicByFacilityIdAndDemographicId")
    @ResponseWrapper(localName = "getDemographicByFacilityIdAndDemographicIdResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicByFacilityIdAndDemographicIdResponse")
    @WebResult(name = "return", targetNamespace = "")
    DemographicTransfer getDemographicByFacilityIdAndDemographicId(@WebParam(name = "integratorFacilityId", targetNamespace = "") final Integer p0, @WebParam(name = "caisiDemographicId", targetNamespace = "") final Integer p1);

    /**
     * Adds a single cached demographic form record.
     *
     * @param p0 CachedDemographicForm the form data to cache
     */
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicForm", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicForm")
    @ResponseWrapper(localName = "addCachedDemographicFormResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicFormResponse")
    void addCachedDemographicForm(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicForm p0);

    /**
     * Stores or updates a list of cached demographic clinical issues.
     *
     * @param p0 List&lt;CachedDemographicIssue&gt; the list of clinical issue records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedDemographicIssues", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicIssues")
    @ResponseWrapper(localName = "setCachedDemographicIssuesResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedDemographicIssuesResponse")
    void setCachedDemographicIssues(@WebParam(name = "arg0", targetNamespace = "") final List<CachedDemographicIssue> p0);

    /**
     * Searches for demographics matching specified criteria with match scores.
     *
     * @param p0 MatchingDemographicParameters the search parameters (name, DOB, gender, etc.)
     * @return List&lt;MatchingDemographicTransferScore&gt; list of matching demographics with confidence scores
     */
    @WebMethod
    @RequestWrapper(localName = "getMatchingDemographics", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMatchingDemographics")
    @ResponseWrapper(localName = "getMatchingDemographicsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetMatchingDemographicsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<MatchingDemographicTransferScore> getMatchingDemographics(@WebParam(name = "arg0", targetNamespace = "") final MatchingDemographicParameters p0);
    
    /**
     * Adds a single cached demographic document record (metadata only).
     *
     * @param p0 CachedDemographicDocument the document metadata to cache
     */
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicDocument", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocument")
    @ResponseWrapper(localName = "addCachedDemographicDocumentResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentResponse")
    void addCachedDemographicDocument(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicDocument p0);

    /**
     * Stores or updates a list of cached electronic form data records.
     *
     * @param p0 List&lt;CachedEformData&gt; the list of eform data records to cache
     */
    @WebMethod
    @RequestWrapper(localName = "setCachedEformData", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformData")
    @ResponseWrapper(localName = "setCachedEformDataResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.SetCachedEformDataResponse")
    void setCachedEformData(@WebParam(name = "arg0", targetNamespace = "") final List<CachedEformData> p0);

    /**
     * Adds a single cached lab result record.
     *
     * @param p0 CachedDemographicLabResult the lab result to cache
     */
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicLabResult", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicLabResult")
    @ResponseWrapper(localName = "addCachedDemographicLabResultResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicLabResultResponse")
    void addCachedDemographicLabResult(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicLabResult p0);

    /**
     * Retrieves demographic IDs pushed after a specified date, filtered by requesting facility.
     *
     * @param p0 Calendar the cutoff date - returns demographic IDs pushed after this date
     * @return List&lt;Integer&gt; list of demographic IDs accessible to the requesting facility
     */
    @WebMethod
    @RequestWrapper(localName = "getDemographicIdPushedAfterDateByRequestingFacility", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicIdPushedAfterDateByRequestingFacility")
    @ResponseWrapper(localName = "getDemographicIdPushedAfterDateByRequestingFacilityResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetDemographicIdPushedAfterDateByRequestingFacilityResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<Integer> getDemographicIdPushedAfterDateByRequestingFacility(@WebParam(name = "arg0", targetNamespace = "") final Calendar p0);

    /**
     * Retrieves cached forms of a specific type for linked demographics.
     *
     * @param p0 Integer the CAISI demographic ID
     * @param p1 String the form name/type to filter by
     * @return List&lt;CachedDemographicForm&gt; list of matching cached forms from linked facilities
     */
    @WebMethod
    @RequestWrapper(localName = "getLinkedCachedDemographicForms", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicForms")
    @ResponseWrapper(localName = "getLinkedCachedDemographicFormsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.GetLinkedCachedDemographicFormsResponse")
    @WebResult(name = "return", targetNamespace = "")
    List<CachedDemographicForm> getLinkedCachedDemographicForms(@WebParam(name = "arg0", targetNamespace = "") final Integer p0, @WebParam(name = "arg1", targetNamespace = "") final String p1);

    /**
     * Adds a cached demographic document with its binary content in a single operation.
     *
     * @param p0 CachedDemographicDocument the document metadata
     * @param p1 byte[] the binary document content
     */
    @WebMethod
    @RequestWrapper(localName = "addCachedDemographicDocumentAndContents", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentAndContents")
    @ResponseWrapper(localName = "addCachedDemographicDocumentAndContentsResponse", targetNamespace = "http://ws.caisi_integrator.oscarehr.org/", className = "ca.openosp.openo.caisi_integrator.webserv.AddCachedDemographicDocumentAndContentsResponse")
    void addCachedDemographicDocumentAndContents(@WebParam(name = "arg0", targetNamespace = "") final CachedDemographicDocument p0, @WebParam(name = "arg1", targetNamespace = "") final byte[] p1);
}
