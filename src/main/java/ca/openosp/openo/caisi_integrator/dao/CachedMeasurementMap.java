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
public class CachedMeasurementMap extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedMeasurementMap>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityMeasurementMapPk;
    @Column(length = 20)
    @Index
    private String loincCode;
    @Column(nullable = false, length = 20)
    @Index
    private String identCode;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(nullable = false, length = 10)
    private String labType;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedMeasurementMap() {
        this.loincCode = null;
        this.identCode = null;
        this.name = null;
        this.labType = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityMeasurementMapPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityMeasurementMapPk) {
        pcSetfacilityMeasurementMapPk(this, facilityMeasurementMapPk);
    }
    
    public String getLoincCode() {
        return pcGetloincCode(this);
    }
    
    public void setLoincCode(final String loincCode) {
        pcSetloincCode(this, StringUtils.trimToEmpty(loincCode));
    }
    
    public String getIdentCode() {
        return pcGetidentCode(this);
    }
    
    public void setIdentCode(final String identCode) {
        pcSetidentCode(this, StringUtils.trimToEmpty(identCode));
    }
    
    public String getName() {
        return pcGetname(this);
    }
    
    public void setName(final String name) {
        pcSetname(this, StringUtils.trimToEmpty(name));
    }
    
    public String getLabType() {
        return pcGetlabType(this);
    }
    
    public void setLabType(final String labType) {
        pcSetlabType(this, StringUtils.trimToEmpty(labType));
    }
    
    @Override
    public int compareTo(final CachedMeasurementMap o) {
        return pcGetfacilityMeasurementMapPk(this).getCaisiItemId() - pcGetfacilityMeasurementMapPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityMeasurementMapPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 2160040152489451055L;
        CachedMeasurementMap.pcFieldNames = new String[] { "facilityMeasurementMapPk", "identCode", "labType", "loincCode", "name" };
        CachedMeasurementMap.pcFieldTypes = new Class[] { (CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedMeasurementMap.class$Ljava$lang$String != null) ? CachedMeasurementMap.class$Ljava$lang$String : (CachedMeasurementMap.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementMap.class$Ljava$lang$String != null) ? CachedMeasurementMap.class$Ljava$lang$String : (CachedMeasurementMap.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementMap.class$Ljava$lang$String != null) ? CachedMeasurementMap.class$Ljava$lang$String : (CachedMeasurementMap.class$Ljava$lang$String = class$("java.lang.String")), (CachedMeasurementMap.class$Ljava$lang$String != null) ? CachedMeasurementMap.class$Ljava$lang$String : (CachedMeasurementMap.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedMeasurementMap.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap != null) ? CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap : (CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementMap")), CachedMeasurementMap.pcFieldNames, CachedMeasurementMap.pcFieldTypes, CachedMeasurementMap.pcFieldFlags, CachedMeasurementMap.pcPCSuperclass, "CachedMeasurementMap", (PersistenceCapable)new CachedMeasurementMap());
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
        this.facilityMeasurementMapPk = null;
        this.identCode = null;
        this.labType = null;
        this.loincCode = null;
        this.name = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedMeasurementMap cachedMeasurementMap = new CachedMeasurementMap();
        if (b) {
            cachedMeasurementMap.pcClearFields();
        }
        cachedMeasurementMap.pcStateManager = pcStateManager;
        cachedMeasurementMap.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedMeasurementMap;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedMeasurementMap cachedMeasurementMap = new CachedMeasurementMap();
        if (b) {
            cachedMeasurementMap.pcClearFields();
        }
        cachedMeasurementMap.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedMeasurementMap;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 5;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedMeasurementMap.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementMapPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.identCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.labType = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.loincCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedMeasurementMap.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityMeasurementMapPk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.identCode);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.labType);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.loincCode);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
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
    
    protected void pcCopyField(final CachedMeasurementMap cachedMeasurementMap, final int n) {
        final int n2 = n - CachedMeasurementMap.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityMeasurementMapPk = cachedMeasurementMap.facilityMeasurementMapPk;
                return;
            }
            case 1: {
                this.identCode = cachedMeasurementMap.identCode;
                return;
            }
            case 2: {
                this.labType = cachedMeasurementMap.labType;
                return;
            }
            case 3: {
                this.loincCode = cachedMeasurementMap.loincCode;
                return;
            }
            case 4: {
                this.name = cachedMeasurementMap.name;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedMeasurementMap cachedMeasurementMap = (CachedMeasurementMap)o;
        if (cachedMeasurementMap.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedMeasurementMap, array[i]);
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
        fieldConsumer.storeObjectField(0 + CachedMeasurementMap.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityMeasurementMapPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedMeasurementMap\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap != null) ? CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap : (CachedMeasurementMap.class$Lca$openosp$openo$caisi_integrator$dao$CachedMeasurementMap = class$("ca.openosp.openo.caisi_integrator.dao.CachedMeasurementMap")), (Object)this.facilityMeasurementMapPk);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityMeasurementMapPk(final CachedMeasurementMap cachedMeasurementMap) {
        if (cachedMeasurementMap.pcStateManager == null) {
            return cachedMeasurementMap.facilityMeasurementMapPk;
        }
        cachedMeasurementMap.pcStateManager.accessingField(CachedMeasurementMap.pcInheritedFieldCount + 0);
        return cachedMeasurementMap.facilityMeasurementMapPk;
    }
    
    private static final void pcSetfacilityMeasurementMapPk(final CachedMeasurementMap cachedMeasurementMap, final FacilityIdIntegerCompositePk facilityMeasurementMapPk) {
        if (cachedMeasurementMap.pcStateManager == null) {
            cachedMeasurementMap.facilityMeasurementMapPk = facilityMeasurementMapPk;
            return;
        }
        cachedMeasurementMap.pcStateManager.settingObjectField((PersistenceCapable)cachedMeasurementMap, CachedMeasurementMap.pcInheritedFieldCount + 0, (Object)cachedMeasurementMap.facilityMeasurementMapPk, (Object)facilityMeasurementMapPk, 0);
    }
    
    private static final String pcGetidentCode(final CachedMeasurementMap cachedMeasurementMap) {
        if (cachedMeasurementMap.pcStateManager == null) {
            return cachedMeasurementMap.identCode;
        }
        cachedMeasurementMap.pcStateManager.accessingField(CachedMeasurementMap.pcInheritedFieldCount + 1);
        return cachedMeasurementMap.identCode;
    }
    
    private static final void pcSetidentCode(final CachedMeasurementMap cachedMeasurementMap, final String identCode) {
        if (cachedMeasurementMap.pcStateManager == null) {
            cachedMeasurementMap.identCode = identCode;
            return;
        }
        cachedMeasurementMap.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementMap, CachedMeasurementMap.pcInheritedFieldCount + 1, cachedMeasurementMap.identCode, identCode, 0);
    }
    
    private static final String pcGetlabType(final CachedMeasurementMap cachedMeasurementMap) {
        if (cachedMeasurementMap.pcStateManager == null) {
            return cachedMeasurementMap.labType;
        }
        cachedMeasurementMap.pcStateManager.accessingField(CachedMeasurementMap.pcInheritedFieldCount + 2);
        return cachedMeasurementMap.labType;
    }
    
    private static final void pcSetlabType(final CachedMeasurementMap cachedMeasurementMap, final String labType) {
        if (cachedMeasurementMap.pcStateManager == null) {
            cachedMeasurementMap.labType = labType;
            return;
        }
        cachedMeasurementMap.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementMap, CachedMeasurementMap.pcInheritedFieldCount + 2, cachedMeasurementMap.labType, labType, 0);
    }
    
    private static final String pcGetloincCode(final CachedMeasurementMap cachedMeasurementMap) {
        if (cachedMeasurementMap.pcStateManager == null) {
            return cachedMeasurementMap.loincCode;
        }
        cachedMeasurementMap.pcStateManager.accessingField(CachedMeasurementMap.pcInheritedFieldCount + 3);
        return cachedMeasurementMap.loincCode;
    }
    
    private static final void pcSetloincCode(final CachedMeasurementMap cachedMeasurementMap, final String loincCode) {
        if (cachedMeasurementMap.pcStateManager == null) {
            cachedMeasurementMap.loincCode = loincCode;
            return;
        }
        cachedMeasurementMap.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementMap, CachedMeasurementMap.pcInheritedFieldCount + 3, cachedMeasurementMap.loincCode, loincCode, 0);
    }
    
    private static final String pcGetname(final CachedMeasurementMap cachedMeasurementMap) {
        if (cachedMeasurementMap.pcStateManager == null) {
            return cachedMeasurementMap.name;
        }
        cachedMeasurementMap.pcStateManager.accessingField(CachedMeasurementMap.pcInheritedFieldCount + 4);
        return cachedMeasurementMap.name;
    }
    
    private static final void pcSetname(final CachedMeasurementMap cachedMeasurementMap, final String name) {
        if (cachedMeasurementMap.pcStateManager == null) {
            cachedMeasurementMap.name = name;
            return;
        }
        cachedMeasurementMap.pcStateManager.settingStringField((PersistenceCapable)cachedMeasurementMap, CachedMeasurementMap.pcInheritedFieldCount + 4, cachedMeasurementMap.name, name, 0);
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
