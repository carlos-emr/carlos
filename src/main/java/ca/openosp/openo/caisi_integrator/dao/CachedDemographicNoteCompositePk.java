package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PCRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Column;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Embeddable;
import org.apache.openjpa.enhance.PersistenceCapable;
import java.io.Serializable;

@Embeddable
public class CachedDemographicNoteCompositePk implements Serializable, PersistenceCapable
{
    private static final long serialVersionUID = 1L;
    @Index
    private Integer integratorFacilityId;
    @Column(length = 50)
    @Index
    private String uuid;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk;
    private transient Object pcDetachedState;
    
    public CachedDemographicNoteCompositePk() {
        this.integratorFacilityId = null;
        this.uuid = null;
    }
    
    public CachedDemographicNoteCompositePk(final Integer integratorFacilityId, final String uuid) {
        this.integratorFacilityId = null;
        this.uuid = null;
        this.integratorFacilityId = integratorFacilityId;
        this.uuid = uuid;
    }
    
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }
    
    public String getUuid() {
        return pcGetuuid(this);
    }
    
    public void setUuid(final String uuid) {
        pcSetuuid(this, StringUtils.trimToNull(uuid));
    }
    
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetuuid(this);
    }
    
    @Override
    public int hashCode() {
        return pcGetuuid(this).hashCode();
    }
    
    @Override
    public boolean equals(final Object o) {
        try {
            final CachedDemographicNoteCompositePk o2 = (CachedDemographicNoteCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetuuid(this).equals(pcGetuuid(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        CachedDemographicNoteCompositePk.pcFieldNames = new String[] { "integratorFacilityId", "uuid" };
        CachedDemographicNoteCompositePk.pcFieldTypes = new Class[] { (CachedDemographicNoteCompositePk.class$Ljava$lang$Integer != null) ? CachedDemographicNoteCompositePk.class$Ljava$lang$Integer : (CachedDemographicNoteCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedDemographicNoteCompositePk.class$Ljava$lang$String != null) ? CachedDemographicNoteCompositePk.class$Ljava$lang$String : (CachedDemographicNoteCompositePk.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedDemographicNoteCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((CachedDemographicNoteCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk != null) ? CachedDemographicNoteCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk : (CachedDemographicNoteCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicNoteCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicNoteCompositePk")), CachedDemographicNoteCompositePk.pcFieldNames, CachedDemographicNoteCompositePk.pcFieldTypes, CachedDemographicNoteCompositePk.pcFieldFlags, CachedDemographicNoteCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new CachedDemographicNoteCompositePk());
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
        this.integratorFacilityId = null;
        this.uuid = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk = new CachedDemographicNoteCompositePk();
        if (b) {
            cachedDemographicNoteCompositePk.pcClearFields();
        }
        cachedDemographicNoteCompositePk.pcStateManager = pcStateManager;
        cachedDemographicNoteCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicNoteCompositePk;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk = new CachedDemographicNoteCompositePk();
        if (b) {
            cachedDemographicNoteCompositePk.pcClearFields();
        }
        cachedDemographicNoteCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicNoteCompositePk;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicNoteCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.uuid = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedDemographicNoteCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.uuid);
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
    
    protected void pcCopyField(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk, final int n) {
        final int n2 = n - CachedDemographicNoteCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = cachedDemographicNoteCompositePk.integratorFacilityId;
                return;
            }
            case 1: {
                this.uuid = cachedDemographicNoteCompositePk.uuid;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk = (CachedDemographicNoteCompositePk)o;
        if (cachedDemographicNoteCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicNoteCompositePk, array[i]);
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
    }
    
    public void pcCopyKeyFieldsToObjectId(final Object o) {
    }
    
    public void pcCopyKeyFieldsFromObjectId(final FieldConsumer fieldConsumer, final Object o) {
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
    }
    
    public Object pcNewObjectIdInstance() {
        return null;
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return null;
    }
    
    private static final Integer pcGetintegratorFacilityId(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            return cachedDemographicNoteCompositePk.integratorFacilityId;
        }
        cachedDemographicNoteCompositePk.pcStateManager.accessingField(CachedDemographicNoteCompositePk.pcInheritedFieldCount + 0);
        return cachedDemographicNoteCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk, final Integer integratorFacilityId) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            cachedDemographicNoteCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        cachedDemographicNoteCompositePk.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicNoteCompositePk, CachedDemographicNoteCompositePk.pcInheritedFieldCount + 0, (Object)cachedDemographicNoteCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final String pcGetuuid(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            return cachedDemographicNoteCompositePk.uuid;
        }
        cachedDemographicNoteCompositePk.pcStateManager.accessingField(CachedDemographicNoteCompositePk.pcInheritedFieldCount + 1);
        return cachedDemographicNoteCompositePk.uuid;
    }
    
    private static final void pcSetuuid(final CachedDemographicNoteCompositePk cachedDemographicNoteCompositePk, final String uuid) {
        if (cachedDemographicNoteCompositePk.pcStateManager == null) {
            cachedDemographicNoteCompositePk.uuid = uuid;
            return;
        }
        cachedDemographicNoteCompositePk.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicNoteCompositePk, CachedDemographicNoteCompositePk.pcInheritedFieldCount + 1, cachedDemographicNoteCompositePk.uuid, uuid, 0);
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
