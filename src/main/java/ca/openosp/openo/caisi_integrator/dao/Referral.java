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
import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.StateManager;
import javax.persistence.TemporalType;
import javax.persistence.Temporal;
import java.util.Date;
import org.apache.openjpa.persistence.jdbc.Index;
import javax.persistence.Column;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Entity;

@Entity
public class Referral extends AbstractModel<Integer> implements PersistenceCapable
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    @Index
    private Integer sourceIntegratorFacilityId;
    @Column(nullable = false)
    @Index
    private Integer sourceCaisiDemographicId;
    @Column(nullable = false, length = 32)
    private String sourceCaisiProviderId;
    @Column(nullable = false)
    @Index
    private Integer destinationIntegratorFacilityId;
    @Column(nullable = false)
    @Index
    private Integer destinationCaisiProgramId;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date referralDate;
    @Column(columnDefinition = "text")
    private String reasonForReferral;
    @Column(columnDefinition = "text")
    private String presentingProblem;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$Integer;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Ljava$util$Date;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$Referral;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public Referral() {
        this.id = null;
        this.sourceIntegratorFacilityId = null;
        this.sourceCaisiDemographicId = null;
        this.sourceCaisiProviderId = null;
        this.destinationIntegratorFacilityId = null;
        this.destinationCaisiProgramId = null;
        this.referralDate = null;
        this.reasonForReferral = null;
        this.presentingProblem = null;
    }
    
    @Override
    public Integer getId() {
        return pcGetid(this);
    }
    
    public Integer getReferralId() {
        return pcGetid(this);
    }
    
    public void setReferralId(final Integer id) {
        throw new UnsupportedOperationException();
    }
    
    public Integer getSourceIntegratorFacilityId() {
        return pcGetsourceIntegratorFacilityId(this);
    }
    
    public void setSourceIntegratorFacilityId(final Integer sourceIntegratorFacilityId) {
        pcSetsourceIntegratorFacilityId(this, sourceIntegratorFacilityId);
    }
    
    public Integer getSourceCaisiDemographicId() {
        return pcGetsourceCaisiDemographicId(this);
    }
    
    public void setSourceCaisiDemographicId(final Integer sourceCaisiDemographicId) {
        pcSetsourceCaisiDemographicId(this, sourceCaisiDemographicId);
    }
    
    public String getSourceCaisiProviderId() {
        return pcGetsourceCaisiProviderId(this);
    }
    
    public void setSourceCaisiProviderId(final String sourceCaisiProviderId) {
        pcSetsourceCaisiProviderId(this, StringUtils.trimToNull(sourceCaisiProviderId));
    }
    
    public Integer getDestinationIntegratorFacilityId() {
        return pcGetdestinationIntegratorFacilityId(this);
    }
    
    public void setDestinationIntegratorFacilityId(final Integer destinationIntegratorFacilityId) {
        pcSetdestinationIntegratorFacilityId(this, destinationIntegratorFacilityId);
    }
    
    public Integer getDestinationCaisiProgramId() {
        return pcGetdestinationCaisiProgramId(this);
    }
    
    public void setDestinationCaisiProgramId(final Integer destinationCaisiProgramId) {
        pcSetdestinationCaisiProgramId(this, destinationCaisiProgramId);
    }
    
    public Date getReferralDate() {
        return pcGetreferralDate(this);
    }
    
    public void setReferralDate(final Date referralDate) {
        pcSetreferralDate(this, referralDate);
    }
    
    public String getReasonForReferral() {
        return pcGetreasonForReferral(this);
    }
    
    public void setReasonForReferral(final String reasonForReferral) {
        pcSetreasonForReferral(this, StringUtils.trimToNull(reasonForReferral));
    }
    
    public String getPresentingProblem() {
        return pcGetpresentingProblem(this);
    }
    
    public void setPresentingProblem(final String presentingProblem) {
        pcSetpresentingProblem(this, StringUtils.trimToNull(presentingProblem));
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = -3840584601644845846L;
        Referral.pcFieldNames = new String[] { "destinationCaisiProgramId", "destinationIntegratorFacilityId", "id", "presentingProblem", "reasonForReferral", "referralDate", "sourceCaisiDemographicId", "sourceCaisiProviderId", "sourceIntegratorFacilityId" };
        Referral.pcFieldTypes = new Class[] { (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$String != null) ? Referral.class$Ljava$lang$String : (Referral.class$Ljava$lang$String = class$("java.lang.String")), (Referral.class$Ljava$lang$String != null) ? Referral.class$Ljava$lang$String : (Referral.class$Ljava$lang$String = class$("java.lang.String")), (Referral.class$Ljava$util$Date != null) ? Referral.class$Ljava$util$Date : (Referral.class$Ljava$util$Date = class$("java.util.Date")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")), (Referral.class$Ljava$lang$String != null) ? Referral.class$Ljava$lang$String : (Referral.class$Ljava$lang$String = class$("java.lang.String")), (Referral.class$Ljava$lang$Integer != null) ? Referral.class$Ljava$lang$Integer : (Referral.class$Ljava$lang$Integer = class$("java.lang.Integer")) };
        Referral.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral != null) ? Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral : (Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral = class$("ca.openosp.openo.caisi_integrator.dao.Referral")), Referral.pcFieldNames, Referral.pcFieldTypes, Referral.pcFieldFlags, Referral.pcPCSuperclass, "Referral", (PersistenceCapable)new Referral());
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
        this.destinationCaisiProgramId = null;
        this.destinationIntegratorFacilityId = null;
        this.id = null;
        this.presentingProblem = null;
        this.reasonForReferral = null;
        this.referralDate = null;
        this.sourceCaisiDemographicId = null;
        this.sourceCaisiProviderId = null;
        this.sourceIntegratorFacilityId = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final Referral referral = new Referral();
        if (b) {
            referral.pcClearFields();
        }
        referral.pcStateManager = pcStateManager;
        referral.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)referral;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final Referral referral = new Referral();
        if (b) {
            referral.pcClearFields();
        }
        referral.pcStateManager = pcStateManager;
        return (PersistenceCapable)referral;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 9;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - Referral.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.destinationCaisiProgramId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.destinationIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.id = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.presentingProblem = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.reasonForReferral = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.referralDate = (Date)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.sourceCaisiDemographicId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.sourceCaisiProviderId = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.sourceIntegratorFacilityId = (Integer)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
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
        final int n2 = n - Referral.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.destinationCaisiProgramId);
                return;
            }
            case 1: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.destinationIntegratorFacilityId);
                return;
            }
            case 2: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.id);
                return;
            }
            case 3: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.presentingProblem);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.reasonForReferral);
                return;
            }
            case 5: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.referralDate);
                return;
            }
            case 6: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sourceCaisiDemographicId);
                return;
            }
            case 7: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.sourceCaisiProviderId);
                return;
            }
            case 8: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.sourceIntegratorFacilityId);
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
    
    protected void pcCopyField(final Referral referral, final int n) {
        final int n2 = n - Referral.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.destinationCaisiProgramId = referral.destinationCaisiProgramId;
                return;
            }
            case 1: {
                this.destinationIntegratorFacilityId = referral.destinationIntegratorFacilityId;
                return;
            }
            case 2: {
                this.id = referral.id;
                return;
            }
            case 3: {
                this.presentingProblem = referral.presentingProblem;
                return;
            }
            case 4: {
                this.reasonForReferral = referral.reasonForReferral;
                return;
            }
            case 5: {
                this.referralDate = referral.referralDate;
                return;
            }
            case 6: {
                this.sourceCaisiDemographicId = referral.sourceCaisiDemographicId;
                return;
            }
            case 7: {
                this.sourceCaisiProviderId = referral.sourceCaisiProviderId;
                return;
            }
            case 8: {
                this.sourceIntegratorFacilityId = referral.sourceIntegratorFacilityId;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final Referral referral = (Referral)o;
        if (referral.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(referral, array[i]);
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
        fieldConsumer.storeObjectField(2 + Referral.pcInheritedFieldCount, (Object)Integer.valueOf(((IntId)o).getId()));
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.id = Integer.valueOf(((IntId)o).getId());
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        return new IntId((Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral != null) ? Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral : (Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral = class$("ca.openosp.openo.caisi_integrator.dao.Referral")), (String)o);
    }
    
    public Object pcNewObjectIdInstance() {
        return new IntId((Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral != null) ? Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral : (Referral.class$Lca$openosp$openo$caisi_integrator$dao$Referral = class$("ca.openosp.openo.caisi_integrator.dao.Referral")), this.id);
    }
    
    private static final Integer pcGetdestinationCaisiProgramId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.destinationCaisiProgramId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 0);
        return referral.destinationCaisiProgramId;
    }
    
    private static final void pcSetdestinationCaisiProgramId(final Referral referral, final Integer destinationCaisiProgramId) {
        if (referral.pcStateManager == null) {
            referral.destinationCaisiProgramId = destinationCaisiProgramId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 0, (Object)referral.destinationCaisiProgramId, (Object)destinationCaisiProgramId, 0);
    }
    
    private static final Integer pcGetdestinationIntegratorFacilityId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.destinationIntegratorFacilityId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 1);
        return referral.destinationIntegratorFacilityId;
    }
    
    private static final void pcSetdestinationIntegratorFacilityId(final Referral referral, final Integer destinationIntegratorFacilityId) {
        if (referral.pcStateManager == null) {
            referral.destinationIntegratorFacilityId = destinationIntegratorFacilityId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 1, (Object)referral.destinationIntegratorFacilityId, (Object)destinationIntegratorFacilityId, 0);
    }
    
    private static final Integer pcGetid(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.id;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 2);
        return referral.id;
    }
    
    private static final void pcSetid(final Referral referral, final Integer id) {
        if (referral.pcStateManager == null) {
            referral.id = id;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 2, (Object)referral.id, (Object)id, 0);
    }
    
    private static final String pcGetpresentingProblem(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.presentingProblem;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 3);
        return referral.presentingProblem;
    }
    
    private static final void pcSetpresentingProblem(final Referral referral, final String presentingProblem) {
        if (referral.pcStateManager == null) {
            referral.presentingProblem = presentingProblem;
            return;
        }
        referral.pcStateManager.settingStringField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 3, referral.presentingProblem, presentingProblem, 0);
    }
    
    private static final String pcGetreasonForReferral(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.reasonForReferral;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 4);
        return referral.reasonForReferral;
    }
    
    private static final void pcSetreasonForReferral(final Referral referral, final String reasonForReferral) {
        if (referral.pcStateManager == null) {
            referral.reasonForReferral = reasonForReferral;
            return;
        }
        referral.pcStateManager.settingStringField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 4, referral.reasonForReferral, reasonForReferral, 0);
    }
    
    private static final Date pcGetreferralDate(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.referralDate;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 5);
        return referral.referralDate;
    }
    
    private static final void pcSetreferralDate(final Referral referral, final Date referralDate) {
        if (referral.pcStateManager == null) {
            referral.referralDate = referralDate;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 5, (Object)referral.referralDate, (Object)referralDate, 0);
    }
    
    private static final Integer pcGetsourceCaisiDemographicId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.sourceCaisiDemographicId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 6);
        return referral.sourceCaisiDemographicId;
    }
    
    private static final void pcSetsourceCaisiDemographicId(final Referral referral, final Integer sourceCaisiDemographicId) {
        if (referral.pcStateManager == null) {
            referral.sourceCaisiDemographicId = sourceCaisiDemographicId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 6, (Object)referral.sourceCaisiDemographicId, (Object)sourceCaisiDemographicId, 0);
    }
    
    private static final String pcGetsourceCaisiProviderId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.sourceCaisiProviderId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 7);
        return referral.sourceCaisiProviderId;
    }
    
    private static final void pcSetsourceCaisiProviderId(final Referral referral, final String sourceCaisiProviderId) {
        if (referral.pcStateManager == null) {
            referral.sourceCaisiProviderId = sourceCaisiProviderId;
            return;
        }
        referral.pcStateManager.settingStringField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 7, referral.sourceCaisiProviderId, sourceCaisiProviderId, 0);
    }
    
    private static final Integer pcGetsourceIntegratorFacilityId(final Referral referral) {
        if (referral.pcStateManager == null) {
            return referral.sourceIntegratorFacilityId;
        }
        referral.pcStateManager.accessingField(Referral.pcInheritedFieldCount + 8);
        return referral.sourceIntegratorFacilityId;
    }
    
    private static final void pcSetsourceIntegratorFacilityId(final Referral referral, final Integer sourceIntegratorFacilityId) {
        if (referral.pcStateManager == null) {
            referral.sourceIntegratorFacilityId = sourceIntegratorFacilityId;
            return;
        }
        referral.pcStateManager.settingObjectField((PersistenceCapable)referral, Referral.pcInheritedFieldCount + 8, (Object)referral.sourceIntegratorFacilityId, (Object)sourceIntegratorFacilityId, 0);
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
