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
public class FacilityIdStringCompositePk implements Serializable, PersistenceCapable
{
    @Index
    private Integer integratorFacilityId;
    @Column(length = 16)
    @Index
    private String caisiItemId;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public FacilityIdStringCompositePk() {
        this.integratorFacilityId = null;
        this.caisiItemId = null;
    }
    
    public FacilityIdStringCompositePk(final Integer integratorFacilityId, final String caisiItemId) {
        this.integratorFacilityId = null;
        this.caisiItemId = null;
        this.integratorFacilityId = integratorFacilityId;
        this.caisiItemId = caisiItemId;
    }
    
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }
    
    public String getCaisiItemId() {
        return pcGetcaisiItemId(this);
    }
    
    public void setCaisiItemId(final String caisiItemId) {
        pcSetcaisiItemId(this, StringUtils.trimToNull(caisiItemId));
    }
    
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetcaisiItemId(this);
    }
    
    @Override
    public int hashCode() {
        return pcGetcaisiItemId(this).hashCode();
    }
    
    @Override
    public boolean equals(final Object o) {
        try {
            final FacilityIdStringCompositePk o2 = (FacilityIdStringCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetcaisiItemId(this).equals(pcGetcaisiItemId(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3604093556893062689L;
        FacilityIdStringCompositePk.pcFieldNames = new String[] { "caisiItemId", "integratorFacilityId" };
        FacilityIdStringCompositePk.pcFieldTypes = new Class[] { (FacilityIdStringCompositePk.class$Ljava$lang$String != null) ? FacilityIdStringCompositePk.class$Ljava$lang$String : (FacilityIdStringCompositePk.class$Ljava$lang$String = class$("java.lang.String")), (FacilityIdStringCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdStringCompositePk.class$Ljava$lang$Integer : (FacilityIdStringCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")) };
        FacilityIdStringCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((FacilityIdStringCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk != null) ? FacilityIdStringCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk : (FacilityIdStringCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdStringCompositePk")), FacilityIdStringCompositePk.pcFieldNames, FacilityIdStringCompositePk.pcFieldTypes, FacilityIdStringCompositePk.pcFieldFlags, FacilityIdStringCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new FacilityIdStringCompositePk());
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
        this.caisiItemId = null;
        this.integratorFacilityId = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final FacilityIdStringCompositePk facilityIdStringCompositePk = new FacilityIdStringCompositePk();
        if (b) {
            facilityIdStringCompositePk.pcClearFields();
        }
        facilityIdStringCompositePk.pcStateManager = pcStateManager;
        facilityIdStringCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facilityIdStringCompositePk;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final FacilityIdStringCompositePk facilityIdStringCompositePk = new FacilityIdStringCompositePk();
        if (b) {
            facilityIdStringCompositePk.pcClearFields();
        }
        facilityIdStringCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)facilityIdStringCompositePk;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - FacilityIdStringCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiItemId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
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
        final int n2 = n - FacilityIdStringCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.caisiItemId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
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
    
    protected void pcCopyField(final FacilityIdStringCompositePk facilityIdStringCompositePk, final int n) {
        final int n2 = n - FacilityIdStringCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiItemId = facilityIdStringCompositePk.caisiItemId;
                return;
            }
            case 1: {
                this.integratorFacilityId = facilityIdStringCompositePk.integratorFacilityId;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final FacilityIdStringCompositePk facilityIdStringCompositePk = (FacilityIdStringCompositePk)o;
        if (facilityIdStringCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facilityIdStringCompositePk, array[i]);
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
    
    private static final String pcGetcaisiItemId(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            return facilityIdStringCompositePk.caisiItemId;
        }
        facilityIdStringCompositePk.pcStateManager.accessingField(FacilityIdStringCompositePk.pcInheritedFieldCount + 0);
        return facilityIdStringCompositePk.caisiItemId;
    }
    
    private static final void pcSetcaisiItemId(final FacilityIdStringCompositePk facilityIdStringCompositePk, final String caisiItemId) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            facilityIdStringCompositePk.caisiItemId = caisiItemId;
            return;
        }
        facilityIdStringCompositePk.pcStateManager.settingStringField((PersistenceCapable)facilityIdStringCompositePk, FacilityIdStringCompositePk.pcInheritedFieldCount + 0, facilityIdStringCompositePk.caisiItemId, caisiItemId, 0);
    }
    
    private static final Integer pcGetintegratorFacilityId(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            return facilityIdStringCompositePk.integratorFacilityId;
        }
        facilityIdStringCompositePk.pcStateManager.accessingField(FacilityIdStringCompositePk.pcInheritedFieldCount + 1);
        return facilityIdStringCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final FacilityIdStringCompositePk facilityIdStringCompositePk, final Integer integratorFacilityId) {
        if (facilityIdStringCompositePk.pcStateManager == null) {
            facilityIdStringCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        facilityIdStringCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdStringCompositePk, FacilityIdStringCompositePk.pcInheritedFieldCount + 1, (Object)facilityIdStringCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
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
