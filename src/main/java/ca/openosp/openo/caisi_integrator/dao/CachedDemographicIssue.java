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
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import ca.openosp.openo.caisi_integrator.util.Role;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedDemographicIssue extends AbstractModel<FacilityIdDemographicIssueCompositePk> implements PersistenceCapable
{
    @EmbeddedId
    private FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk;
    @Column(nullable = false, length = 128)
    private String issueDescription;
    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private Role issueRole;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean acute;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean certain;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean major;
    @Column(columnDefinition = "tinyint(1)")
    private Boolean resolved;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Boolean;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$util$Role;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedDemographicIssue() {
        this.issueDescription = null;
        this.issueRole = null;
        this.acute = null;
        this.certain = null;
        this.major = null;
        this.resolved = null;
    }
    
    public FacilityIdDemographicIssueCompositePk getFacilityDemographicIssuePk() {
        return pcGetfacilityDemographicIssuePk(this);
    }
    
    public void setFacilityDemographicIssuePk(final FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk) {
        pcSetfacilityDemographicIssuePk(this, facilityDemographicIssuePk);
    }
    
    public String getIssueDescription() {
        return pcGetissueDescription(this);
    }
    
    public void setIssueDescription(final String issueDescription) {
        pcSetissueDescription(this, StringUtils.trimToNull(issueDescription));
    }
    
    public Boolean getAcute() {
        return pcGetacute(this);
    }
    
    public void setAcute(final Boolean acute) {
        pcSetacute(this, acute);
    }
    
    public Role getIssueRole() {
        return pcGetissueRole(this);
    }
    
    public void setIssueRole(final Role issueRole) {
        pcSetissueRole(this, issueRole);
    }
    
    public Boolean getCertain() {
        return pcGetcertain(this);
    }
    
    public void setCertain(final Boolean certain) {
        pcSetcertain(this, certain);
    }
    
    public Boolean getMajor() {
        return pcGetmajor(this);
    }
    
    public void setMajor(final Boolean major) {
        pcSetmajor(this, major);
    }
    
    public Boolean getResolved() {
        return pcGetresolved(this);
    }
    
    public void setResolved(final Boolean resolved) {
        pcSetresolved(this, resolved);
    }
    
    @Override
    public FacilityIdDemographicIssueCompositePk getId() {
        return pcGetfacilityDemographicIssuePk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 2003466602538355709L;
        CachedDemographicIssue.pcFieldNames = new String[] { "acute", "certain", "facilityDemographicIssuePk", "issueDescription", "issueRole", "major", "resolved" };
        CachedDemographicIssue.pcFieldTypes = new Class[] { (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdDemographicIssueCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdDemographicIssueCompositePk")), (CachedDemographicIssue.class$Ljava$lang$String != null) ? CachedDemographicIssue.class$Ljava$lang$String : (CachedDemographicIssue.class$Ljava$lang$String = class$("java.lang.String")), (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$util$Role != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$util$Role : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$util$Role = class$("ca.openosp.openo.caisi_integrator.util.Role")), (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")), (CachedDemographicIssue.class$Ljava$lang$Boolean != null) ? CachedDemographicIssue.class$Ljava$lang$Boolean : (CachedDemographicIssue.class$Ljava$lang$Boolean = class$("java.lang.Boolean")) };
        CachedDemographicIssue.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicIssue")), CachedDemographicIssue.pcFieldNames, CachedDemographicIssue.pcFieldTypes, CachedDemographicIssue.pcFieldFlags, CachedDemographicIssue.pcPCSuperclass, "CachedDemographicIssue", (PersistenceCapable)new CachedDemographicIssue());
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
        this.acute = null;
        this.certain = null;
        this.facilityDemographicIssuePk = null;
        this.issueDescription = null;
        this.issueRole = null;
        this.major = null;
        this.resolved = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedDemographicIssue cachedDemographicIssue = new CachedDemographicIssue();
        if (b) {
            cachedDemographicIssue.pcClearFields();
        }
        cachedDemographicIssue.pcStateManager = pcStateManager;
        cachedDemographicIssue.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedDemographicIssue;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedDemographicIssue cachedDemographicIssue = new CachedDemographicIssue();
        if (b) {
            cachedDemographicIssue.pcClearFields();
        }
        cachedDemographicIssue.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedDemographicIssue;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 7;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedDemographicIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.acute = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.certain = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.facilityDemographicIssuePk = (FacilityIdDemographicIssueCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.issueDescription = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.issueRole = (Role)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.major = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.resolved = (Boolean)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedDemographicIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.acute);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.certain);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityDemographicIssuePk);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.issueDescription);
                return;
            }
            case 4: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.issueRole);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.major);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.resolved);
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
    
    protected void pcCopyField(final CachedDemographicIssue cachedDemographicIssue, final int n) {
        final int n2 = n - CachedDemographicIssue.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.acute = cachedDemographicIssue.acute;
                return;
            }
            case 1: {
                this.certain = cachedDemographicIssue.certain;
                return;
            }
            case 2: {
                this.facilityDemographicIssuePk = cachedDemographicIssue.facilityDemographicIssuePk;
                return;
            }
            case 3: {
                this.issueDescription = cachedDemographicIssue.issueDescription;
                return;
            }
            case 4: {
                this.issueRole = cachedDemographicIssue.issueRole;
                return;
            }
            case 5: {
                this.major = cachedDemographicIssue.major;
                return;
            }
            case 6: {
                this.resolved = cachedDemographicIssue.resolved;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedDemographicIssue cachedDemographicIssue = (CachedDemographicIssue)o;
        if (cachedDemographicIssue.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedDemographicIssue, array[i]);
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
        fieldConsumer.storeObjectField(2 + CachedDemographicIssue.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityDemographicIssuePk = (FacilityIdDemographicIssueCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedDemographicIssue\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue != null) ? CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue : (CachedDemographicIssue.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographicIssue = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographicIssue")), (Object)this.facilityDemographicIssuePk);
    }
    
    private static final Boolean pcGetacute(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.acute;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 0);
        return cachedDemographicIssue.acute;
    }
    
    private static final void pcSetacute(final CachedDemographicIssue cachedDemographicIssue, final Boolean acute) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.acute = acute;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 0, (Object)cachedDemographicIssue.acute, (Object)acute, 0);
    }
    
    private static final Boolean pcGetcertain(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.certain;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 1);
        return cachedDemographicIssue.certain;
    }
    
    private static final void pcSetcertain(final CachedDemographicIssue cachedDemographicIssue, final Boolean certain) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.certain = certain;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 1, (Object)cachedDemographicIssue.certain, (Object)certain, 0);
    }
    
    private static final FacilityIdDemographicIssueCompositePk pcGetfacilityDemographicIssuePk(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.facilityDemographicIssuePk;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 2);
        return cachedDemographicIssue.facilityDemographicIssuePk;
    }
    
    private static final void pcSetfacilityDemographicIssuePk(final CachedDemographicIssue cachedDemographicIssue, final FacilityIdDemographicIssueCompositePk facilityDemographicIssuePk) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.facilityDemographicIssuePk = facilityDemographicIssuePk;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 2, (Object)cachedDemographicIssue.facilityDemographicIssuePk, (Object)facilityDemographicIssuePk, 0);
    }
    
    private static final String pcGetissueDescription(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.issueDescription;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 3);
        return cachedDemographicIssue.issueDescription;
    }
    
    private static final void pcSetissueDescription(final CachedDemographicIssue cachedDemographicIssue, final String issueDescription) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.issueDescription = issueDescription;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingStringField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 3, cachedDemographicIssue.issueDescription, issueDescription, 0);
    }
    
    private static final Role pcGetissueRole(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.issueRole;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 4);
        return cachedDemographicIssue.issueRole;
    }
    
    private static final void pcSetissueRole(final CachedDemographicIssue cachedDemographicIssue, final Role issueRole) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.issueRole = issueRole;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 4, (Object)cachedDemographicIssue.issueRole, (Object)issueRole, 0);
    }
    
    private static final Boolean pcGetmajor(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.major;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 5);
        return cachedDemographicIssue.major;
    }
    
    private static final void pcSetmajor(final CachedDemographicIssue cachedDemographicIssue, final Boolean major) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.major = major;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 5, (Object)cachedDemographicIssue.major, (Object)major, 0);
    }
    
    private static final Boolean pcGetresolved(final CachedDemographicIssue cachedDemographicIssue) {
        if (cachedDemographicIssue.pcStateManager == null) {
            return cachedDemographicIssue.resolved;
        }
        cachedDemographicIssue.pcStateManager.accessingField(CachedDemographicIssue.pcInheritedFieldCount + 6);
        return cachedDemographicIssue.resolved;
    }
    
    private static final void pcSetresolved(final CachedDemographicIssue cachedDemographicIssue, final Boolean resolved) {
        if (cachedDemographicIssue.pcStateManager == null) {
            cachedDemographicIssue.resolved = resolved;
            return;
        }
        cachedDemographicIssue.pcStateManager.settingObjectField((PersistenceCapable)cachedDemographicIssue, CachedDemographicIssue.pcInheritedFieldCount + 6, (Object)cachedDemographicIssue.resolved, (Object)resolved, 0);
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
