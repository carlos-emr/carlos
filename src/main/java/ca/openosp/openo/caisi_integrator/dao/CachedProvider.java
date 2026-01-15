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
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedProvider extends AbstractModel<FacilityIdStringCompositePk> implements Comparable<CachedProvider>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdStringCompositePk facilityIdStringCompositePk;
    @Column(length = 64)
    private String firstName;
    @Column(length = 64)
    private String lastName;
    @Column(length = 64)
    private String specialty;
    @Column(length = 64)
    private String workPhone;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedProvider() {
        this.firstName = null;
        this.lastName = null;
        this.specialty = null;
        this.workPhone = null;
    }
    
    public FacilityIdStringCompositePk getFacilityIdStringCompositePk() {
        return pcGetfacilityIdStringCompositePk(this);
    }
    
    public void setFacilityIdStringCompositePk(final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        pcSetfacilityIdStringCompositePk(this, facilityIdStringCompositePk);
    }
    
    @Override
    public int compareTo(final CachedProvider o) {
        return pcGetfacilityIdStringCompositePk(this).getCaisiItemId().compareTo(pcGetfacilityIdStringCompositePk(o).getCaisiItemId());
    }
    
    @Override
    public FacilityIdStringCompositePk getId() {
        return pcGetfacilityIdStringCompositePk(this);
    }
    
    public String getFirstName() {
        return pcGetfirstName(this);
    }
    
    public void setFirstName(final String firstName) {
        pcSetfirstName(this, StringUtils.trimToNull(firstName));
    }
    
    public String getLastName() {
        return pcGetlastName(this);
    }
    
    public void setLastName(final String lastName) {
        pcSetlastName(this, StringUtils.trimToNull(lastName));
    }
    
    public String getSpecialty() {
        return pcGetspecialty(this);
    }
    
    public void setSpecialty(final String specialty) {
        pcSetspecialty(this, StringUtils.trimToNull(specialty));
    }
    
    public String getWorkPhone() {
        return pcGetworkPhone(this);
    }
    
    public void setWorkPhone(final String workPhone) {
        pcSetworkPhone(this, StringUtils.trimToNull(workPhone));
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3178592570531907948L;
        CachedProvider.pcFieldNames = new String[] { "facilityIdStringCompositePk", "firstName", "lastName", "specialty", "workPhone" };
        CachedProvider.pcFieldTypes = new Class[] { (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk != null) ? CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk : (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdStringCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdStringCompositePk")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")), (CachedProvider.class$Ljava$lang$String != null) ? CachedProvider.class$Ljava$lang$String : (CachedProvider.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedProvider.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider != null) ? CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider : (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider = class$("ca.openosp.openo.caisi_integrator.dao.CachedProvider")), CachedProvider.pcFieldNames, CachedProvider.pcFieldTypes, CachedProvider.pcFieldFlags, CachedProvider.pcPCSuperclass, "CachedProvider", (PersistenceCapable)new CachedProvider());
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
        this.facilityIdStringCompositePk = null;
        this.firstName = null;
        this.lastName = null;
        this.specialty = null;
        this.workPhone = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedProvider cachedProvider = new CachedProvider();
        if (b) {
            cachedProvider.pcClearFields();
        }
        cachedProvider.pcStateManager = pcStateManager;
        cachedProvider.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedProvider;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedProvider cachedProvider = new CachedProvider();
        if (b) {
            cachedProvider.pcClearFields();
        }
        cachedProvider.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedProvider;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 5;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedProvider.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityIdStringCompositePk = (FacilityIdStringCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.firstName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.lastName = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.specialty = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.workPhone = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedProvider.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdStringCompositePk);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.firstName);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.lastName);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.specialty);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.workPhone);
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
    
    protected void pcCopyField(final CachedProvider cachedProvider, final int n) {
        final int n2 = n - CachedProvider.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.facilityIdStringCompositePk = cachedProvider.facilityIdStringCompositePk;
                return;
            }
            case 1: {
                this.firstName = cachedProvider.firstName;
                return;
            }
            case 2: {
                this.lastName = cachedProvider.lastName;
                return;
            }
            case 3: {
                this.specialty = cachedProvider.specialty;
                return;
            }
            case 4: {
                this.workPhone = cachedProvider.workPhone;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedProvider cachedProvider = (CachedProvider)o;
        if (cachedProvider.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedProvider, array[i]);
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
        fieldConsumer.storeObjectField(0 + CachedProvider.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdStringCompositePk = (FacilityIdStringCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedProvider\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider != null) ? CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider : (CachedProvider.class$Lca$openosp$openo$caisi_integrator$dao$CachedProvider = class$("ca.openosp.openo.caisi_integrator.dao.CachedProvider")), (Object)this.facilityIdStringCompositePk);
    }
    
    private static final FacilityIdStringCompositePk pcGetfacilityIdStringCompositePk(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.facilityIdStringCompositePk;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 0);
        return cachedProvider.facilityIdStringCompositePk;
    }
    
    private static final void pcSetfacilityIdStringCompositePk(final CachedProvider cachedProvider, final FacilityIdStringCompositePk facilityIdStringCompositePk) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.facilityIdStringCompositePk = facilityIdStringCompositePk;
            return;
        }
        cachedProvider.pcStateManager.settingObjectField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 0, (Object)cachedProvider.facilityIdStringCompositePk, (Object)facilityIdStringCompositePk, 0);
    }
    
    private static final String pcGetfirstName(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.firstName;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 1);
        return cachedProvider.firstName;
    }
    
    private static final void pcSetfirstName(final CachedProvider cachedProvider, final String firstName) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.firstName = firstName;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 1, cachedProvider.firstName, firstName, 0);
    }
    
    private static final String pcGetlastName(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.lastName;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 2);
        return cachedProvider.lastName;
    }
    
    private static final void pcSetlastName(final CachedProvider cachedProvider, final String lastName) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.lastName = lastName;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 2, cachedProvider.lastName, lastName, 0);
    }
    
    private static final String pcGetspecialty(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.specialty;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 3);
        return cachedProvider.specialty;
    }
    
    private static final void pcSetspecialty(final CachedProvider cachedProvider, final String specialty) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.specialty = specialty;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 3, cachedProvider.specialty, specialty, 0);
    }
    
    private static final String pcGetworkPhone(final CachedProvider cachedProvider) {
        if (cachedProvider.pcStateManager == null) {
            return cachedProvider.workPhone;
        }
        cachedProvider.pcStateManager.accessingField(CachedProvider.pcInheritedFieldCount + 4);
        return cachedProvider.workPhone;
    }
    
    private static final void pcSetworkPhone(final CachedProvider cachedProvider, final String workPhone) {
        if (cachedProvider.pcStateManager == null) {
            cachedProvider.workPhone = workPhone;
            return;
        }
        cachedProvider.pcStateManager.settingStringField((PersistenceCapable)cachedProvider, CachedProvider.pcInheritedFieldCount + 4, cachedProvider.workPhone, workPhone, 0);
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
