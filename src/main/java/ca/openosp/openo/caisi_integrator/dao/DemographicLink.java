package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.IntId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.UniqueConstraint;
import javax.persistence.Table;
import javax.persistence.Entity;

@Entity
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = { "integratorDemographicFacilityId1", "caisiDemographicId1", "integratorDemographicFacilityId2", "caisiDemographicId2" }) })
public class DemographicLink extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    @Index
    private Integer integratorDemographicFacilityId1;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId1;
    @Column(nullable = false)
    @Index
    private Integer integratorDemographicFacilityId2;
    @Column(nullable = false)
    @Index
    private Integer caisiDemographicId2;
    @Column(nullable = false)
    private Integer creatorIntegratorProviderFacilityId;
    @Column(nullable = false, length = 16)
    private String creatorCaisiProviderId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdDate;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public DemographicLink() {
        this.id = null;
        this.integratorDemographicFacilityId1 = null;
        this.caisiDemographicId1 = null;
        this.integratorDemographicFacilityId2 = null;
        this.caisiDemographicId2 = null;
        this.creatorIntegratorProviderFacilityId = null;
        this.creatorCaisiProviderId = null;
        this.createdDate = new Date();
    }
    
    public Integer getIntegratorDemographicFacilityId1() {
        return pcGetintegratorDemographicFacilityId1(this);
    }
    
    public void setIntegratorDemographicFacilityId1(final Integer integratorDemographicFacilityId1) {
        pcSetintegratorDemographicFacilityId1(this, integratorDemographicFacilityId1);
    }
    
    public Integer getCaisiDemographicId1() {
        return pcGetcaisiDemographicId1(this);
    }
    
    public void setCaisiDemographicId1(final Integer caisiDemographicId1) {
        pcSetcaisiDemographicId1(this, caisiDemographicId1);
    }
    
    public Integer getIntegratorDemographicFacilityId2() {
        return pcGetintegratorDemographicFacilityId2(this);
    }
    
    public void setIntegratorDemographicFacilityId2(final Integer integratorDemographicFacilityId2) {
        pcSetintegratorDemographicFacilityId2(this, integratorDemographicFacilityId2);
    }
    
    public Integer getCaisiDemographicId2() {
        return pcGetcaisiDemographicId2(this);
    }
    
    public void setCaisiDemographicId2(final Integer caisiDemographicId2) {
        pcSetcaisiDemographicId2(this, caisiDemographicId2);
    }
    
    public Integer getCreatorIntegratorProviderFacilityId() {
        return pcGetcreatorIntegratorProviderFacilityId(this);
    }
    
    public void setCreatorIntegratorProviderFacilityId(final Integer creatorIntegratorProviderFacilityId) {
        pcSetcreatorIntegratorProviderFacilityId(this, creatorIntegratorProviderFacilityId);
    }
    
    public String getCreatorCaisiProviderId() {
        return pcGetcreatorCaisiProviderId(this);
    }
    
    public void setCreatorCaisiProviderId(final String creatorCaisiProviderId) {
        pcSetcreatorCaisiProviderId(this, creatorCaisiProviderId);
    }
    
    public Date getCreatedDate() {
        return pcGetcreatedDate(this);
    }
    
    @Override
    public int hashCode() {
        return pcGetid(this).hashCode();
    }
    
    @Override
    public boolean equals(final Object o) {
        return pcGetid(this).equals(((DemographicLink)o).getId());
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 4681496997954442913L;
        DemographicLink.pcFieldNames = new String[] { "caisiDemographicId1", "caisiDemographicId2", "createdDate", "creatorCaisiProviderId", "creatorIntegratorProviderFacilityId", "id", "integratorDemographicFacilityId1", "integratorDemographicFacilityId2" };
        DemographicLink.pcFieldTypes = new Class[] { (DemographicLink.class$Ljava$lang$Integer != null) ? DemographicLink.class$Ljava$lang$Integer : (DemographicLink.class$Ljava$lang$Integer = class$("java.lang.Integer")), (DemographicLink.class$Ljava$lang$Integer != null) ? DemographicLink.class$Ljava$lang$Integer : (DemographicLink.class$Ljava$lang$Integer = class$("java.lang.Integer")), (DemographicLink.class$Ljava$util$Date != null) ? DemographicLink.class$Ljava$util$Date : (DemographicLink.class$Ljava$util$Date = class$("java.util.Date")), (DemographicLink.class$Ljava$lang$String != null) ? DemographicLink.class$Ljava$lang$String : (DemographicLink.class$Ljava$lang$String = class$("java.lang.String")), (DemographicLink.class$Ljava$lang$Integer != null) ? DemographicLink.class$Ljava$lang$Integer : (DemographicLink.class$Ljava$lang$Integer = class$("java.lang.Integer")), (DemographicLink.class$Ljava$lang$Integer != null) ? DemographicLink.class$Ljava$lang$Integer : (DemographicLink.class$Ljava$lang$Integer = class$("java.lang.Integer")), (DemographicLink.class$Ljava$lang$Integer != null) ? DemographicLink.class$Ljava$lang$Integer : (DemographicLink.class$Ljava$lang$Integer = class$("java.lang.Integer")), (DemographicLink.class$Ljava$lang$Integer != null) ? DemographicLink.class$Ljava$lang$Integer : (DemographicLink.class$Ljava$lang$Integer = class$("java.lang.Integer")) };
        DemographicLink.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink != null) ? DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink : (DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink = class$("ca.openosp.openo.caisi_integrator.dao.DemographicLink")), DemographicLink.pcFieldNames, DemographicLink.pcFieldTypes, DemographicLink.pcFieldFlags, DemographicLink.pcPCSuperclass, "DemographicLink", (PersistenceCapable)new DemographicLink());
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
        this.caisiDemographicId1 = null;
        this.caisiDemographicId2 = null;
        this.createdDate = null;
        this.creatorCaisiProviderId = null;
        this.creatorIntegratorProviderFacilityId = null;
        this.id = null;
        this.integratorDemographicFacilityId1 = null;
        this.integratorDemographicFacilityId2 = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final DemographicLink demographicLink = new DemographicLink();
        if (b) {
            demographicLink.pcClearFields();
        }
        demographicLink.pcStateManager = pcStateManager;
        demographicLink.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)demographicLink;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final DemographicLink demographicLink = new DemographicLink();
        if (b) {
            demographicLink.pcClearFields();
        }
        demographicLink.pcStateManager = pcStateManager;
        return (PersistenceCapable)demographicLink;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 8;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - DemographicLink.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId1 = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.caisiDemographicId2 = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.createdDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.creatorCaisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.creatorIntegratorProviderFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.integratorDemographicFacilityId1 = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.integratorDemographicFacilityId2 = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
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
        final int n2 = n - DemographicLink.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId1);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId2);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.createdDate);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.creatorCaisiProviderId);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.creatorIntegratorProviderFacilityId);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorDemographicFacilityId1);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorDemographicFacilityId2);
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
    
    protected void pcCopyField(final DemographicLink demographicLink, final int n) {
        final int n2 = n - DemographicLink.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId1 = demographicLink.caisiDemographicId1;
                return;
            }
            case 1: {
                this.caisiDemographicId2 = demographicLink.caisiDemographicId2;
                return;
            }
            case 2: {
                this.createdDate = demographicLink.createdDate;
                return;
            }
            case 3: {
                this.creatorCaisiProviderId = demographicLink.creatorCaisiProviderId;
                return;
            }
            case 4: {
                this.creatorIntegratorProviderFacilityId = demographicLink.creatorIntegratorProviderFacilityId;
                return;
            }
            case 5: {
                this.id = demographicLink.id;
                return;
            }
            case 6: {
                this.integratorDemographicFacilityId1 = demographicLink.integratorDemographicFacilityId1;
                return;
            }
            case 7: {
                this.integratorDemographicFacilityId2 = demographicLink.integratorDemographicFacilityId2;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final DemographicLink demographicLink = (DemographicLink)o;
        if (demographicLink.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(demographicLink, array[i]);
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
        fieldConsumer.storeObjectField(5 + DemographicLink.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink != null) ? DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink : (DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink = class$("ca.openosp.openo.caisi_integrator.dao.DemographicLink")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink != null) ? DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink : (DemographicLink.class$Lca$openosp$openo$caisi_integrator$dao$DemographicLink = class$("ca.openosp.openo.caisi_integrator.dao.DemographicLink")), this.id);
    }
    
    private static final Integer pcGetcaisiDemographicId1(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.caisiDemographicId1;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 0);
        return demographicLink.caisiDemographicId1;
    }
    
    private static final void pcSetcaisiDemographicId1(final DemographicLink demographicLink, final Integer caisiDemographicId1) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.caisiDemographicId1 = caisiDemographicId1;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 0, (Object)demographicLink.caisiDemographicId1, (Object)caisiDemographicId1, 0);
    }
    
    private static final Integer pcGetcaisiDemographicId2(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.caisiDemographicId2;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 1);
        return demographicLink.caisiDemographicId2;
    }
    
    private static final void pcSetcaisiDemographicId2(final DemographicLink demographicLink, final Integer caisiDemographicId2) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.caisiDemographicId2 = caisiDemographicId2;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 1, (Object)demographicLink.caisiDemographicId2, (Object)caisiDemographicId2, 0);
    }
    
    private static final Date pcGetcreatedDate(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.createdDate;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 2);
        return demographicLink.createdDate;
    }
    
    private static final void pcSetcreatedDate(final DemographicLink demographicLink, final Date createdDate) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.createdDate = createdDate;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 2, (Object)demographicLink.createdDate, (Object)createdDate, 0);
    }
    
    private static final String pcGetcreatorCaisiProviderId(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.creatorCaisiProviderId;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 3);
        return demographicLink.creatorCaisiProviderId;
    }
    
    private static final void pcSetcreatorCaisiProviderId(final DemographicLink demographicLink, final String creatorCaisiProviderId) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.creatorCaisiProviderId = creatorCaisiProviderId;
            return;
        }
        demographicLink.pcStateManager.settingStringField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 3, demographicLink.creatorCaisiProviderId, creatorCaisiProviderId, 0);
    }
    
    private static final Integer pcGetcreatorIntegratorProviderFacilityId(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.creatorIntegratorProviderFacilityId;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 4);
        return demographicLink.creatorIntegratorProviderFacilityId;
    }
    
    private static final void pcSetcreatorIntegratorProviderFacilityId(final DemographicLink demographicLink, final Integer creatorIntegratorProviderFacilityId) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.creatorIntegratorProviderFacilityId = creatorIntegratorProviderFacilityId;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 4, (Object)demographicLink.creatorIntegratorProviderFacilityId, (Object)creatorIntegratorProviderFacilityId, 0);
    }
    
    private static final Integer pcGetid(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.id;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 5);
        return demographicLink.id;
    }
    
    private static final void pcSetid(final DemographicLink demographicLink, final Integer id) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.id = id;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 5, (Object)demographicLink.id, (Object)id, 0);
    }
    
    private static final Integer pcGetintegratorDemographicFacilityId1(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.integratorDemographicFacilityId1;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 6);
        return demographicLink.integratorDemographicFacilityId1;
    }
    
    private static final void pcSetintegratorDemographicFacilityId1(final DemographicLink demographicLink, final Integer integratorDemographicFacilityId1) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.integratorDemographicFacilityId1 = integratorDemographicFacilityId1;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 6, (Object)demographicLink.integratorDemographicFacilityId1, (Object)integratorDemographicFacilityId1, 0);
    }
    
    private static final Integer pcGetintegratorDemographicFacilityId2(final DemographicLink demographicLink) {
        if (demographicLink.pcStateManager == null) {
            return demographicLink.integratorDemographicFacilityId2;
        }
        demographicLink.pcStateManager.accessingField(DemographicLink.pcInheritedFieldCount + 7);
        return demographicLink.integratorDemographicFacilityId2;
    }
    
    private static final void pcSetintegratorDemographicFacilityId2(final DemographicLink demographicLink, final Integer integratorDemographicFacilityId2) {
        if (demographicLink.pcStateManager == null) {
            demographicLink.integratorDemographicFacilityId2 = integratorDemographicFacilityId2;
            return;
        }
        demographicLink.pcStateManager.settingObjectField((PersistenceCapable)demographicLink, DemographicLink.pcInheritedFieldCount + 7, (Object)demographicLink.integratorDemographicFacilityId2, (Object)integratorDemographicFacilityId2, 0);
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
            if (this.id != null) {
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
