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
public class CachedEformValue extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedEformValue>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityEformValuesPk;
    @Column(nullable = false)
    private Integer formDataId;
    private Integer formId;
    @Index
    private Integer caisiDemographicId;
    @Column(nullable = false, length = 30)
    private String varName;
    @Column(columnDefinition = "text")
    private String varValue;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedEformValue() {
        this.formDataId = null;
        this.formId = null;
        this.caisiDemographicId = null;
        this.varName = null;
        this.varValue = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityEformValuesPk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityEformValuesPk) {
        pcSetfacilityEformValuesPk(this, facilityEformValuesPk);
    }
    
    public Integer getFormDataId() {
        return pcGetformDataId(this);
    }
    
    public void setFormDataId(final Integer formDataId) {
        pcSetformDataId(this, formDataId);
    }
    
    public Integer getFormId() {
        return pcGetformId(this);
    }
    
    public void setFormId(final Integer formId) {
        pcSetformId(this, formId);
    }
    
    public Integer getCaisiDemographicId() {
        return pcGetcaisiDemographicId(this);
    }
    
    public void setCaisiDemographicId(final Integer caisiDemographicId) {
        pcSetcaisiDemographicId(this, caisiDemographicId);
    }
    
    public String getVarName() {
        return pcGetvarName(this);
    }
    
    public void setVarName(final String varName) {
        pcSetvarName(this, StringUtils.trimToEmpty(varName));
    }
    
    public String getVarValue() {
        return pcGetvarValue(this);
    }
    
    public void setVarValue(final String varValue) {
        pcSetvarValue(this, StringUtils.trimToNull(varValue));
    }
    
    @Override
    public int compareTo(final CachedEformValue o) {
        return pcGetfacilityEformValuesPk(this).getCaisiItemId() - pcGetfacilityEformValuesPk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityEformValuesPk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -5338133436139653969L;
        CachedEformValue.pcFieldNames = new String[] { "caisiDemographicId", "facilityEformValuesPk", "formDataId", "formId", "varName", "varValue" };
        CachedEformValue.pcFieldTypes = new Class[] { (CachedEformValue.class$Ljava$lang$Integer != null) ? CachedEformValue.class$Ljava$lang$Integer : (CachedEformValue.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedEformValue.class$Ljava$lang$Integer != null) ? CachedEformValue.class$Ljava$lang$Integer : (CachedEformValue.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformValue.class$Ljava$lang$Integer != null) ? CachedEformValue.class$Ljava$lang$Integer : (CachedEformValue.class$Ljava$lang$Integer = class$("java.lang.Integer")), (CachedEformValue.class$Ljava$lang$String != null) ? CachedEformValue.class$Ljava$lang$String : (CachedEformValue.class$Ljava$lang$String = class$("java.lang.String")), (CachedEformValue.class$Ljava$lang$String != null) ? CachedEformValue.class$Ljava$lang$String : (CachedEformValue.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedEformValue.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue != null) ? CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue : (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue = class$("ca.openosp.openo.caisi_integrator.dao.CachedEformValue")), CachedEformValue.pcFieldNames, CachedEformValue.pcFieldTypes, CachedEformValue.pcFieldFlags, CachedEformValue.pcPCSuperclass, "CachedEformValue", (PersistenceCapable)new CachedEformValue());
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
        this.caisiDemographicId = null;
        this.facilityEformValuesPk = null;
        this.formDataId = null;
        this.formId = null;
        this.varName = null;
        this.varValue = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedEformValue cachedEformValue = new CachedEformValue();
        if (b) {
            cachedEformValue.pcClearFields();
        }
        cachedEformValue.pcStateManager = pcStateManager;
        cachedEformValue.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedEformValue;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedEformValue cachedEformValue = new CachedEformValue();
        if (b) {
            cachedEformValue.pcClearFields();
        }
        cachedEformValue.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedEformValue;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 6;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedEformValue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.facilityEformValuesPk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.formDataId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.formId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.varName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.varValue = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedEformValue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.caisiDemographicId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityEformValuesPk);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formDataId);
                return;
            }
            case 3: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.formId);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.varName);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.varValue);
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
    
    protected void pcCopyField(final CachedEformValue cachedEformValue, final int n) {
        final int n2 = n - CachedEformValue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.caisiDemographicId = cachedEformValue.caisiDemographicId;
                return;
            }
            case 1: {
                this.facilityEformValuesPk = cachedEformValue.facilityEformValuesPk;
                return;
            }
            case 2: {
                this.formDataId = cachedEformValue.formDataId;
                return;
            }
            case 3: {
                this.formId = cachedEformValue.formId;
                return;
            }
            case 4: {
                this.varName = cachedEformValue.varName;
                return;
            }
            case 5: {
                this.varValue = cachedEformValue.varValue;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedEformValue cachedEformValue = (CachedEformValue)o;
        if (cachedEformValue.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedEformValue, array[i]);
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
        fieldConsumer.storeObjectField(1 + CachedEformValue.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityEformValuesPk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedEformValue\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue != null) ? CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue : (CachedEformValue.class$Lca$openosp$openo$caisi_integrator$dao$CachedEformValue = class$("ca.openosp.openo.caisi_integrator.dao.CachedEformValue")), (Object)this.facilityEformValuesPk);
    }
    
    private static final Integer pcGetcaisiDemographicId(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.caisiDemographicId;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 0);
        return cachedEformValue.caisiDemographicId;
    }
    
    private static final void pcSetcaisiDemographicId(final CachedEformValue cachedEformValue, final Integer caisiDemographicId) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.caisiDemographicId = caisiDemographicId;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 0, (Object)cachedEformValue.caisiDemographicId, (Object)caisiDemographicId, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityEformValuesPk(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.facilityEformValuesPk;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 1);
        return cachedEformValue.facilityEformValuesPk;
    }
    
    private static final void pcSetfacilityEformValuesPk(final CachedEformValue cachedEformValue, final FacilityIdIntegerCompositePk facilityEformValuesPk) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.facilityEformValuesPk = facilityEformValuesPk;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 1, (Object)cachedEformValue.facilityEformValuesPk, (Object)facilityEformValuesPk, 0);
    }
    
    private static final Integer pcGetformDataId(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.formDataId;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 2);
        return cachedEformValue.formDataId;
    }
    
    private static final void pcSetformDataId(final CachedEformValue cachedEformValue, final Integer formDataId) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.formDataId = formDataId;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 2, (Object)cachedEformValue.formDataId, (Object)formDataId, 0);
    }
    
    private static final Integer pcGetformId(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.formId;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 3);
        return cachedEformValue.formId;
    }
    
    private static final void pcSetformId(final CachedEformValue cachedEformValue, final Integer formId) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.formId = formId;
            return;
        }
        cachedEformValue.pcStateManager.settingObjectField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 3, (Object)cachedEformValue.formId, (Object)formId, 0);
    }
    
    private static final String pcGetvarName(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.varName;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 4);
        return cachedEformValue.varName;
    }
    
    private static final void pcSetvarName(final CachedEformValue cachedEformValue, final String varName) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.varName = varName;
            return;
        }
        cachedEformValue.pcStateManager.settingStringField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 4, cachedEformValue.varName, varName, 0);
    }
    
    private static final String pcGetvarValue(final CachedEformValue cachedEformValue) {
        if (cachedEformValue.pcStateManager == null) {
            return cachedEformValue.varValue;
        }
        cachedEformValue.pcStateManager.accessingField(CachedEformValue.pcInheritedFieldCount + 5);
        return cachedEformValue.varValue;
    }
    
    private static final void pcSetvarValue(final CachedEformValue cachedEformValue, final String varValue) {
        if (cachedEformValue.pcStateManager == null) {
            cachedEformValue.varValue = varValue;
            return;
        }
        cachedEformValue.pcStateManager.settingStringField((PersistenceCapable)cachedEformValue, CachedEformValue.pcInheritedFieldCount + 5, cachedEformValue.varValue, varValue, 0);
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
