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
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import ca.openosp.openo.caisi_integrator.util.CodeType;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;
import ca.openosp.openo.caisi_integrator.util.Named;

@Entity(name = "IntegratorIssueGroup")
public class IssueGroup extends AbstractModel<Integer> implements Named, PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, length = 32)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeType codeType;
    @Column(length = 64, nullable = false)
    private String issueCode;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$util$CodeType;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public IssueGroup() {
        this.id = null;
        this.name = null;
        this.codeType = null;
        this.issueCode = null;
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public String getName() {
        return pcGetname(this);
    }
    
    public void setName(final String name) {
        pcSetname(this, MiscUtils.validateAndNormaliseUserName(name));
    }
    
    public CodeType getCodeType() {
        return pcGetcodeType(this);
    }
    
    public void setCodeType(final CodeType codeType) {
        pcSetcodeType(this, codeType);
    }
    
    public String getIssueCode() {
        return pcGetissueCode(this);
    }
    
    public void setIssueCode(final String issueCode) {
        pcSetissueCode(this, issueCode);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -6454467711710743377L;
        IssueGroup.pcFieldNames = new String[] { "codeType", "id", "issueCode", "name" };
        IssueGroup.pcFieldTypes = new Class[] { (IssueGroup.class$Lca$openosp$openo$caisi_integrator$util$CodeType != null) ? IssueGroup.class$Lca$openosp$openo$caisi_integrator$util$CodeType : (IssueGroup.class$Lca$openosp$openo$caisi_integrator$util$CodeType = class$("ca.openosp.openo.caisi_integrator.util.CodeType")), (IssueGroup.class$Ljava$lang$Integer != null) ? IssueGroup.class$Ljava$lang$Integer : (IssueGroup.class$Ljava$lang$Integer = class$("java.lang.Integer")), (IssueGroup.class$Ljava$lang$String != null) ? IssueGroup.class$Ljava$lang$String : (IssueGroup.class$Ljava$lang$String = class$("java.lang.String")), (IssueGroup.class$Ljava$lang$String != null) ? IssueGroup.class$Ljava$lang$String : (IssueGroup.class$Ljava$lang$String = class$("java.lang.String")) };
        IssueGroup.pcFieldFlags = new byte[] { 26, 26, 26, 26 };
        PCRegistry.register((IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup != null) ? IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup : (IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup = class$("ca.openosp.openo.caisi_integrator.dao.IssueGroup")), IssueGroup.pcFieldNames, IssueGroup.pcFieldTypes, IssueGroup.pcFieldFlags, IssueGroup.pcPCSuperclass, "IssueGroup", (PersistenceCapable)new IssueGroup());
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
        this.codeType = null;
        this.id = null;
        this.issueCode = null;
        this.name = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final IssueGroup issueGroup = new IssueGroup();
        if (b) {
            issueGroup.pcClearFields();
        }
        issueGroup.pcStateManager = pcStateManager;
        issueGroup.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)issueGroup;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final IssueGroup issueGroup = new IssueGroup();
        if (b) {
            issueGroup.pcClearFields();
        }
        issueGroup.pcStateManager = pcStateManager;
        return (PersistenceCapable)issueGroup;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 4;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - IssueGroup.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.codeType = (CodeType)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.issueCode = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
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
        final int n2 = n - IssueGroup.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.codeType);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 2: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.issueCode);
                return;
            }
            case 3: {
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
    
    protected void pcCopyField(final IssueGroup issueGroup, final int n) {
        final int n2 = n - IssueGroup.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.codeType = issueGroup.codeType;
                return;
            }
            case 1: {
                this.id = issueGroup.id;
                return;
            }
            case 2: {
                this.issueCode = issueGroup.issueCode;
                return;
            }
            case 3: {
                this.name = issueGroup.name;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final IssueGroup issueGroup = (IssueGroup)o;
        if (issueGroup.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(issueGroup, array[i]);
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
        fieldConsumer.storeObjectField(1 + IssueGroup.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup != null) ? IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup : (IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup = class$("ca.openosp.openo.caisi_integrator.dao.IssueGroup")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup != null) ? IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup : (IssueGroup.class$Lca$openosp$openo$caisi_integrator$dao$IssueGroup = class$("ca.openosp.openo.caisi_integrator.dao.IssueGroup")), this.id);
    }
    
    private static final CodeType pcGetcodeType(final IssueGroup issueGroup) {
        if (issueGroup.pcStateManager == null) {
            return issueGroup.codeType;
        }
        issueGroup.pcStateManager.accessingField(IssueGroup.pcInheritedFieldCount + 0);
        return issueGroup.codeType;
    }
    
    private static final void pcSetcodeType(final IssueGroup issueGroup, final CodeType codeType) {
        if (issueGroup.pcStateManager == null) {
            issueGroup.codeType = codeType;
            return;
        }
        issueGroup.pcStateManager.settingObjectField((PersistenceCapable)issueGroup, IssueGroup.pcInheritedFieldCount + 0, (Object)issueGroup.codeType, (Object)codeType, 0);
    }
    
    private static final Integer pcGetid(final IssueGroup issueGroup) {
        if (issueGroup.pcStateManager == null) {
            return issueGroup.id;
        }
        issueGroup.pcStateManager.accessingField(IssueGroup.pcInheritedFieldCount + 1);
        return issueGroup.id;
    }
    
    private static final void pcSetid(final IssueGroup issueGroup, final Integer id) {
        if (issueGroup.pcStateManager == null) {
            issueGroup.id = id;
            return;
        }
        issueGroup.pcStateManager.settingObjectField((PersistenceCapable)issueGroup, IssueGroup.pcInheritedFieldCount + 1, (Object)issueGroup.id, (Object)id, 0);
    }
    
    private static final String pcGetissueCode(final IssueGroup issueGroup) {
        if (issueGroup.pcStateManager == null) {
            return issueGroup.issueCode;
        }
        issueGroup.pcStateManager.accessingField(IssueGroup.pcInheritedFieldCount + 2);
        return issueGroup.issueCode;
    }
    
    private static final void pcSetissueCode(final IssueGroup issueGroup, final String issueCode) {
        if (issueGroup.pcStateManager == null) {
            issueGroup.issueCode = issueCode;
            return;
        }
        issueGroup.pcStateManager.settingStringField((PersistenceCapable)issueGroup, IssueGroup.pcInheritedFieldCount + 2, issueGroup.issueCode, issueCode, 0);
    }
    
    private static final String pcGetname(final IssueGroup issueGroup) {
        if (issueGroup.pcStateManager == null) {
            return issueGroup.name;
        }
        issueGroup.pcStateManager.accessingField(IssueGroup.pcInheritedFieldCount + 3);
        return issueGroup.name;
    }
    
    private static final void pcSetname(final IssueGroup issueGroup, final String name) {
        if (issueGroup.pcStateManager == null) {
            issueGroup.name = name;
            return;
        }
        issueGroup.pcStateManager.settingStringField((PersistenceCapable)issueGroup, IssueGroup.pcInheritedFieldCount + 3, issueGroup.name, name, 0);
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
            if (this.id != null) {
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
