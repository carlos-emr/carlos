package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.ObjectId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedDemographicDocument extends AbstractModel<FacilityIdIntegerCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIntegerPk;
    @Column(nullable = false)
    @Index
    private int caisiDemographicId;
    private String docType;
    private String docXml;
    private String docFilename;
    private String docCreator;
    private String responsible;
    private String source;
    private Integer programId;
    private Date updateDateTime;
    private String status;
    private String contentType;
    private int public1;
    private Date observationDate;
    private String reviewer;
    private Date reviewDateTime;
    private int numberOfPages;
    private int appointmentNo;
    private String description;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedDemographicDocument() {
        this.caisiDemographicId = 0;
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIntegerPk(this);
    }
    
    public FacilityIdIntegerCompositePk getFacilityIntegerPk() {
        return pcGetfacilityIntegerPk(this);
    }
    
    public void setFacilityIntegerPk(final FacilityIdIntegerCompositePk facilityIntegerPk) {
        pcSetfacilityIntegerPk(this, facilityIntegerPk);
    }
    
    public int getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    public void setCaisiDemographicId(final int caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    public String getDocType() {
        return pcGetdocType(this);
    }
    
    public void setDocType(final String docType) {
        pcSetdocType(this, StringUtils.trimToNull(docType));
    }
    
    public String getDocXml() {
        return pcGetdocXml(this);
    }
    
    public void setDocXml(final String docXml) {
        pcSetdocXml(this, StringUtils.trimToNull(docXml));
    }
    
    public String getDescription() {
        return pcGetdescription(this);
    }
    
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }
    
    public String getDocFilename() {
        return pcGetdocFilename(this);
    }
    
    public void setDocFilename(final String docFilename) {
        pcSetdocFilename(this, StringUtils.trimToNull(docFilename));
    }
    
    public String getDocCreator() {
        return pcGetdocCreator(this);
    }
    
    public void setDocCreator(final String docCreator) {
        pcSetdocCreator(this, StringUtils.trimToNull(docCreator));
    }
    
    public String getResponsible() {
        return pcGetresponsible(this);
    }
    
    public void setResponsible(final String responsible) {
        pcSetresponsible(this, StringUtils.trimToNull(responsible));
    }
    
    public String getSource() {
        return pcGetsource(this);
    }
    
    public void setSource(final String source) {
        pcSetsource(this, StringUtils.trimToNull(source));
    }
    
    public Integer getProgramId() {
        return pcGetprogramId(this);
    }
    
    public void setProgramId(final Integer programId) {
        pcSetprogramId(this, programId);
    }
    
    public Date getUpdateDateTime() {
        return pcGetupdateDateTime(this);
    }
    
    public void setUpdateDateTime(final Date updateDateTime) {
        pcSetupdateDateTime(this, updateDateTime);
    }
    
    public String getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }
    
    public String getContentType() {
        return pcGetcontentType(this);
    }
    
    public void setContentType(final String contentType) {
        pcSetcontentType(this, StringUtils.trimToNull(contentType));
    }
    
    public int getPublic1() {
        return pcGetpublic1(this);
    }
    
    public void setPublic1(final int public1) {
        pcSetpublic1(this, public1);
    }
    
    public Date getObservationDate() {
        return pcGetobservationDate(this);
    }
    
    public void setObservationDate(final Date observationDate) {
        pcSetobservationDate(this, observationDate);
    }
    
    public String getReviewer() {
        return pcGetreviewer(this);
    }
    
    public void setReviewer(final String reviewer) {
        pcSetreviewer(this, StringUtils.trimToNull(reviewer));
    }
    
    public Date getReviewDateTime() {
        return pcGetreviewDateTime(this);
    }
    
    public void setReviewDateTime(final Date reviewDateTime) {
        pcSetreviewDateTime(this, reviewDateTime);
    }
    
    public int getNumberOfPages() {
        return pcGetnumberOfPages(this);
    }
    
    public void setNumberOfPages(final int numberOfPages) {
        pcSetnumberOfPages(this, numberOfPages);
    }
    
    public int getAppointmentNo() {
        return pcGetappointmentNo(this);
    }
    
    public void setAppointmentNo(final int appointmentNo) {
        pcSetappointmentNo(this, appointmentNo);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 743919396538312571L;
        CachedDemographicDocument.pcFieldNames = new String[] { "appointmentNo", "caisiDemographicId", "contentType", "description", "docCreator", "docFilename", "docType", "docXml", "facilityIntegerPk", "numberOfPages", "observationDate", "programId", "public1", "responsible", "reviewDateTime", "reviewer", "source", "status", "updateDateTime" };
        CachedDemographicDocument.pcFieldTypes = new Class[] { Integer.TYPE, Integer.TYPE, (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), Integer.TYPE, (CachedDemographicDocument.class$Ljava$util$Date != null) ? CachedDemographicDocument.class$Ljava$util$Date : (CachedDemographicDocument.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDocument.class$Ljava$lang$Integer != null) ? CachedDemographicDocument.class$Ljava$lang$Integer : (CachedDemographicDocument.class$Ljava$lang$Integer = class$("java.lang.Integer")), Integer.TYPE, (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$util$Date != null) ? CachedDemographicDocument.class$Ljava$util$Date : (CachedDemographicDocument.class$Ljava$util$Date = class$("java.util.Date")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$lang$String != null) ? CachedDemographicDocument.class$Ljava$lang$String : (CachedDemographicDocument.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicDocument.class$Ljava$util$Date != null) ? CachedDemographicDocument.class$Ljava$util$Date : (CachedDemographicDocument.class$Ljava$util$Date = class$("java.util.Date")) };
        CachedDemographicDocument.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument != null) ? CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument : (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocument")), CachedDemographicDocument.pcFieldNames, CachedDemographicDocument.pcFieldTypes, CachedDemographicDocument.pcFieldFlags, CachedDemographicDocument.pcPCSuperclass, "CachedDemographicDocument", (PersistenceCapable)new CachedDemographicDocument());
    }
    
    static /* synthetic */ Class class$(final String className) {
        try {
            return Class.forName(className);
        }
        catch (final ClassNotFoundException ex) {
            throw new NoClassDefFoundError(ex.getMessage());
        }
    }
    
    protected void pcClearFields() {
        this.appointmentNo = 0;
        this.caisiDemographicId = 0;
        this.contentType = null;
        this.description = null;
        this.docCreator = null;
        this.docFilename = null;
        this.docType = null;
        this.docXml = null;
        this.facilityIntegerPk = null;
        this.numberOfPages = 0;
        this.observationDate = null;
        this.programId = null;
        this.public1 = 0;
        this.responsible = null;
        this.reviewDateTime = null;
        this.reviewer = null;
        this.source = null;
        this.status = null;
        this.updateDateTime = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicDocument cachedDemographicDocument = new CachedDemographicDocument();
        if (b) {
            cachedDemographicDocument.pcClearFields();
        }
        cachedDemographicDocument.pcStateManager = pcStateManager;
        cachedDemographicDocument.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicDocument;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicDocument cachedDemographicDocument = new CachedDemographicDocument();
        if (b) {
            cachedDemographicDocument.pcClearFields();
        }
        cachedDemographicDocument.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicDocument;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 19;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicDocument.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentNo = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.contentType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.docCreator = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.docFilename = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.docType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.docXml = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.facilityIntegerPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.numberOfPages = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.observationDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.programId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.public1 = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.responsible = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.reviewDateTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.reviewer = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.source = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.updateDateTime = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcReplaceFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcReplaceField(array[i]);
        }
    }
    
    public void pcProvideField(final int n) {
        final int n2 = n - CachedDemographicDocument.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.appointmentNo);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.caisiDemographicId);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.contentType);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docCreator);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docFilename);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docType);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.docXml);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIntegerPk);
                return;
            }
            case 9: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.numberOfPages);
                return;
            }
            case 10: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.observationDate);
                return;
            }
            case 11: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.programId);
                return;
            }
            case 12: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.public1);
                return;
            }
            case 13: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.responsible);
                return;
            }
            case 14: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.reviewDateTime);
                return;
            }
            case 15: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reviewer);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.source);
                return;
            }
            case 17: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 18: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.updateDateTime);
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcProvideFields(final int[] array) {
        for (int i = 0; i < array.length; ++i) {
            this.pcProvideField(array[i]);
        }
    }
    
    protected void pcCopyField(final CachedDemographicDocument cachedDemographicDocument, final int n) {
        final int n2 = n - CachedDemographicDocument.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.appointmentNo = cachedDemographicDocument.appointmentNo;
                return;
            }
            case 1: {
                this.caisiDemographicId = cachedDemographicDocument.caisiDemographicId;
                return;
            }
            case 2: {
                this.contentType = cachedDemographicDocument.contentType;
                return;
            }
            case 3: {
                this.description = cachedDemographicDocument.description;
                return;
            }
            case 4: {
                this.docCreator = cachedDemographicDocument.docCreator;
                return;
            }
            case 5: {
                this.docFilename = cachedDemographicDocument.docFilename;
                return;
            }
            case 6: {
                this.docType = cachedDemographicDocument.docType;
                return;
            }
            case 7: {
                this.docXml = cachedDemographicDocument.docXml;
                return;
            }
            case 8: {
                this.facilityIntegerPk = cachedDemographicDocument.facilityIntegerPk;
                return;
            }
            case 9: {
                this.numberOfPages = cachedDemographicDocument.numberOfPages;
                return;
            }
            case 10: {
                this.observationDate = cachedDemographicDocument.observationDate;
                return;
            }
            case 11: {
                this.programId = cachedDemographicDocument.programId;
                return;
            }
            case 12: {
                this.public1 = cachedDemographicDocument.public1;
                return;
            }
            case 13: {
                this.responsible = cachedDemographicDocument.responsible;
                return;
            }
            case 14: {
                this.reviewDateTime = cachedDemographicDocument.reviewDateTime;
                return;
            }
            case 15: {
                this.reviewer = cachedDemographicDocument.reviewer;
                return;
            }
            case 16: {
                this.source = cachedDemographicDocument.source;
                return;
            }
            case 17: {
                this.status = cachedDemographicDocument.status;
                return;
            }
            case 18: {
                this.updateDateTime = cachedDemographicDocument.updateDateTime;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicDocument cachedDemographicDocument = (CachedDemographicDocument)o;
        if (cachedDemographicDocument.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicDocument, array[i]);
        }
    }
    
    public Object pcGetGenericContext() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getGenericContext();
    }
    
    public Object pcFetchObjectId() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.fetchObjectId();
    }
    
    public boolean pcIsDeleted() {
        return this.pcStateManager != null && this.pcStateManager.isDeleted();
    }
    
    public boolean pcIsDirty() {
        if (this.pcStateManager == null) {
            return false;
        }
        final StateManager pcStateManager = this.pcStateManager;
        RedefinitionHelper.dirtyCheck(pcStateManager);
        return pcStateManager.isDirty();
    }
    
    public boolean pcIsNew() {
        return this.pcStateManager != null && this.pcStateManager.isNew();
    }
    
    public boolean pcIsPersistent() {
        return this.pcStateManager != null && this.pcStateManager.isPersistent();
    }
    
    public boolean pcIsTransactional() {
        return this.pcStateManager != null && this.pcStateManager.isTransactional();
    }
    
    public boolean pcSerializing() {
        return this.pcStateManager != null && this.pcStateManager.serializing();
    }
    
    public void pcDirty(final String s) {
        if (this.pcStateManager == null) {
            return;
        }
        this.pcStateManager.dirty(s);
    }
    
    public StateManager pcGetStateManager() {
        return this.pcStateManager;
    }
    
    public Object pcGetVersion() {
        if (this.pcStateManager == null) {
            return null;
        }
        return this.pcStateManager.getVersion();
    }
    
    public void pcReplaceStateManager(final StateManager pcStateManager) throws SecurityException {
        if (this.pcStateManager != null) {
            this.pcStateManager = this.pcStateManager.replaceStateManager(pcStateManager);
            return;
        }
        this.pcStateManager = pcStateManager;
    }
    
    public void pcCopyKeyFieldsToObjectId(final FieldSupplier fieldSupplier, final Object o) {
        throw new InternalException();
    }
    
    public void pcCopyKeyFieldsToObjectId(final Object o) {
        throw new InternalException();
    }
    
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
        fieldConsumer.storeObjectField(8 + CachedDemographicDocument.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIntegerPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocument\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument != null) ? CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument : (CachedDemographicDocument.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicDocument = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicDocument")), (Object)this.facilityIntegerPk);
    }
    
    private static final int pcGetappointmentNo(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.appointmentNo;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 0);
        return cachedDemographicDocument.appointmentNo;
    }
    
    private static final void pcSetappointmentNo(final CachedDemographicDocument cachedDemographicDocument, final int appointmentNo) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.appointmentNo = appointmentNo;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 0, cachedDemographicDocument.appointmentNo, appointmentNo, 0);
    }
    
    private static final int pcGetcaisiDemographicId(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.caisiDemographicId;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 1);
        return cachedDemographicDocument.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedDemographicDocument cachedDemographicDocument, final int caisiDemographicId) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 1, cachedDemographicDocument.caisiDemographicId, caisiDemographicId, 0);
    }
    
    private static final String pcGetcontentType(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.contentType;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 2);
        return cachedDemographicDocument.contentType;
    }
    
    private static final void pcSetcontentType(final CachedDemographicDocument cachedDemographicDocument, final String contentType) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.contentType = contentType;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 2, cachedDemographicDocument.contentType, contentType, 0);
    }
    
    private static final String pcGetdescription(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.description;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 3);
        return cachedDemographicDocument.description;
    }
    
    private static final void pcSetdescription(final CachedDemographicDocument cachedDemographicDocument, final String description) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.description = description;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 3, cachedDemographicDocument.description, description, 0);
    }
    
    private static final String pcGetdocCreator(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docCreator;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 4);
        return cachedDemographicDocument.docCreator;
    }
    
    private static final void pcSetdocCreator(final CachedDemographicDocument cachedDemographicDocument, final String docCreator) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docCreator = docCreator;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 4, cachedDemographicDocument.docCreator, docCreator, 0);
    }
    
    private static final String pcGetdocFilename(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docFilename;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 5);
        return cachedDemographicDocument.docFilename;
    }
    
    private static final void pcSetdocFilename(final CachedDemographicDocument cachedDemographicDocument, final String docFilename) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docFilename = docFilename;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 5, cachedDemographicDocument.docFilename, docFilename, 0);
    }
    
    private static final String pcGetdocType(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docType;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 6);
        return cachedDemographicDocument.docType;
    }
    
    private static final void pcSetdocType(final CachedDemographicDocument cachedDemographicDocument, final String docType) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docType = docType;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 6, cachedDemographicDocument.docType, docType, 0);
    }
    
    private static final String pcGetdocXml(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.docXml;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 7);
        return cachedDemographicDocument.docXml;
    }
    
    private static final void pcSetdocXml(final CachedDemographicDocument cachedDemographicDocument, final String docXml) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.docXml = docXml;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 7, cachedDemographicDocument.docXml, docXml, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIntegerPk(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.facilityIntegerPk;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 8);
        return cachedDemographicDocument.facilityIntegerPk;
    }
    
    private static final void pcSetfacilityIntegerPk(final CachedDemographicDocument cachedDemographicDocument, final FacilityIdIntegerCompositePk facilityIntegerPk) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.facilityIntegerPk = facilityIntegerPk;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 8, (Object)cachedDemographicDocument.facilityIntegerPk, (Object)facilityIntegerPk, 0);
    }
    
    private static final int pcGetnumberOfPages(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.numberOfPages;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 9);
        return cachedDemographicDocument.numberOfPages;
    }
    
    private static final void pcSetnumberOfPages(final CachedDemographicDocument cachedDemographicDocument, final int numberOfPages) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.numberOfPages = numberOfPages;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 9, cachedDemographicDocument.numberOfPages, numberOfPages, 0);
    }
    
    private static final Date pcGetobservationDate(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.observationDate;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 10);
        return cachedDemographicDocument.observationDate;
    }
    
    private static final void pcSetobservationDate(final CachedDemographicDocument cachedDemographicDocument, final Date observationDate) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.observationDate = observationDate;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 10, (Object)cachedDemographicDocument.observationDate, (Object)observationDate, 0);
    }
    
    private static final Integer pcGetprogramId(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.programId;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 11);
        return cachedDemographicDocument.programId;
    }
    
    private static final void pcSetprogramId(final CachedDemographicDocument cachedDemographicDocument, final Integer programId) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.programId = programId;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 11, (Object)cachedDemographicDocument.programId, (Object)programId, 0);
    }
    
    private static final int pcGetpublic1(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.public1;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 12);
        return cachedDemographicDocument.public1;
    }
    
    private static final void pcSetpublic1(final CachedDemographicDocument cachedDemographicDocument, final int public1) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.public1 = public1;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingIntField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 12, cachedDemographicDocument.public1, public1, 0);
    }
    
    private static final String pcGetresponsible(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.responsible;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 13);
        return cachedDemographicDocument.responsible;
    }
    
    private static final void pcSetresponsible(final CachedDemographicDocument cachedDemographicDocument, final String responsible) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.responsible = responsible;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 13, cachedDemographicDocument.responsible, responsible, 0);
    }
    
    private static final Date pcGetreviewDateTime(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.reviewDateTime;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 14);
        return cachedDemographicDocument.reviewDateTime;
    }
    
    private static final void pcSetreviewDateTime(final CachedDemographicDocument cachedDemographicDocument, final Date reviewDateTime) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.reviewDateTime = reviewDateTime;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 14, (Object)cachedDemographicDocument.reviewDateTime, (Object)reviewDateTime, 0);
    }
    
    private static final String pcGetreviewer(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.reviewer;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 15);
        return cachedDemographicDocument.reviewer;
    }
    
    private static final void pcSetreviewer(final CachedDemographicDocument cachedDemographicDocument, final String reviewer) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.reviewer = reviewer;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 15, cachedDemographicDocument.reviewer, reviewer, 0);
    }
    
    private static final String pcGetsource(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.source;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 16);
        return cachedDemographicDocument.source;
    }
    
    private static final void pcSetsource(final CachedDemographicDocument cachedDemographicDocument, final String source) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.source = source;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 16, cachedDemographicDocument.source, source, 0);
    }
    
    private static final String pcGetstatus(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.status;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 17);
        return cachedDemographicDocument.status;
    }
    
    private static final void pcSetstatus(final CachedDemographicDocument cachedDemographicDocument, final String status) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.status = status;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 17, cachedDemographicDocument.status, status, 0);
    }
    
    private static final Date pcGetupdateDateTime(final CachedDemographicDocument cachedDemographicDocument) {
        if (cachedDemographicDocument.pcStateManager == null) {
            return cachedDemographicDocument.updateDateTime;
        }
        cachedDemographicDocument.pcStateManager.accessingField(CachedDemographicDocument.pcInheritedFieldCount + 18);
        return cachedDemographicDocument.updateDateTime;
    }
    
    private static final void pcSetupdateDateTime(final CachedDemographicDocument cachedDemographicDocument, final Date updateDateTime) {
        if (cachedDemographicDocument.pcStateManager == null) {
            cachedDemographicDocument.updateDateTime = updateDateTime;
            return;
        }
        cachedDemographicDocument.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicDocument, CachedDemographicDocument.pcInheritedFieldCount + 18, (Object)cachedDemographicDocument.updateDateTime, (Object)updateDateTime, 0);
    }
    
    public Boolean pcIsDetached() {
        if (this.pcStateManager != null) {
            if (this.pcStateManager.isDetached()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        else {
            if (this.pcGetDetachedState() != null && this.pcGetDetachedState() != PersistenceCapable.DESERIALIZED) {
                return Boolean.TRUE;
            }
            if (!this.pcisDetachedStateDefinitive()) {
                return null;
            }
            if (this.pcGetDetachedState() == null) {
                return Boolean.FALSE;
            }
            return null;
        }
    }
    
    private boolean pcisDetachedStateDefinitive() {
        return false;
    }
    
    public Object pcGetDetachedState() {
        return this.pcDetachedState;
    }
    
    public void pcSetDetachedState(final Object pcDetachedState) {
        this.pcDetachedState = pcDetachedState;
    }
    
    private void writeObject(final ObjectOutputStream objectOutputStream) throws IOException {
        final boolean pcSerializing = this.pcSerializing();
        objectOutputStream.defaultWriteObject();
        if (pcSerializing) {
            this.pcSetDetachedState(null);
        }
    }
    
    private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        this.pcSetDetachedState(PersistenceCapable.DESERIALIZED);
        objectInputStream.defaultReadObject();
    }
}
