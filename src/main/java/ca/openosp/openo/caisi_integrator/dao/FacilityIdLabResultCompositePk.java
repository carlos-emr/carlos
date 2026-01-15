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
public class FacilityIdLabResultCompositePk implements Serializable, PersistenceCapable
{
    @Index
    private Integer integratorFacilityId;
    @Column(length = 64)
    @Index
    private String labResultId;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public FacilityIdLabResultCompositePk() {
        this.integratorFacilityId = null;
        this.labResultId = null;
    }
    
    public FacilityIdLabResultCompositePk(final Integer integratorFacilityId, final String labResultId) {
        this.integratorFacilityId = null;
        this.labResultId = null;
        this.integratorFacilityId = integratorFacilityId;
        this.labResultId = labResultId;
    }
    
    public Integer getIntegratorFacilityId() {
        return pcGetintegratorFacilityId(this);
    }
    
    public void setIntegratorFacilityId(final Integer integratorFacilityId) {
        pcSetintegratorFacilityId(this, integratorFacilityId);
    }
    
    public String getLabResultId() {
        return pcGetlabResultId(this);
    }
    
    public void setLabResultId(final String labResultId) {
        pcSetlabResultId(this, StringUtils.trimToNull(labResultId));
    }
    
    @Override
    public String toString() {
        return "" + pcGetintegratorFacilityId(this) + ':' + pcGetlabResultId(this);
    }
    
    @Override
    public int hashCode() {
        return pcGetlabResultId(this).hashCode();
    }
    
    @Override
    public boolean equals(final Object o) {
        try {
            final FacilityIdLabResultCompositePk o2 = (FacilityIdLabResultCompositePk)o;
            return pcGetintegratorFacilityId(this).equals(pcGetintegratorFacilityId(o2)) && pcGetlabResultId(this).equals(pcGetlabResultId(o2));
        }
        catch (final RuntimeException e) {
            return false;
        }
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 4579769637734049905L;
        FacilityIdLabResultCompositePk.pcFieldNames = new String[] { "integratorFacilityId", "labResultId" };
        FacilityIdLabResultCompositePk.pcFieldTypes = new Class[] { (FacilityIdLabResultCompositePk.class$Ljava$lang$Integer != null) ? FacilityIdLabResultCompositePk.class$Ljava$lang$Integer : (FacilityIdLabResultCompositePk.class$Ljava$lang$Integer = class$("java.lang.Integer")), (FacilityIdLabResultCompositePk.class$Ljava$lang$String != null) ? FacilityIdLabResultCompositePk.class$Ljava$lang$String : (FacilityIdLabResultCompositePk.class$Ljava$lang$String = class$("java.lang.String")) };
        FacilityIdLabResultCompositePk.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((FacilityIdLabResultCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk != null) ? FacilityIdLabResultCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk : (FacilityIdLabResultCompositePk.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdLabResultCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdLabResultCompositePk")), FacilityIdLabResultCompositePk.pcFieldNames, FacilityIdLabResultCompositePk.pcFieldTypes, FacilityIdLabResultCompositePk.pcFieldFlags, FacilityIdLabResultCompositePk.pcPCSuperclass, (String)null, (PersistenceCapable)new FacilityIdLabResultCompositePk());
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
        this.labResultId = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk = new FacilityIdLabResultCompositePk();
        if (b) {
            facilityIdLabResultCompositePk.pcClearFields();
        }
        facilityIdLabResultCompositePk.pcStateManager = pcStateManager;
        facilityIdLabResultCompositePk.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)facilityIdLabResultCompositePk;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk = new FacilityIdLabResultCompositePk();
        if (b) {
            facilityIdLabResultCompositePk.pcClearFields();
        }
        facilityIdLabResultCompositePk.pcStateManager = pcStateManager;
        return (PersistenceCapable)facilityIdLabResultCompositePk;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - FacilityIdLabResultCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.labResultId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - FacilityIdLabResultCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.integratorFacilityId);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.labResultId);
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
    
    protected void pcCopyField(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk, final int n) {
        final int n2 = n - FacilityIdLabResultCompositePk.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.integratorFacilityId = facilityIdLabResultCompositePk.integratorFacilityId;
                return;
            }
            case 1: {
                this.labResultId = facilityIdLabResultCompositePk.labResultId;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk = (FacilityIdLabResultCompositePk)o;
        if (facilityIdLabResultCompositePk.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(facilityIdLabResultCompositePk, array[i]);
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
    
    private static final Integer pcGetintegratorFacilityId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            return facilityIdLabResultCompositePk.integratorFacilityId;
        }
        facilityIdLabResultCompositePk.pcStateManager.accessingField(FacilityIdLabResultCompositePk.pcInheritedFieldCount + 0);
        return facilityIdLabResultCompositePk.integratorFacilityId;
    }
    
    private static final void pcSetintegratorFacilityId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk, final Integer integratorFacilityId) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            facilityIdLabResultCompositePk.integratorFacilityId = integratorFacilityId;
            return;
        }
        facilityIdLabResultCompositePk.pcStateManager.settingObjectField((PersistenceCapable)facilityIdLabResultCompositePk, FacilityIdLabResultCompositePk.pcInheritedFieldCount + 0, (Object)facilityIdLabResultCompositePk.integratorFacilityId, (Object)integratorFacilityId, 0);
    }
    
    private static final String pcGetlabResultId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            return facilityIdLabResultCompositePk.labResultId;
        }
        facilityIdLabResultCompositePk.pcStateManager.accessingField(FacilityIdLabResultCompositePk.pcInheritedFieldCount + 1);
        return facilityIdLabResultCompositePk.labResultId;
    }
    
    private static final void pcSetlabResultId(final FacilityIdLabResultCompositePk facilityIdLabResultCompositePk, final String labResultId) {
        if (facilityIdLabResultCompositePk.pcStateManager == null) {
            facilityIdLabResultCompositePk.labResultId = labResultId;
            return;
        }
        facilityIdLabResultCompositePk.pcStateManager.settingStringField((PersistenceCapable)facilityIdLabResultCompositePk, FacilityIdLabResultCompositePk.pcInheritedFieldCount + 1, facilityIdLabResultCompositePk.labResultId, labResultId, 0);
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
