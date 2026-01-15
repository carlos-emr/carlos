package ca.openosp.openo.caisi_integrator.dao;

import java.util.Iterator;
import java.util.List;
import ca.openosp.openo.caisi_integrator.util.MiscUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import java.io.Serializable;

abstract class AbstractModel<T> implements Serializable
{
    protected static final String OBJECT_NOT_YET_PERISTED = "The object is not persisted yet, this operation requires the object to already be persisted.";
    
    public abstract T getId();
    
    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString((Object)this);
    }
    
    @Override
    public int hashCode() {
        if (this.getId() == null) {
            MiscUtils.getLogger().warn((Object)"The object is not persisted yet, this operation requires the object to already be persisted.", (Throwable)new Exception());
            return super.hashCode();
        }
        return this.getId().hashCode();
    }
    
    @Override
    public boolean equals(final Object object) {
        if (this.getClass() != object.getClass()) {
            return false;
        }
        final AbstractModel<T> abstractModel = (AbstractModel<T>)object;
        if (this.getId() == null) {
            MiscUtils.getLogger().warn((Object)"The object is not persisted yet, this operation requires the object to already be persisted.", (Throwable)new Exception());
        }
        return this.getId().equals(abstractModel.getId());
    }
    
    public static <X extends AbstractModel<?>> boolean existsId(final List<X> list, final X searchModel) {
        final Object searchPk = ((AbstractModel<Object>)searchModel).getId();
        for (final X tempModel : list) {
            final Object tempPk = ((AbstractModel<Object>)tempModel).getId();
            if (searchPk.equals(tempPk)) {
                return Boolean.TRUE;
            }
        }
        return false;
    }
}
