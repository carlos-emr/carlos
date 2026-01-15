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
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;

@Entity
public class CachedProgram extends AbstractModel<FacilityIdIntegerCompositePk> implements Comparable<CachedProgram>, PersistenceCapable
{
    @EmbeddedId
    private FacilityIdIntegerCompositePk facilityIdIntegerCompositePk;
    private String name;
    private String description;
    @Column(length = 32)
    private String type;
    @Column(length = 32)
    private String status;
    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private CachedDemographic.Gender gender;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean firstNation;
    @Column(nullable = false)
    private int minAge;
    @Column(nullable = false)
    private int maxAge;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean bedProgramAffiliated;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean alcohol;
    @Column(length = 32)
    private String abstinenceSupport;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean physicalHealth;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean mentalHealth;
    @Column(nullable = false, columnDefinition = "tinyint(1)")
    private boolean housing;
    private String address;
    private String phone;
    private String fax;
    private String url;
    private String email;
    private String emergencyNumber;
    private static int pcInheritedFieldCount;
    private static String[] pcFieldNames;
    private static Class[] pcFieldTypes;
    private static byte[] pcFieldFlags;
    private static Class pcPCSuperclass;
    protected transient StateManager pcStateManager;
    static /* synthetic */ Class class$Ljava$lang$String;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender;
    static /* synthetic */ Class class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram;
    private transient Object pcDetachedState;
    private static final long serialVersionUID;
    
    public CachedProgram() {
        this.name = null;
        this.description = null;
        this.type = null;
        this.status = null;
        this.gender = null;
        this.firstNation = false;
        this.minAge = 0;
        this.maxAge = 200;
        this.bedProgramAffiliated = false;
        this.alcohol = false;
        this.abstinenceSupport = null;
        this.physicalHealth = false;
        this.mentalHealth = false;
        this.housing = false;
        this.address = null;
        this.phone = null;
        this.fax = null;
        this.url = null;
        this.email = null;
        this.emergencyNumber = null;
    }
    
    public FacilityIdIntegerCompositePk getFacilityIdIntegerCompositePk() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }
    
    public void setFacilityIdIntegerCompositePk(final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        pcSetfacilityIdIntegerCompositePk(this, facilityIdIntegerCompositePk);
    }
    
    public String getName() {
        return pcGetname(this);
    }
    
    public void setName(final String name) {
        pcSetname(this, StringUtils.trimToNull(name));
    }
    
    public String getDescription() {
        return pcGetdescription(this);
    }
    
    public void setDescription(final String description) {
        pcSetdescription(this, StringUtils.trimToNull(description));
    }
    
    public String getType() {
        return pcGettype(this);
    }
    
    public void setType(final String type) {
        pcSettype(this, StringUtils.trimToNull(type));
    }
    
    public String getStatus() {
        return pcGetstatus(this);
    }
    
    public void setStatus(final String status) {
        pcSetstatus(this, StringUtils.trimToNull(status));
    }
    
    public boolean isBedProgramAffiliated() {
        return pcGetbedProgramAffiliated(this);
    }
    
    public void setBedProgramAffiliated(final boolean bedProgramAffiliated) {
        pcSetbedProgramAffiliated(this, bedProgramAffiliated);
    }
    
    public boolean isAlcohol() {
        return pcGetalcohol(this);
    }
    
    public void setAlcohol(final boolean alcohol) {
        pcSetalcohol(this, alcohol);
    }
    
    public String getAbstinenceSupport() {
        return pcGetabstinenceSupport(this);
    }
    
    public void setAbstinenceSupport(final String abstinenceSupport) {
        pcSetabstinenceSupport(this, StringUtils.trimToNull(abstinenceSupport));
    }
    
    public boolean isPhysicalHealth() {
        return pcGetphysicalHealth(this);
    }
    
    public void setPhysicalHealth(final boolean physicalHealth) {
        pcSetphysicalHealth(this, physicalHealth);
    }
    
    public boolean isMentalHealth() {
        return pcGetmentalHealth(this);
    }
    
    public void setMentalHealth(final boolean mentalHealth) {
        pcSetmentalHealth(this, mentalHealth);
    }
    
    public boolean isHousing() {
        return pcGethousing(this);
    }
    
    public void setHousing(final boolean housing) {
        pcSethousing(this, housing);
    }
    
    public CachedDemographic.Gender getGender() {
        return pcGetgender(this);
    }
    
    public void setGender(final CachedDemographic.Gender gender) {
        pcSetgender(this, gender);
    }
    
    public boolean isFirstNation() {
        return pcGetfirstNation(this);
    }
    
    public void setFirstNation(final boolean firstNation) {
        pcSetfirstNation(this, firstNation);
    }
    
    public int getMinAge() {
        return pcGetminAge(this);
    }
    
    public void setMinAge(final int minAge) {
        pcSetminAge(this, minAge);
    }
    
    public int getMaxAge() {
        return pcGetmaxAge(this);
    }
    
    public void setMaxAge(final int maxAge) {
        pcSetmaxAge(this, maxAge);
    }
    
    public String getAddress() {
        return pcGetaddress(this);
    }
    
    public void setAddress(final String address) {
        pcSetaddress(this, address);
    }
    
    public String getPhone() {
        return pcGetphone(this);
    }
    
    public void setPhone(final String phone) {
        pcSetphone(this, phone);
    }
    
    public String getFax() {
        return pcGetfax(this);
    }
    
    public void setFax(final String fax) {
        pcSetfax(this, fax);
    }
    
    public String getUrl() {
        return pcGeturl(this);
    }
    
    public void setUrl(final String url) {
        pcSeturl(this, url);
    }
    
    public String getEmail() {
        return pcGetemail(this);
    }
    
    public void setEmail(final String email) {
        pcSetemail(this, email);
    }
    
    public String getEmergencyNumber() {
        return pcGetemergencyNumber(this);
    }
    
    public void setEmergencyNumber(final String emergencyNumber) {
        pcSetemergencyNumber(this, emergencyNumber);
    }
    
    @Override
    public int compareTo(final CachedProgram o) {
        return pcGetfacilityIdIntegerCompositePk(this).getCaisiItemId() - pcGetfacilityIdIntegerCompositePk(o).getCaisiItemId();
    }
    
    @Override
    public FacilityIdIntegerCompositePk getId() {
        return pcGetfacilityIdIntegerCompositePk(this);
    }
    
    public int pcGetEnhancementContractVersion() {
        return 2;
    }
    
    static {
        serialVersionUID = 892878660194000526L;
        CachedProgram.pcFieldNames = new String[] { "abstinenceSupport", "address", "alcohol", "bedProgramAffiliated", "description", "email", "emergencyNumber", "facilityIdIntegerCompositePk", "fax", "firstNation", "gender", "housing", "maxAge", "mentalHealth", "minAge", "name", "phone", "physicalHealth", "status", "type", "url" };
        CachedProgram.pcFieldTypes = new Class[] { (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, Boolean.TYPE, (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$FacilityIdIntegerCompositePk = class$("ca.openosp.openo.caisi_integrator.dao.FacilityIdIntegerCompositePk")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedDemographic$Gender = class$("ca.openosp.openo.caisi_integrator.dao.CachedDemographic$Gender")), Boolean.TYPE, Integer.TYPE, Boolean.TYPE, Integer.TYPE, (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), Boolean.TYPE, (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")), (CachedProgram.class$Ljava$lang$String != null) ? CachedProgram.class$Ljava$lang$String : (CachedProgram.class$Ljava$lang$String = class$("java.lang.String")) };
        CachedProgram.pcFieldFlags = new byte[] { 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26 };
        PCRegistry.register((CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram = class$("ca.openosp.openo.caisi_integrator.dao.CachedProgram")), CachedProgram.pcFieldNames, CachedProgram.pcFieldTypes, CachedProgram.pcFieldFlags, CachedProgram.pcPCSuperclass, "CachedProgram", (PersistenceCapable)new CachedProgram());
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
        this.abstinenceSupport = null;
        this.address = null;
        this.alcohol = false;
        this.bedProgramAffiliated = false;
        this.description = null;
        this.email = null;
        this.emergencyNumber = null;
        this.facilityIdIntegerCompositePk = null;
        this.fax = null;
        this.firstNation = false;
        this.gender = null;
        this.housing = false;
        this.maxAge = 0;
        this.mentalHealth = false;
        this.minAge = 0;
        this.name = null;
        this.phone = null;
        this.physicalHealth = false;
        this.status = null;
        this.type = null;
        this.url = null;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final Object o, final boolean b) {
        final CachedProgram cachedProgram = new CachedProgram();
        if (b) {
            cachedProgram.pcClearFields();
        }
        cachedProgram.pcStateManager = pcStateManager;
        cachedProgram.pcCopyKeyFieldsFromObjectId(o);
        return (PersistenceCapable)cachedProgram;
    }
    
    public PersistenceCapable pcNewInstance(final StateManager pcStateManager, final boolean b) {
        final CachedProgram cachedProgram = new CachedProgram();
        if (b) {
            cachedProgram.pcClearFields();
        }
        cachedProgram.pcStateManager = pcStateManager;
        return (PersistenceCapable)cachedProgram;
    }
    
    protected static int pcGetManagedFieldCount() {
        return 21;
    }
    
    public void pcReplaceField(final int n) {
        final int n2 = n - CachedProgram.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.abstinenceSupport = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 1: {
                this.address = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 2: {
                this.alcohol = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 3: {
                this.bedProgramAffiliated = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 4: {
                this.description = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 5: {
                this.email = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 6: {
                this.emergencyNumber = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 7: {
                this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 8: {
                this.fax = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 9: {
                this.firstNation = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 10: {
                this.gender = (CachedDemographic.Gender)this.pcStateManager.replaceObjectField((PersistenceCapable)this, n);
                return;
            }
            case 11: {
                this.housing = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 12: {
                this.maxAge = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 13: {
                this.mentalHealth = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 14: {
                this.minAge = this.pcStateManager.replaceIntField((PersistenceCapable)this, n);
                return;
            }
            case 15: {
                this.name = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 16: {
                this.phone = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 17: {
                this.physicalHealth = this.pcStateManager.replaceBooleanField((PersistenceCapable)this, n);
                return;
            }
            case 18: {
                this.status = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 19: {
                this.type = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
                return;
            }
            case 20: {
                this.url = this.pcStateManager.replaceStringField((PersistenceCapable)this, n);
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
        final int n2 = n - CachedProgram.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.abstinenceSupport);
                return;
            }
            case 1: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.address);
                return;
            }
            case 2: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.alcohol);
                return;
            }
            case 3: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.bedProgramAffiliated);
                return;
            }
            case 4: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.description);
                return;
            }
            case 5: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.email);
                return;
            }
            case 6: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.emergencyNumber);
                return;
            }
            case 7: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.facilityIdIntegerCompositePk);
                return;
            }
            case 8: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.fax);
                return;
            }
            case 9: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.firstNation);
                return;
            }
            case 10: {
                this.pcStateManager.providedObjectField((PersistenceCapable)this, n, (Object)this.gender);
                return;
            }
            case 11: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.housing);
                return;
            }
            case 12: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.maxAge);
                return;
            }
            case 13: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.mentalHealth);
                return;
            }
            case 14: {
                this.pcStateManager.providedIntField((PersistenceCapable)this, n, this.minAge);
                return;
            }
            case 15: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.name);
                return;
            }
            case 16: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.phone);
                return;
            }
            case 17: {
                this.pcStateManager.providedBooleanField((PersistenceCapable)this, n, this.physicalHealth);
                return;
            }
            case 18: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.status);
                return;
            }
            case 19: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.type);
                return;
            }
            case 20: {
                this.pcStateManager.providedStringField((PersistenceCapable)this, n, this.url);
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
    
    protected void pcCopyField(final CachedProgram cachedProgram, final int n) {
        final int n2 = n - CachedProgram.pcInheritedFieldCount;
        if (n2 < 0) {
            throw new IllegalArgumentException();
        }
        switch (n2) {
            case 0: {
                this.abstinenceSupport = cachedProgram.abstinenceSupport;
                return;
            }
            case 1: {
                this.address = cachedProgram.address;
                return;
            }
            case 2: {
                this.alcohol = cachedProgram.alcohol;
                return;
            }
            case 3: {
                this.bedProgramAffiliated = cachedProgram.bedProgramAffiliated;
                return;
            }
            case 4: {
                this.description = cachedProgram.description;
                return;
            }
            case 5: {
                this.email = cachedProgram.email;
                return;
            }
            case 6: {
                this.emergencyNumber = cachedProgram.emergencyNumber;
                return;
            }
            case 7: {
                this.facilityIdIntegerCompositePk = cachedProgram.facilityIdIntegerCompositePk;
                return;
            }
            case 8: {
                this.fax = cachedProgram.fax;
                return;
            }
            case 9: {
                this.firstNation = cachedProgram.firstNation;
                return;
            }
            case 10: {
                this.gender = cachedProgram.gender;
                return;
            }
            case 11: {
                this.housing = cachedProgram.housing;
                return;
            }
            case 12: {
                this.maxAge = cachedProgram.maxAge;
                return;
            }
            case 13: {
                this.mentalHealth = cachedProgram.mentalHealth;
                return;
            }
            case 14: {
                this.minAge = cachedProgram.minAge;
                return;
            }
            case 15: {
                this.name = cachedProgram.name;
                return;
            }
            case 16: {
                this.phone = cachedProgram.phone;
                return;
            }
            case 17: {
                this.physicalHealth = cachedProgram.physicalHealth;
                return;
            }
            case 18: {
                this.status = cachedProgram.status;
                return;
            }
            case 19: {
                this.type = cachedProgram.type;
                return;
            }
            case 20: {
                this.url = cachedProgram.url;
                return;
            }
            default: {
                throw new IllegalArgumentException();
            }
        }
    }
    
    public void pcCopyFields(final Object o, final int[] array) {
        final CachedProgram cachedProgram = (CachedProgram)o;
        if (cachedProgram.pcStateManager != this.pcStateManager) {
            throw new IllegalArgumentException();
        }
        if (this.pcStateManager == null) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < array.length; ++i) {
            this.pcCopyField(cachedProgram, array[i]);
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
        fieldConsumer.storeObjectField(7 + CachedProgram.pcInheritedFieldCount, ((ObjectId)o).getId());
    }
    
    public void pcCopyKeyFieldsFromObjectId(final Object o) {
        this.facilityIdIntegerCompositePk = (FacilityIdIntegerCompositePk)((ObjectId)o).getId();
    }
    
    public Object pcNewObjectIdInstance(final Object o) {
        throw new IllegalArgumentException("The id type \"class org.apache.openjpa.util.ObjectId\" specified by persistent type \"class ca.openosp.openo.caisi_integrator.dao.CachedProgram\" does not have a public class org.apache.openjpa.util.ObjectId(String) or class org.apache.openjpa.util.ObjectId(Class, String) constructor.");
    }
    
    public Object pcNewObjectIdInstance() {
        return new ObjectId((CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram != null) ? CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram : (CachedProgram.class$Lca$openosp$openo$caisi_integrator$dao$CachedProgram = class$("ca.openosp.openo.caisi_integrator.dao.CachedProgram")), (Object)this.facilityIdIntegerCompositePk);
    }
    
    private static final String pcGetabstinenceSupport(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.abstinenceSupport;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 0);
        return cachedProgram.abstinenceSupport;
    }
    
    private static final void pcSetabstinenceSupport(final CachedProgram cachedProgram, final String abstinenceSupport) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.abstinenceSupport = abstinenceSupport;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 0, cachedProgram.abstinenceSupport, abstinenceSupport, 0);
    }
    
    private static final String pcGetaddress(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.address;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 1);
        return cachedProgram.address;
    }
    
    private static final void pcSetaddress(final CachedProgram cachedProgram, final String address) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.address = address;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 1, cachedProgram.address, address, 0);
    }
    
    private static final boolean pcGetalcohol(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.alcohol;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 2);
        return cachedProgram.alcohol;
    }
    
    private static final void pcSetalcohol(final CachedProgram cachedProgram, final boolean alcohol) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.alcohol = alcohol;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 2, cachedProgram.alcohol, alcohol, 0);
    }
    
    private static final boolean pcGetbedProgramAffiliated(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.bedProgramAffiliated;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 3);
        return cachedProgram.bedProgramAffiliated;
    }
    
    private static final void pcSetbedProgramAffiliated(final CachedProgram cachedProgram, final boolean bedProgramAffiliated) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.bedProgramAffiliated = bedProgramAffiliated;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 3, cachedProgram.bedProgramAffiliated, bedProgramAffiliated, 0);
    }
    
    private static final String pcGetdescription(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.description;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 4);
        return cachedProgram.description;
    }
    
    private static final void pcSetdescription(final CachedProgram cachedProgram, final String description) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.description = description;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 4, cachedProgram.description, description, 0);
    }
    
    private static final String pcGetemail(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.email;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 5);
        return cachedProgram.email;
    }
    
    private static final void pcSetemail(final CachedProgram cachedProgram, final String email) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.email = email;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 5, cachedProgram.email, email, 0);
    }
    
    private static final String pcGetemergencyNumber(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.emergencyNumber;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 6);
        return cachedProgram.emergencyNumber;
    }
    
    private static final void pcSetemergencyNumber(final CachedProgram cachedProgram, final String emergencyNumber) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.emergencyNumber = emergencyNumber;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 6, cachedProgram.emergencyNumber, emergencyNumber, 0);
    }
    
    private static final FacilityIdIntegerCompositePk pcGetfacilityIdIntegerCompositePk(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.facilityIdIntegerCompositePk;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 7);
        return cachedProgram.facilityIdIntegerCompositePk;
    }
    
    private static final void pcSetfacilityIdIntegerCompositePk(final CachedProgram cachedProgram, final FacilityIdIntegerCompositePk facilityIdIntegerCompositePk) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.facilityIdIntegerCompositePk = facilityIdIntegerCompositePk;
            return;
        }
        cachedProgram.pcStateManager.settingObjectField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 7, (Object)cachedProgram.facilityIdIntegerCompositePk, (Object)facilityIdIntegerCompositePk, 0);
    }
    
    private static final String pcGetfax(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.fax;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 8);
        return cachedProgram.fax;
    }
    
    private static final void pcSetfax(final CachedProgram cachedProgram, final String fax) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.fax = fax;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 8, cachedProgram.fax, fax, 0);
    }
    
    private static final boolean pcGetfirstNation(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.firstNation;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 9);
        return cachedProgram.firstNation;
    }
    
    private static final void pcSetfirstNation(final CachedProgram cachedProgram, final boolean firstNation) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.firstNation = firstNation;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 9, cachedProgram.firstNation, firstNation, 0);
    }
    
    private static final CachedDemographic.Gender pcGetgender(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.gender;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 10);
        return cachedProgram.gender;
    }
    
    private static final void pcSetgender(final CachedProgram cachedProgram, final CachedDemographic.Gender gender) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.gender = gender;
            return;
        }
        cachedProgram.pcStateManager.settingObjectField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 10, (Object)cachedProgram.gender, (Object)gender, 0);
    }
    
    private static final boolean pcGethousing(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.housing;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 11);
        return cachedProgram.housing;
    }
    
    private static final void pcSethousing(final CachedProgram cachedProgram, final boolean housing) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.housing = housing;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 11, cachedProgram.housing, housing, 0);
    }
    
    private static final int pcGetmaxAge(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.maxAge;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 12);
        return cachedProgram.maxAge;
    }
    
    private static final void pcSetmaxAge(final CachedProgram cachedProgram, final int maxAge) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.maxAge = maxAge;
            return;
        }
        cachedProgram.pcStateManager.settingIntField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 12, cachedProgram.maxAge, maxAge, 0);
    }
    
    private static final boolean pcGetmentalHealth(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.mentalHealth;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 13);
        return cachedProgram.mentalHealth;
    }
    
    private static final void pcSetmentalHealth(final CachedProgram cachedProgram, final boolean mentalHealth) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.mentalHealth = mentalHealth;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 13, cachedProgram.mentalHealth, mentalHealth, 0);
    }
    
    private static final int pcGetminAge(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.minAge;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 14);
        return cachedProgram.minAge;
    }
    
    private static final void pcSetminAge(final CachedProgram cachedProgram, final int minAge) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.minAge = minAge;
            return;
        }
        cachedProgram.pcStateManager.settingIntField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 14, cachedProgram.minAge, minAge, 0);
    }
    
    private static final String pcGetname(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.name;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 15);
        return cachedProgram.name;
    }
    
    private static final void pcSetname(final CachedProgram cachedProgram, final String name) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.name = name;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 15, cachedProgram.name, name, 0);
    }
    
    private static final String pcGetphone(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.phone;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 16);
        return cachedProgram.phone;
    }
    
    private static final void pcSetphone(final CachedProgram cachedProgram, final String phone) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.phone = phone;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 16, cachedProgram.phone, phone, 0);
    }
    
    private static final boolean pcGetphysicalHealth(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.physicalHealth;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 17);
        return cachedProgram.physicalHealth;
    }
    
    private static final void pcSetphysicalHealth(final CachedProgram cachedProgram, final boolean physicalHealth) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.physicalHealth = physicalHealth;
            return;
        }
        cachedProgram.pcStateManager.settingBooleanField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 17, cachedProgram.physicalHealth, physicalHealth, 0);
    }
    
    private static final String pcGetstatus(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.status;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 18);
        return cachedProgram.status;
    }
    
    private static final void pcSetstatus(final CachedProgram cachedProgram, final String status) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.status = status;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 18, cachedProgram.status, status, 0);
    }
    
    private static final String pcGettype(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.type;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 19);
        return cachedProgram.type;
    }
    
    private static final void pcSettype(final CachedProgram cachedProgram, final String type) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.type = type;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 19, cachedProgram.type, type, 0);
    }
    
    private static final String pcGeturl(final CachedProgram cachedProgram) {
        if (cachedProgram.pcStateManager == null) {
            return cachedProgram.url;
        }
        cachedProgram.pcStateManager.accessingField(CachedProgram.pcInheritedFieldCount + 20);
        return cachedProgram.url;
    }
    
    private static final void pcSeturl(final CachedProgram cachedProgram, final String url) {
        if (cachedProgram.pcStateManager == null) {
            cachedProgram.url = url;
            return;
        }
        cachedProgram.pcStateManager.settingStringField((PersistenceCapable)cachedProgram, CachedProgram.pcInheritedFieldCount + 20, cachedProgram.url, url, 0);
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
