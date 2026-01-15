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
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;

@Entity
public class ProviderCommunication extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    private Integer sourceIntegratorFacilityId;
    @Column(nullable = false)
    private String sourceProviderId;
    @Column(nullable = false)
    @Index
    private Integer destinationIntegratorFacilityId;
    @Column(nullable = false)
    @Index
    private String destinationProviderId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date sentDate;
    @Column(nullable = false, columnDefinition = "tinyint")
    private boolean active;
    @Index
    private String type;
    @Column(columnDefinition = "mediumblob")
    private byte[] data;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$L$B;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public ProviderCommunication() {
        this.id = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceProviderId = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationProviderId = null;
        this.sentDate = new Date();
        this.active = true;
        this.type = null;
        this.data = null;
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public Integer getSourceIntegratorFacilityId() {
        return pcGetsourceIntegratorFacilityId(this);
    }
    
    public void setSourceIntegratorFacilityId(final Integer sourceIntegratorFacilityId) {
        pcSetsourceIntegratorFacilityId(this, sourceIntegratorFacilityId);
    }
    
    public String getSourceProviderId() {
        return pcGetsourceProviderId(this);
    }
    
    public void setSourceProviderId(final String sourceProviderId) {
        pcSetsourceProviderId(this, sourceProviderId);
    }
    
    public Integer getDestinationIntegratorFacilityId() {
        return pcGetdestinationIntegratorFacilityId(this);
    }
    
    public void setDestinationIntegratorFacilityId(final Integer destinationIntegratorFacilityId) {
        pcSetdestinationIntegratorFacilityId(this, destinationIntegratorFacilityId);
    }
    
    public String getDestinationProviderId() {
        return pcGetdestinationProviderId(this);
    }
    
    public void setDestinationProviderId(final String destinationProviderId) {
        pcSetdestinationProviderId(this, destinationProviderId);
    }
    
    public Date getSentDate() {
        return pcGetsentDate(this);
    }
    
    public void setSentDate(final Date sentDate) {
        pcSetsentDate(this, sentDate);
    }
    
    public boolean isActive() {
        return pcGetactive(this);
    }
    
    public void setActive(final boolean active) {
        pcSetactive(this, active);
    }
    
    public byte[] getData() {
        return pcGetdata(this);
    }
    
    public void setData(final byte[] data) {
        pcSetdata(this, data);
    }
    
    public String getType() {
        return pcGettype(this);
    }
    
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToNull(type));
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -8060429262360155339L;
        ProviderCommunication.pcFieldNames = new String[] { "active", "data", "destinationIntegratorFacilityId", "destinationProviderId", "id", "sentDate", "sourceIntegratorFacilityId", "sourceProviderId", "type" };
        ProviderCommunication.pcFieldTypes = new Class[] { Boolean.TYPE, (ProviderCommunication.class$L$B != null) ? ProviderCommunication.class$L$B : (ProviderCommunication.class$L$B = class$("[B")), (ProviderCommunication.class$Ljava$lang$Integer != null) ? ProviderCommunication.class$Ljava$lang$Integer : (ProviderCommunication.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ProviderCommunication.class$Ljava$lang$String != null) ? ProviderCommunication.class$Ljava$lang$String : (ProviderCommunication.class$Ljava$lang$String = class$("java.lang.String")), (ProviderCommunication.class$Ljava$lang$Integer != null) ? ProviderCommunication.class$Ljava$lang$Integer : (ProviderCommunication.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ProviderCommunication.class$Ljava$util$Date != null) ? ProviderCommunication.class$Ljava$util$Date : (ProviderCommunication.class$Ljava$util$Date = class$("java.util.Date")), (ProviderCommunication.class$Ljava$lang$Integer != null) ? ProviderCommunication.class$Ljava$lang$Integer : (ProviderCommunication.class$Ljava$lang$Integer = class$("java.lang.Integer")), (ProviderCommunication.class$Ljava$lang$String != null) ? ProviderCommunication.class$Ljava$lang$String : (ProviderCommunication.class$Ljava$lang$String = class$("java.lang.String")), (ProviderCommunication.class$Ljava$lang$String != null) ? ProviderCommunication.class$Ljava$lang$String : (ProviderCommunication.class$Ljava$lang$String = class$("java.lang.String")) };
        ProviderCommunication.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication != null) ? ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication : (ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication = class$("ca.openosp.openo.caisi_integrator.dao.ProviderCommunication")), ProviderCommunication.pcFieldNames, ProviderCommunication.pcFieldTypes, ProviderCommunication.pcFieldFlags, ProviderCommunication.pcPCSuperclass, "ProviderCommunication", (PersistenceCapable)new ProviderCommunication());
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
        this.active = false;
        this.data = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationProviderId = null;
        this.id = null;
        this.sentDate = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceProviderId = null;
        this.type = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final ProviderCommunication providerCommunication = new ProviderCommunication();
        if (b) {
            providerCommunication.pcClearFields();
        }
        providerCommunication.pcStateManager = pcStateManager;
        providerCommunication.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)providerCommunication;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final ProviderCommunication providerCommunication = new ProviderCommunication();
        if (b) {
            providerCommunication.pcClearFields();
        }
        providerCommunication.pcStateManager = pcStateManager;
        return (PersistenceCapable)providerCommunication;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 9;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - ProviderCommunication.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.active = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.data = (byte[])this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.destinationIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.destinationProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.sentDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.sourceIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.sourceProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - ProviderCommunication.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.active);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.data);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.destinationIntegratorFacilityId);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.destinationProviderId);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sentDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sourceIntegratorFacilityId);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.sourceProviderId);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
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
    
    protected void pcCopyField(final ProviderCommunication providerCommunication, final int n) {
        final int n2 = n - ProviderCommunication.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.active = providerCommunication.active;
                return;
            }
            case 1: {
                this.data = providerCommunication.data;
                return;
            }
            case 2: {
                this.destinationIntegratorFacilityId = providerCommunication.destinationIntegratorFacilityId;
                return;
            }
            case 3: {
                this.destinationProviderId = providerCommunication.destinationProviderId;
                return;
            }
            case 4: {
                this.id = providerCommunication.id;
                return;
            }
            case 5: {
                this.sentDate = providerCommunication.sentDate;
                return;
            }
            case 6: {
                this.sourceIntegratorFacilityId = providerCommunication.sourceIntegratorFacilityId;
                return;
            }
            case 7: {
                this.sourceProviderId = providerCommunication.sourceProviderId;
                return;
            }
            case 8: {
                this.type = providerCommunication.type;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final ProviderCommunication providerCommunication = (ProviderCommunication)o;
        if (providerCommunication.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(providerCommunication, array[i]);
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
        fieldConsumer.storeObjectField(4 + ProviderCommunication.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication != null) ? ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication : (ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication = class$("ca.openosp.openo.caisi_integrator.dao.ProviderCommunication")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication != null) ? ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication : (ProviderCommunication.class$Lca$openosp$openo$caisi_integrator$dao$ProviderCommunication = class$("ca.openosp.openo.caisi_integrator.dao.ProviderCommunication")), this.id);
    }
    
    private static final boolean pcGetactive(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.active;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 0);
        return providerCommunication.active;
    }
    
    private static final void pcSetactive(final ProviderCommunication providerCommunication, final boolean active) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.active = active;
            return;
        }
        providerCommunication.pcStateManager.settingBooleanField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 0, providerCommunication.active, active, 0);
    }
    
    private static final byte[] pcGetdata(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.data;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 1);
        return providerCommunication.data;
    }
    
    private static final void pcSetdata(final ProviderCommunication providerCommunication, final byte[] data) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.data = data;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 1, (Object)providerCommunication.data, (Object)data, 0);
    }
    
    private static final Integer pcGetdestinationIntegratorFacilityId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.destinationIntegratorFacilityId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 2);
        return providerCommunication.destinationIntegratorFacilityId;
    }
    
    private static final void pcSetdestinationIntegratorFacilityId(final ProviderCommunication providerCommunication, final Integer destinationIntegratorFacilityId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.destinationIntegratorFacilityId = destinationIntegratorFacilityId;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 2, (Object)providerCommunication.destinationIntegratorFacilityId, (Object)destinationIntegratorFacilityId, 0);
    }
    
    private static final String pcGetdestinationProviderId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.destinationProviderId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 3);
        return providerCommunication.destinationProviderId;
    }
    
    private static final void pcSetdestinationProviderId(final ProviderCommunication providerCommunication, final String destinationProviderId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.destinationProviderId = destinationProviderId;
            return;
        }
        providerCommunication.pcStateManager.settingStringField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 3, providerCommunication.destinationProviderId, destinationProviderId, 0);
    }
    
    private static final Integer pcGetid(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.id;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 4);
        return providerCommunication.id;
    }
    
    private static final void pcSetid(final ProviderCommunication providerCommunication, final Integer id) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.id = id;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 4, (Object)providerCommunication.id, (Object)id, 0);
    }
    
    private static final Date pcGetsentDate(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.sentDate;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 5);
        return providerCommunication.sentDate;
    }
    
    private static final void pcSetsentDate(final ProviderCommunication providerCommunication, final Date sentDate) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.sentDate = sentDate;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 5, (Object)providerCommunication.sentDate, (Object)sentDate, 0);
    }
    
    private static final Integer pcGetsourceIntegratorFacilityId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.sourceIntegratorFacilityId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 6);
        return providerCommunication.sourceIntegratorFacilityId;
    }
    
    private static final void pcSetsourceIntegratorFacilityId(final ProviderCommunication providerCommunication, final Integer sourceIntegratorFacilityId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.sourceIntegratorFacilityId = sourceIntegratorFacilityId;
            return;
        }
        providerCommunication.pcStateManager.settingObjectField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 6, (Object)providerCommunication.sourceIntegratorFacilityId, (Object)sourceIntegratorFacilityId, 0);
    }
    
    private static final String pcGetsourceProviderId(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.sourceProviderId;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 7);
        return providerCommunication.sourceProviderId;
    }
    
    private static final void pcSetsourceProviderId(final ProviderCommunication providerCommunication, final String sourceProviderId) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.sourceProviderId = sourceProviderId;
            return;
        }
        providerCommunication.pcStateManager.settingStringField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 7, providerCommunication.sourceProviderId, sourceProviderId, 0);
    }
    
    private static final String pcGettype(final ProviderCommunication providerCommunication) {
        if (providerCommunication.pcStateManager == null) {
            return providerCommunication.type;
        }
        providerCommunication.pcStateManager.accessingField(ProviderCommunication.pcInheritedFieldCount + 8);
        return providerCommunication.type;
    }
    
    private static final void pcSettype(final ProviderCommunication providerCommunication, final String type) {
        if (providerCommunication.pcStateManager == null) {
            providerCommunication.type = type;
            return;
        }
        providerCommunication.pcStateManager.settingStringField((PersistenceCapable)providerCommunication, ProviderCommunication.pcInheritedFieldCount + 8, providerCommunication.type, type, 0);
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
