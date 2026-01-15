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
import javax.persistence.PreRemove;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.Id;
import javax.persistence.Entity;

@Entity
public class SystemProperties extends AbstractModel<Integer> implements PersistenceCapable
{
    public static final int SYSTEM_PROPERTY_ID = 1;
    public static final int CODE_SCHEMA_VERSION = 1;
    @Id
    private Integer id;
    private int schemaVersion;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public SystemProperties() {
        this.id = Integer.valueOf(1);
        this.schemaVersion = 1;
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public int getSchemaVersion() {
        return pcGetschemaVersion(this);
    }
    
    @PreRemove
    protected void jpaPreventDelete() {
        throw new UnsupportedOperationException("Remove is not allowed for this type of item.");
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 1250262097996517519L;
        SystemProperties.pcFieldNames = new String[] { "id", "schemaVersion" };
        SystemProperties.pcFieldTypes = new Class[] { (SystemProperties.class$Ljava$lang$Integer != null) ? SystemProperties.class$Ljava$lang$Integer : (SystemProperties.class$Ljava$lang$Integer = class$("java.lang.Integer")), Integer.TYPE };
        SystemProperties.pcFieldFlags = new byte[] { 26, 26 };
        PCRegistry.register((SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties != null) ? SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties : (SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties = class$("ca.openosp.openo.caisi_integrator.dao.SystemProperties")), SystemProperties.pcFieldNames, SystemProperties.pcFieldTypes, SystemProperties.pcFieldFlags, SystemProperties.pcPCSuperclass, "SystemProperties", (PersistenceCapable)new SystemProperties());
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
        this.id = null;
        this.schemaVersion = 0;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final SystemProperties systemProperties = new SystemProperties();
        if (b) {
            systemProperties.pcClearFields();
        }
        systemProperties.pcStateManager = pcStateManager;
        systemProperties.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)systemProperties;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final SystemProperties systemProperties = new SystemProperties();
        if (b) {
            systemProperties.pcClearFields();
        }
        systemProperties.pcStateManager = pcStateManager;
        return (PersistenceCapable)systemProperties;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 2;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - SystemProperties.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.schemaVersion = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
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
        final int n2 = n - SystemProperties.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 1: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.schemaVersion);
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
    
    protected void pcCopyField(final SystemProperties systemProperties, final int n) {
        final int n2 = n - SystemProperties.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.id = systemProperties.id;
                return;
            }
            case 1: {
                this.schemaVersion = systemProperties.schemaVersion;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final SystemProperties systemProperties = (SystemProperties)o;
        if (systemProperties.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(systemProperties, array[i]);
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
        fieldConsumer.storeObjectField(0 + SystemProperties.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties != null) ? SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties : (SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties = class$("ca.openosp.openo.caisi_integrator.dao.SystemProperties")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties != null) ? SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties : (SystemProperties.class$Lca$openosp$openo$caisi_integrator$dao$SystemProperties = class$("ca.openosp.openo.caisi_integrator.dao.SystemProperties")), this.id);
    }
    
    private static final Integer pcGetid(final SystemProperties systemProperties) {
        if (systemProperties.pcStateManager == null) {
            return systemProperties.id;
        }
        systemProperties.pcStateManager.accessingField(SystemProperties.pcInheritedFieldCount + 0);
        return systemProperties.id;
    }
    
    private static final void pcSetid(final SystemProperties systemProperties, final Integer id) {
        if (systemProperties.pcStateManager == null) {
            systemProperties.id = id;
            return;
        }
        systemProperties.pcStateManager.settingObjectField((PersistenceCapable)systemProperties, SystemProperties.pcInheritedFieldCount + 0, (Object)systemProperties.id, (Object)id, 0);
    }
    
    private static final int pcGetschemaVersion(final SystemProperties systemProperties) {
        if (systemProperties.pcStateManager == null) {
            return systemProperties.schemaVersion;
        }
        systemProperties.pcStateManager.accessingField(SystemProperties.pcInheritedFieldCount + 1);
        return systemProperties.schemaVersion;
    }
    
    private static final void pcSetschemaVersion(final SystemProperties systemProperties, final int schemaVersion) {
        if (systemProperties.pcStateManager == null) {
            systemProperties.schemaVersion = schemaVersion;
            return;
        }
        systemProperties.pcStateManager.settingIntField((PersistenceCapable)systemProperties, SystemProperties.pcInheritedFieldCount + 1, systemProperties.schemaVersion, schemaVersion, 0);
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
