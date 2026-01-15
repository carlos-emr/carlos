package ca.openosp.openo.caisi_integrator.dao;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.openjpa.util.LongId;
import org.apache.openjpa.enhance.FieldConsumer;
import org.apache.openjpa.util.InternalException;
import org.apache.openjpa.enhance.FieldSupplier;
import org.apache.openjpa.enhance.RedefinitionHelper;
import org.apache.openjpa.enhance.PersistenceCapable;
import org.apache.openjpa.enhance.PCRegistry;
import javax.persistence.PreUpdate;
import javax.persistence.PreRemove;
import org.apache.openjpa.enhance.StateManager;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import org.apache.openjpa.persistence.DataCache;
import javax.persistence.Entity;

@Entity
@DataCache(enabled = false)
public class EventLog extends AbstractModel<Long> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    @Index
    private Calendar date;
    @Column(length = 255)
    @Index
    private String source;
    @Column(nullable = false, length = 255)
    @Index
    private String action;
    @Column(length = 255)
    private String parameters;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$GregorianCalendar;
    static /* synthetic */ Class class$Ljava$lang$Long;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$EventLog;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public EventLog() {
        this.id = null;
        this.date = new GregorianCalendar();
        this.source = null;
        this.action = null;
        this.parameters = null;
    }
    
    @Override
    public Long getId() {
        return pcGetid(this);
    }
    
    public Calendar getDate() {
        return pcGetdate(this);
    }
    
    public void setDate(final Calendar date) {
        pcSetdate(this, date);
    }
    
    public String getSource() {
        return pcGetsource(this);
    }
    
    public void setSource(final String source) {
        pcSetsource(this, source);
    }
    
    public String getAction() {
        return pcGetaction(this);
    }
    
    public void setAction(final String action) {
        pcSetaction(this, action);
    }
    
    public String getParameters() {
        return pcGetparameters(this);
    }
    
    public void setParameters(final String parameters) {
        pcSetparameters(this, parameters);
    }
    
    @PreRemove
    protected void jpaPreventDelete() {
        throw new UnsupportedOperationException("Remove is not allowed for this type of item.");
    }
    
    @PreUpdate
    protected void jpaPreventUpdate() {
        throw new UnsupportedOperationException("Update is not allowed for this type of item.");
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 5163363772754285468L;
        EventLog.pcFieldNames = new String[] { "action", "date", "id", "parameters", "source" };
        EventLog.pcFieldTypes = new Class[] { (EventLog.class$Ljava$lang$String != null) ? EventLog.class$Ljava$lang$String : (EventLog.class$Ljava$lang$String = class$("java.lang.String")), (EventLog.class$Ljava$util$GregorianCalendar != null) ? EventLog.class$Ljava$util$GregorianCalendar : (EventLog.class$Ljava$util$GregorianCalendar = class$("java.util.GregorianCalendar")), (EventLog.class$Ljava$lang$Long != null) ? EventLog.class$Ljava$lang$Long : (EventLog.class$Ljava$lang$Long = class$("java.lang.Long")), (EventLog.class$Ljava$lang$String != null) ? EventLog.class$Ljava$lang$String : (EventLog.class$Ljava$lang$String = class$("java.lang.String")), (EventLog.class$Ljava$lang$String != null) ? EventLog.class$Ljava$lang$String : (EventLog.class$Ljava$lang$String = class$("java.lang.String")) };
        EventLog.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26 };
        PCRegistry.register((EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog != null) ? EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog : (EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog = class$("ca.openosp.openo.caisi_integrator.dao.EventLog")), EventLog.pcFieldNames, EventLog.pcFieldTypes, EventLog.pcFieldFlags, EventLog.pcPCSuperclass, "EventLog", (PersistenceCapable)new EventLog());
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
        this.action = null;
        this.date = null;
        this.id = null;
        this.parameters = null;
        this.source = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final EventLog eventLog = new EventLog();
        if (b) {
            eventLog.pcClearFields();
        }
        eventLog.pcStateManager = pcStateManager;
        eventLog.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)eventLog;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final EventLog eventLog = new EventLog();
        if (b) {
            eventLog.pcClearFields();
        }
        eventLog.pcStateManager = pcStateManager;
        return (PersistenceCapable)eventLog;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 5;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - EventLog.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.action = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.date = (Calendar)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.id = (Long)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.parameters = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.source = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - EventLog.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.action);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.date);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.parameters);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.source);
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
    
    protected void pcCopyField(final EventLog eventLog, final int n) {
        final int n2 = n - EventLog.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.action = eventLog.action;
                return;
            }
            case 1: {
                this.date = eventLog.date;
                return;
            }
            case 2: {
                this.id = eventLog.id;
                return;
            }
            case 3: {
                this.parameters = eventLog.parameters;
                return;
            }
            case 4: {
                this.source = eventLog.source;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final EventLog eventLog = (EventLog)o;
        if (eventLog.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(eventLog, array[i]);
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
        fieldConsumer.storeObjectField(2 + EventLog.pcInheritedFieldCount, (Object)Long.valueOf(((LongId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Long.valueOf(((LongId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new LongId((EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog != null) ? EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog : (EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog = class$("ca.openosp.openo.caisi_integrator.dao.EventLog")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new LongId((EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog != null) ? EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog : (EventLog.class$Lca$openosp$openo$caisi_integrator$dao$EventLog = class$("ca.openosp.openo.caisi_integrator.dao.EventLog")), this.id);
    }
    
    private static final String pcGetaction(final EventLog eventLog) {
        if (eventLog.pcStateManager == null) {
            return eventLog.action;
        }
        eventLog.pcStateManager.accessingField(EventLog.pcInheritedFieldCount + 0);
        return eventLog.action;
    }
    
    private static final void pcSetaction(final EventLog eventLog, final String action) {
        if (eventLog.pcStateManager == null) {
            eventLog.action = action;
            return;
        }
        eventLog.pcStateManager.settingStringField((PersistenceCapable)eventLog, EventLog.pcInheritedFieldCount + 0, eventLog.action, action, 0);
    }
    
    private static final Calendar pcGetdate(final EventLog eventLog) {
        if (eventLog.pcStateManager == null) {
            return eventLog.date;
        }
        eventLog.pcStateManager.accessingField(EventLog.pcInheritedFieldCount + 1);
        return eventLog.date;
    }
    
    private static final void pcSetdate(final EventLog eventLog, final Calendar date) {
        if (eventLog.pcStateManager == null) {
            eventLog.date = date;
            return;
        }
        eventLog.pcStateManager.settingObjectField((PersistenceCapable)eventLog, EventLog.pcInheritedFieldCount + 1, (Object)eventLog.date, (Object)date, 0);
    }
    
    private static final Long pcGetid(final EventLog eventLog) {
        if (eventLog.pcStateManager == null) {
            return eventLog.id;
        }
        eventLog.pcStateManager.accessingField(EventLog.pcInheritedFieldCount + 2);
        return eventLog.id;
    }
    
    private static final void pcSetid(final EventLog eventLog, final Long id) {
        if (eventLog.pcStateManager == null) {
            eventLog.id = id;
            return;
        }
        eventLog.pcStateManager.settingObjectField((PersistenceCapable)eventLog, EventLog.pcInheritedFieldCount + 2, (Object)eventLog.id, (Object)id, 0);
    }
    
    private static final String pcGetparameters(final EventLog eventLog) {
        if (eventLog.pcStateManager == null) {
            return eventLog.parameters;
        }
        eventLog.pcStateManager.accessingField(EventLog.pcInheritedFieldCount + 3);
        return eventLog.parameters;
    }
    
    private static final void pcSetparameters(final EventLog eventLog, final String parameters) {
        if (eventLog.pcStateManager == null) {
            eventLog.parameters = parameters;
            return;
        }
        eventLog.pcStateManager.settingStringField((PersistenceCapable)eventLog, EventLog.pcInheritedFieldCount + 3, eventLog.parameters, parameters, 0);
    }
    
    private static final String pcGetsource(final EventLog eventLog) {
        if (eventLog.pcStateManager == null) {
            return eventLog.source;
        }
        eventLog.pcStateManager.accessingField(EventLog.pcInheritedFieldCount + 4);
        return eventLog.source;
    }
    
    private static final void pcSetsource(final EventLog eventLog, final String source) {
        if (eventLog.pcStateManager == null) {
            eventLog.source = source;
            return;
        }
        eventLog.pcStateManager.settingStringField((PersistenceCapable)eventLog, EventLog.pcInheritedFieldCount + 4, eventLog.source, source, 0);
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
    
    public enum ActionPrefix
    {
        DATA, 
        LOGIC, 
        PERFORMANCE;
    }
    
    public enum DataActionValue
    {
        READ, 
        WRITE, 
        DELETE, 
        SEARCH_RESULT;
    }
}
