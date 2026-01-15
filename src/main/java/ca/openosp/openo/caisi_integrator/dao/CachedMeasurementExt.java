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
public class CachedMeasurementExt extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurementExt>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementExtPk;
    @Column(nullable = false)
    @Index
    private Integer measurementId;
    @Column(nullable = false, length = 20)
    private String keyval;
    @Column(nullable = false, columnDefinition = "text")
    private String val;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedMeasurementExt() {
        this.measurementId = null;
        this.keyval = null;
        this.val = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementExtPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementExtPk) {
        pcSetfacilityMeasurementExtPk(this, facilityMeasurementExtPk);
    }
    
    public Integer getMeasurementId() {
        return pcGetmeasurementId(this);
    }
    
    public void setMeasurementId(final Integer measurementId) {
        pcSetmeasurementId(this, measurementId);
    }
    
    public String getKeyval() {
        return pcGetkeyval(this);
    }
    
    public void setKeyval(final String keyval) {
        pcSetkeyval(this, StringUtils.trimToEmpty(keyval));
    }
    
    public String getVal() {
        return pcGetval(this);
    }
    
    public void setVal(final String val) {
        pcSetval(this, StringUtils.trimToEmpty(val));
    }
    
    @Override
    public int compareTo(final CachedMeasurementExt o) {
        return pcGetfacilityMeasurementExtPk(this).getCaisiItemId() - pcGetfacilityMeasurementExtPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementExtPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 5357802782015682797L;
        CachedMeasurementExt.pcFieldNames = new String[] { "facilityMeasurementExtPk", "keyval", "measurementId", "val" };
        CachedMeasurementExt.pcFieldTypes = new Class[] { (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurementExt.class$Ljava$lang$String != null) ? CachedMeasurementExt.class$Ljava$lang$String : (CachedMeasurementExt.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementExt.class$Ljava$lang$Integer != null) ? CachedMeasurementExt.class$Ljava$lang$Integer : (CachedMeasurementExt.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedMeasurementExt.class$Ljava$lang$String != null) ? CachedMeasurementExt.class$Ljava$lang$String : (CachedMeasurementExt.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurementExt.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt != null) ? CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt : (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementExt")), CachedMeasurementExt.pcFieldNames, CachedMeasurementExt.pcFieldTypes, CachedMeasurementExt.pcFieldFlags, CachedMeasurementExt.pcPCSuperclass, "CachedMeasurementExt", (PersistenceCapable)new CachedMeasurementExt());
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
        this.facilityMeasurementExtPk = null;
        this.keyval = null;
        this.measurementId = null;
        this.val = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurementExt cachedMeasurementExt = new CachedMeasurementExt();
        if (b) {
            cachedMeasurementExt.pcClearFields();
        }
        cachedMeasurementExt.pcStateManager = pcStateManager;
        cachedMeasurementExt.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurementExt;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurementExt cachedMeasurementExt = new CachedMeasurementExt();
        if (b) {
            cachedMeasurementExt.pcClearFields();
        }
        cachedMeasurementExt.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurementExt;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 4;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurementExt.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementExtPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.keyval = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.measurementId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.val = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedMeasurementExt.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementExtPk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.keyval);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.measurementId);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.val);
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
    
    protected void pcCopyField(final CachedMeasurementExt cachedMeasurementExt, final int n) {
        final int n2 = n - CachedMeasurementExt.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementExtPk = cachedMeasurementExt.facilityMeasurementExtPk;
                return;
            }
            case 1: {
                this.keyval = cachedMeasurementExt.keyval;
                return;
            }
            case 2: {
                this.measurementId = cachedMeasurementExt.measurementId;
                return;
            }
            case 3: {
                this.val = cachedMeasurementExt.val;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurementExt cachedMeasurementExt = (CachedMeasurementExt)o;
        if (cachedMeasurementExt.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurementExt, array[i]);
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
        fieldConsumer.storeObjectField(0 + CachedMeasurementExt.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementExtPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurementExt\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt != null) ? CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt : (CachedMeasurementExt.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementExt = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementExt")), (Object)this.facilityMeasurementExtPk);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementExtPk(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.facilityMeasurementExtPk;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 0);
        return cachedMeasurementExt.facilityMeasurementExtPk;
    }
    
    private static final void pcSetfacilityMeasurementExtPk(final CachedMeasurementExt cachedMeasurementExt, final FacilityIdIntegerCompositePk facilityMeasurementExtPk) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.facilityMeasurementExtPk = facilityMeasurementExtPk;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 0, (Object)cachedMeasurementExt.facilityMeasurementExtPk, (Object)facilityMeasurementExtPk, 0);
    }
    
    private static final String pcGetkeyval(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.keyval;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 1);
        return cachedMeasurementExt.keyval;
    }
    
    private static final void pcSetkeyval(final CachedMeasurementExt cachedMeasurementExt, final String keyval) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.keyval = keyval;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 1, cachedMeasurementExt.keyval, keyval, 0);
    }
    
    private static final Integer pcGetmeasurementId(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.measurementId;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 2);
        return cachedMeasurementExt.measurementId;
    }
    
    private static final void pcSetmeasurementId(final CachedMeasurementExt cachedMeasurementExt, final Integer measurementId) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.measurementId = measurementId;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 2, (Object)cachedMeasurementExt.measurementId, (Object)measurementId, 0);
    }
    
    private static final String pcGetval(final CachedMeasurementExt cachedMeasurementExt) {
        if (cachedMeasurementExt.pcStateManager == null) {
            return cachedMeasurementExt.val;
        }
        cachedMeasurementExt.pcStateManager.accessingField(CachedMeasurementExt.pcInheritedFieldCount + 3);
        return cachedMeasurementExt.val;
    }
    
    private static final void pcSetval(final CachedMeasurementExt cachedMeasurementExt, final String val) {
        if (cachedMeasurementExt.pcStateManager == null) {
            cachedMeasurementExt.val = val;
            return;
        }
        cachedMeasurementExt.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementExt, CachedMeasurementExt.pcInheritedFieldCount + 3, cachedMeasurementExt.val, val, 0);
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
