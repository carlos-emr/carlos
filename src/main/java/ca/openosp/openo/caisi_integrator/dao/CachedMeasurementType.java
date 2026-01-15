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
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedMeasurementType extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurementType>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementTypePk;
    @Column(nullable = false, length = 4)
    @Index
    private String type;
    @Column(nullable = false, length = 255)
    private String typeDescription;
    @Column(nullable = false, length = 255)
    private String measuringInstruction;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedMeasurementType() {
        this.type = null;
        this.typeDescription = null;
        this.measuringInstruction = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementTypePk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementTypePk) {
        pcSetfacilityMeasurementTypePk(this, facilityMeasurementTypePk);
    }
    
    public String getType() {
        return pcGettype(this);
    }
    
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToEmpty(type));
    }
    
    public String getTypeDescription() {
        return pcGettypeDescription(this);
    }
    
    public void setTypeDescription(final String typeDescription) {
        pcSettypeDescription(this, StringUtils.trimToEmpty(typeDescription));
    }
    
    public String getMeasuringInstruction() {
        return pcGetmeasuringInstruction(this);
    }
    
    public void setMeasuringInstruction(final String measuringInstruction) {
        pcSetmeasuringInstruction(this, StringUtils.trimToEmpty(measuringInstruction));
    }
    
    @Override
    public int compareTo(final CachedMeasurementType o) {
        return pcGetfacilityMeasurementTypePk(this).getCaisiItemId() - pcGetfacilityMeasurementTypePk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementTypePk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3892680324771808367L;
        CachedMeasurementType.pcFieldNames = new String[] { "facilityMeasurementTypePk", "measuringInstruction", "type", "typeDescription" };
        CachedMeasurementType.pcFieldTypes = new Class[] { (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurementType.class$Ljava$lang$String != null) ? CachedMeasurementType.class$Ljava$lang$String : (CachedMeasurementType.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementType.class$Ljava$lang$String != null) ? CachedMeasurementType.class$Ljava$lang$String : (CachedMeasurementType.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementType.class$Ljava$lang$String != null) ? CachedMeasurementType.class$Ljava$lang$String : (CachedMeasurementType.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurementType.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType != null) ? CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType : (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementType")), CachedMeasurementType.pcFieldNames, CachedMeasurementType.pcFieldTypes, CachedMeasurementType.pcFieldFlags, CachedMeasurementType.pcPCSuperclass, "CachedMeasurementType", (PersistenceCapable)new CachedMeasurementType());
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
        this.facilityMeasurementTypePk = null;
        this.measuringInstruction = null;
        this.type = null;
        this.typeDescription = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurementType cachedMeasurementType = new CachedMeasurementType();
        if (b) {
            cachedMeasurementType.pcClearFields();
        }
        cachedMeasurementType.pcStateManager = pcStateManager;
        cachedMeasurementType.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurementType;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurementType cachedMeasurementType = new CachedMeasurementType();
        if (b) {
            cachedMeasurementType.pcClearFields();
        }
        cachedMeasurementType.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurementType;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 4;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurementType.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementTypePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.measuringInstruction = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.typeDescription = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedMeasurementType.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementTypePk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.measuringInstruction);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.typeDescription);
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
    
    protected void pcCopyField(final CachedMeasurementType cachedMeasurementType, final int n) {
        final int n2 = n - CachedMeasurementType.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementTypePk = cachedMeasurementType.facilityMeasurementTypePk;
                return;
            }
            case 1: {
                this.measuringInstruction = cachedMeasurementType.measuringInstruction;
                return;
            }
            case 2: {
                this.type = cachedMeasurementType.type;
                return;
            }
            case 3: {
                this.typeDescription = cachedMeasurementType.typeDescription;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurementType cachedMeasurementType = (CachedMeasurementType)o;
        if (cachedMeasurementType.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurementType, array[i]);
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
        fieldConsumer.storeObjectField(0 + CachedMeasurementType.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementTypePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurementType\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType != null) ? CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType : (CachedMeasurementType.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementType = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementType")), (Object)this.facilityMeasurementTypePk);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementTypePk(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.facilityMeasurementTypePk;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 0);
        return cachedMeasurementType.facilityMeasurementTypePk;
    }
    
    private static final void pcSetfacilityMeasurementTypePk(final CachedMeasurementType cachedMeasurementType, final FacilityIdIntegerCompositePk facilityMeasurementTypePk) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.facilityMeasurementTypePk = facilityMeasurementTypePk;
            return;
        }
        cachedMeasurementType.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 0, (Object)cachedMeasurementType.facilityMeasurementTypePk, (Object)facilityMeasurementTypePk, 0);
    }
    
    private static final String pcGetmeasuringInstruction(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.measuringInstruction;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 1);
        return cachedMeasurementType.measuringInstruction;
    }
    
    private static final void pcSetmeasuringInstruction(final CachedMeasurementType cachedMeasurementType, final String measuringInstruction) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.measuringInstruction = measuringInstruction;
            return;
        }
        cachedMeasurementType.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 1, cachedMeasurementType.measuringInstruction, measuringInstruction, 0);
    }
    
    private static final String pcGettype(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.type;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 2);
        return cachedMeasurementType.type;
    }
    
    private static final void pcSettype(final CachedMeasurementType cachedMeasurementType, final String type) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.type = type;
            return;
        }
        cachedMeasurementType.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 2, cachedMeasurementType.type, type, 0);
    }
    
    private static final String pcGettypeDescription(final CachedMeasurementType cachedMeasurementType) {
        if (cachedMeasurementType.pcStateManager == null) {
            return cachedMeasurementType.typeDescription;
        }
        cachedMeasurementType.pcStateManager.accessingField(CachedMeasurementType.pcInheritedFieldCount + 3);
        return cachedMeasurementType.typeDescription;
    }
    
    private static final void pcSettypeDescription(final CachedMeasurementType cachedMeasurementType, final String typeDescription) {
        if (cachedMeasurementType.pcStateManager == null) {
            cachedMeasurementType.typeDescription = typeDescription;
            return;
        }
        cachedMeasurementType.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementType, CachedMeasurementType.pcInheritedFieldCount + 3, cachedMeasurementType.typeDescription, typeDescription, 0);
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
