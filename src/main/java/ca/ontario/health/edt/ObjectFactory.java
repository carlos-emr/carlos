package ca.ontario.health.edt;

import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlRegistry;

/**
 * JAXB ObjectFactory for Ontario Health EDT (Electronic Data Transfer) web service client.
 *
 * <p>This factory class provides factory methods for creating instances of EDT schema-derived classes
 * and JAXBElement-wrapped objects for XML marshalling and unmarshalling operations. The EDT service
 * facilitates electronic data transfer for Ontario healthcare providers, supporting operations such as
 * document upload, download, submission, listing, updating, and deletion.</p>
 *
 * <p>The factory creates both standalone object instances (via create* methods) and JAXBElement-wrapped
 * instances for XML element declarations defined in the Ontario Health EDT namespace
 * (http://edt.health.ontario.ca/).</p>
 *
 * <p><strong>Healthcare Context:</strong> This class supports integration with Ontario's Electronic Data
 * Transfer system, which is used for submitting and managing healthcare-related documents and data
 * between EMR systems and Ontario Health services.</p>
 *
 * @see javax.xml.bind.annotation.XmlRegistry
 * @see JAXBElement
 * @since 2026-01-24
 */
@XmlRegistry
public class ObjectFactory
{
    private static final QName _Info_QNAME;
    private static final QName _Upload_QNAME;
    private static final QName _DownloadResponse_QNAME;
    private static final QName _Download_QNAME;
    private static final QName _List_QNAME;
    private static final QName _Submit_QNAME;
    private static final QName _Update_QNAME;
    private static final QName _InfoResponse_QNAME;
    private static final QName _GetTypeList_QNAME;
    private static final QName _UploadResponse_QNAME;
    private static final QName _SubmitResponse_QNAME;
    private static final QName _GetTypeListResponse_QNAME;
    private static final QName _ListResponse_QNAME;
    private static final QName _Delete_QNAME;
    private static final QName _UpdateResponse_QNAME;
    private static final QName _DeleteResponse_QNAME;

    /**
     * Creates a new UploadResponse instance.
     *
     * @return UploadResponse a new instance for handling upload operation responses
     */
    public UploadResponse createUploadResponse() {
        return new UploadResponse();
    }

    /**
     * Creates a new Upload instance.
     *
     * @return Upload a new instance for upload request operations
     */
    public Upload createUpload() {
        return new Upload();
    }

    /**
     * Creates a new List instance.
     *
     * @return List a new instance for list request operations
     */
    public List createList() {
        return new List();
    }

    /**
     * Creates a new ResponseResult instance.
     *
     * @return ResponseResult a new instance containing response result data
     */
    public ResponseResult createResponseResult() {
        return new ResponseResult();
    }

    /**
     * Creates a new SubmitResponse instance.
     *
     * @return SubmitResponse a new instance for handling submit operation responses
     */
    public SubmitResponse createSubmitResponse() {
        return new SubmitResponse();
    }

    /**
     * Creates a new GetTypeList instance.
     *
     * @return GetTypeList a new instance for retrieving available document types
     */
    public GetTypeList createGetTypeList() {
        return new GetTypeList();
    }

    /**
     * Creates a new ResourceResult instance.
     *
     * @return ResourceResult a new instance containing resource result data
     */
    public ResourceResult createResourceResult() {
        return new ResourceResult();
    }

    /**
     * Creates a new Detail instance.
     *
     * @return Detail a new instance for detail request operations
     */
    public Detail createDetail() {
        return new Detail();
    }

    /**
     * Creates a new UploadData instance.
     *
     * @return UploadData a new instance containing upload data payload
     */
    public UploadData createUploadData() {
        return new UploadData();
    }

    /**
     * Creates a new Delete instance.
     *
     * @return Delete a new instance for delete request operations
     */
    public Delete createDelete() {
        return new Delete();
    }

    /**
     * Creates a new GetTypeListResponse instance.
     *
     * @return GetTypeListResponse a new instance for handling type list responses
     */
    public GetTypeListResponse createGetTypeListResponse() {
        return new GetTypeListResponse();
    }

    /**
     * Creates a new Download instance.
     *
     * @return Download a new instance for download request operations
     */
    public Download createDownload() {
        return new Download();
    }

    /**
     * Creates a new CsnData instance.
     *
     * @return CsnData a new instance containing CSN (Case Submission Number) data
     */
    public CsnData createCsnData() {
        return new CsnData();
    }

    /**
     * Creates a new DownloadResponse instance.
     *
     * @return DownloadResponse a new instance for handling download operation responses
     */
    public DownloadResponse createDownloadResponse() {
        return new DownloadResponse();
    }

    /**
     * Creates a new Info instance.
     *
     * @return Info a new instance for info request operations
     */
    public Info createInfo() {
        return new Info();
    }

    /**
     * Creates a new InfoResponse instance.
     *
     * @return InfoResponse a new instance for handling info operation responses
     */
    public InfoResponse createInfoResponse() {
        return new InfoResponse();
    }

    /**
     * Creates a new DetailData instance.
     *
     * @return DetailData a new instance containing detail data payload
     */
    public DetailData createDetailData() {
        return new DetailData();
    }

    /**
     * Creates a new Update instance.
     *
     * @return Update a new instance for update request operations
     */
    public Update createUpdate() {
        return new Update();
    }

    /**
     * Creates a new CommonResult instance.
     *
     * @return CommonResult a new instance containing common result data
     */
    public CommonResult createCommonResult() {
        return new CommonResult();
    }

    /**
     * Creates a new DownloadResult instance.
     *
     * @return DownloadResult a new instance containing download result data
     */
    public DownloadResult createDownloadResult() {
        return new DownloadResult();
    }

    /**
     * Creates a new TypeListData instance.
     *
     * @return TypeListData a new instance containing type list data payload
     */
    public TypeListData createTypeListData() {
        return new TypeListData();
    }

    /**
     * Creates a new UpdateRequest instance.
     *
     * @return UpdateRequest a new instance for update request operations
     */
    public UpdateRequest createUpdateRequest() {
        return new UpdateRequest();
    }

    /**
     * Creates a new TypeListResult instance.
     *
     * @return TypeListResult a new instance containing type list result data
     */
    public TypeListResult createTypeListResult() {
        return new TypeListResult();
    }

    /**
     * Creates a new UpdateResponse instance.
     *
     * @return UpdateResponse a new instance for handling update operation responses
     */
    public UpdateResponse createUpdateResponse() {
        return new UpdateResponse();
    }

    /**
     * Creates a new ListResponse instance.
     *
     * @return ListResponse a new instance for handling list operation responses
     */
    public ListResponse createListResponse() {
        return new ListResponse();
    }

    /**
     * Creates a new DownloadData instance.
     *
     * @return DownloadData a new instance containing download data payload
     */
    public DownloadData createDownloadData() {
        return new DownloadData();
    }

    /**
     * Creates a new Submit instance.
     *
     * @return Submit a new instance for submit request operations
     */
    public Submit createSubmit() {
        return new Submit();
    }

    /**
     * Creates a new DeleteResponse instance.
     *
     * @return DeleteResponse a new instance for handling delete operation responses
     */
    public DeleteResponse createDeleteResponse() {
        return new DeleteResponse();
    }

    /**
     * Creates a JAXBElement wrapper for an Info instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "info" in the EDT namespace,
     * wrapping the provided Info object for XML marshalling operations.</p>
     *
     * @param value Info the Info instance to wrap
     * @return JAXBElement&lt;Info&gt; a JAXBElement wrapping the Info instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "info")
    public JAXBElement<Info> createInfo(final Info value) {
        return (JAXBElement<Info>)new JAXBElement(ObjectFactory._Info_QNAME, (Class)Info.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for an Upload instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "upload" in the EDT namespace,
     * wrapping the provided Upload object for XML marshalling operations.</p>
     *
     * @param value Upload the Upload instance to wrap
     * @return JAXBElement&lt;Upload&gt; a JAXBElement wrapping the Upload instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "upload")
    public JAXBElement<Upload> createUpload(final Upload value) {
        return (JAXBElement<Upload>)new JAXBElement(ObjectFactory._Upload_QNAME, (Class)Upload.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a DownloadResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "downloadResponse" in the EDT namespace,
     * wrapping the provided DownloadResponse object for XML marshalling operations.</p>
     *
     * @param value DownloadResponse the DownloadResponse instance to wrap
     * @return JAXBElement&lt;DownloadResponse&gt; a JAXBElement wrapping the DownloadResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "downloadResponse")
    public JAXBElement<DownloadResponse> createDownloadResponse(final DownloadResponse value) {
        return (JAXBElement<DownloadResponse>)new JAXBElement(ObjectFactory._DownloadResponse_QNAME, (Class)DownloadResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a Download instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "download" in the EDT namespace,
     * wrapping the provided Download object for XML marshalling operations.</p>
     *
     * @param value Download the Download instance to wrap
     * @return JAXBElement&lt;Download&gt; a JAXBElement wrapping the Download instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "download")
    public JAXBElement<Download> createDownload(final Download value) {
        return (JAXBElement<Download>)new JAXBElement(ObjectFactory._Download_QNAME, (Class)Download.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a List instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "list" in the EDT namespace,
     * wrapping the provided List object for XML marshalling operations.</p>
     *
     * @param value List the List instance to wrap
     * @return JAXBElement&lt;List&gt; a JAXBElement wrapping the List instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "list")
    public JAXBElement<List> createList(final List value) {
        return (JAXBElement<List>)new JAXBElement(ObjectFactory._List_QNAME, (Class)List.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a Submit instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "submit" in the EDT namespace,
     * wrapping the provided Submit object for XML marshalling operations.</p>
     *
     * @param value Submit the Submit instance to wrap
     * @return JAXBElement&lt;Submit&gt; a JAXBElement wrapping the Submit instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "submit")
    public JAXBElement<Submit> createSubmit(final Submit value) {
        return (JAXBElement<Submit>)new JAXBElement(ObjectFactory._Submit_QNAME, (Class)Submit.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for an Update instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "update" in the EDT namespace,
     * wrapping the provided Update object for XML marshalling operations.</p>
     *
     * @param value Update the Update instance to wrap
     * @return JAXBElement&lt;Update&gt; a JAXBElement wrapping the Update instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "update")
    public JAXBElement<Update> createUpdate(final Update value) {
        return (JAXBElement<Update>)new JAXBElement(ObjectFactory._Update_QNAME, (Class)Update.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for an InfoResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "infoResponse" in the EDT namespace,
     * wrapping the provided InfoResponse object for XML marshalling operations.</p>
     *
     * @param value InfoResponse the InfoResponse instance to wrap
     * @return JAXBElement&lt;InfoResponse&gt; a JAXBElement wrapping the InfoResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "infoResponse")
    public JAXBElement<InfoResponse> createInfoResponse(final InfoResponse value) {
        return (JAXBElement<InfoResponse>)new JAXBElement(ObjectFactory._InfoResponse_QNAME, (Class)InfoResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a GetTypeList instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "getTypeList" in the EDT namespace,
     * wrapping the provided GetTypeList object for XML marshalling operations.</p>
     *
     * @param value GetTypeList the GetTypeList instance to wrap
     * @return JAXBElement&lt;GetTypeList&gt; a JAXBElement wrapping the GetTypeList instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "getTypeList")
    public JAXBElement<GetTypeList> createGetTypeList(final GetTypeList value) {
        return (JAXBElement<GetTypeList>)new JAXBElement(ObjectFactory._GetTypeList_QNAME, (Class)GetTypeList.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for an UploadResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "uploadResponse" in the EDT namespace,
     * wrapping the provided UploadResponse object for XML marshalling operations.</p>
     *
     * @param value UploadResponse the UploadResponse instance to wrap
     * @return JAXBElement&lt;UploadResponse&gt; a JAXBElement wrapping the UploadResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "uploadResponse")
    public JAXBElement<UploadResponse> createUploadResponse(final UploadResponse value) {
        return (JAXBElement<UploadResponse>)new JAXBElement(ObjectFactory._UploadResponse_QNAME, (Class)UploadResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a SubmitResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "submitResponse" in the EDT namespace,
     * wrapping the provided SubmitResponse object for XML marshalling operations.</p>
     *
     * @param value SubmitResponse the SubmitResponse instance to wrap
     * @return JAXBElement&lt;SubmitResponse&gt; a JAXBElement wrapping the SubmitResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "submitResponse")
    public JAXBElement<SubmitResponse> createSubmitResponse(final SubmitResponse value) {
        return (JAXBElement<SubmitResponse>)new JAXBElement(ObjectFactory._SubmitResponse_QNAME, (Class)SubmitResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a GetTypeListResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "getTypeListResponse" in the EDT namespace,
     * wrapping the provided GetTypeListResponse object for XML marshalling operations.</p>
     *
     * @param value GetTypeListResponse the GetTypeListResponse instance to wrap
     * @return JAXBElement&lt;GetTypeListResponse&gt; a JAXBElement wrapping the GetTypeListResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "getTypeListResponse")
    public JAXBElement<GetTypeListResponse> createGetTypeListResponse(final GetTypeListResponse value) {
        return (JAXBElement<GetTypeListResponse>)new JAXBElement(ObjectFactory._GetTypeListResponse_QNAME, (Class)GetTypeListResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a ListResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "listResponse" in the EDT namespace,
     * wrapping the provided ListResponse object for XML marshalling operations.</p>
     *
     * @param value ListResponse the ListResponse instance to wrap
     * @return JAXBElement&lt;ListResponse&gt; a JAXBElement wrapping the ListResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "listResponse")
    public JAXBElement<ListResponse> createListResponse(final ListResponse value) {
        return (JAXBElement<ListResponse>)new JAXBElement(ObjectFactory._ListResponse_QNAME, (Class)ListResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a Delete instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "delete" in the EDT namespace,
     * wrapping the provided Delete object for XML marshalling operations.</p>
     *
     * @param value Delete the Delete instance to wrap
     * @return JAXBElement&lt;Delete&gt; a JAXBElement wrapping the Delete instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "delete")
    public JAXBElement<Delete> createDelete(final Delete value) {
        return (JAXBElement<Delete>)new JAXBElement(ObjectFactory._Delete_QNAME, (Class)Delete.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for an UpdateResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "updateResponse" in the EDT namespace,
     * wrapping the provided UpdateResponse object for XML marshalling operations.</p>
     *
     * @param value UpdateResponse the UpdateResponse instance to wrap
     * @return JAXBElement&lt;UpdateResponse&gt; a JAXBElement wrapping the UpdateResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "updateResponse")
    public JAXBElement<UpdateResponse> createUpdateResponse(final UpdateResponse value) {
        return (JAXBElement<UpdateResponse>)new JAXBElement(ObjectFactory._UpdateResponse_QNAME, (Class)UpdateResponse.class, (Class)null, (Object)value);
    }

    /**
     * Creates a JAXBElement wrapper for a DeleteResponse instance.
     *
     * <p>This method creates a JAXBElement with the XML element name "deleteResponse" in the EDT namespace,
     * wrapping the provided DeleteResponse object for XML marshalling operations.</p>
     *
     * @param value DeleteResponse the DeleteResponse instance to wrap
     * @return JAXBElement&lt;DeleteResponse&gt; a JAXBElement wrapping the DeleteResponse instance
     */
    @XmlElementDecl(namespace = "http://edt.health.ontario.ca/", name = "deleteResponse")
    public JAXBElement<DeleteResponse> createDeleteResponse(final DeleteResponse value) {
        return (JAXBElement<DeleteResponse>)new JAXBElement(ObjectFactory._DeleteResponse_QNAME, (Class)DeleteResponse.class, (Class)null, (Object)value);
    }
    
    static {
        _Info_QNAME = new QName("http://edt.health.ontario.ca/", "info");
        _Upload_QNAME = new QName("http://edt.health.ontario.ca/", "upload");
        _DownloadResponse_QNAME = new QName("http://edt.health.ontario.ca/", "downloadResponse");
        _Download_QNAME = new QName("http://edt.health.ontario.ca/", "download");
        _List_QNAME = new QName("http://edt.health.ontario.ca/", "list");
        _Submit_QNAME = new QName("http://edt.health.ontario.ca/", "submit");
        _Update_QNAME = new QName("http://edt.health.ontario.ca/", "update");
        _InfoResponse_QNAME = new QName("http://edt.health.ontario.ca/", "infoResponse");
        _GetTypeList_QNAME = new QName("http://edt.health.ontario.ca/", "getTypeList");
        _UploadResponse_QNAME = new QName("http://edt.health.ontario.ca/", "uploadResponse");
        _SubmitResponse_QNAME = new QName("http://edt.health.ontario.ca/", "submitResponse");
        _GetTypeListResponse_QNAME = new QName("http://edt.health.ontario.ca/", "getTypeListResponse");
        _ListResponse_QNAME = new QName("http://edt.health.ontario.ca/", "listResponse");
        _Delete_QNAME = new QName("http://edt.health.ontario.ca/", "delete");
        _UpdateResponse_QNAME = new QName("http://edt.health.ontario.ca/", "updateResponse");
        _DeleteResponse_QNAME = new QName("http://edt.health.ontario.ca/", "deleteResponse");
    }
}
