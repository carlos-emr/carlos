package schemaorg_apache_xmlbeans.system.sB2AF798ADE52EA1BCFFBCA28E4F3F101;

import org.apache.xmlbeans.SchemaTypeSystem;

public class TypeSystemHolder
{
    public static final SchemaTypeSystem typeSystem;
    
    private TypeSystemHolder() {
    }
    
    private static final SchemaTypeSystem loadTypeSystem() {
        try {
            return (SchemaTypeSystem) Class.forName("org.apache.xmlbeans.impl.schema.SchemaTypeSystemImpl", true, TypeSystemHolder.class.getClassLoader()).getConstructor(Class.class).newInstance(TypeSystemHolder.class);
        }
        catch (final ClassNotFoundException cause) {
            throw new RuntimeException("Cannot load org.apache.xmlbeans.impl.SchemaTypeSystemImpl: make sure xbean.jar is on the classpath.", cause);
        }
        catch (final Exception cause2) {
            throw new RuntimeException("Could not instantiate SchemaTypeSystemImpl (" + cause2.toString() + "): is the version of xbean.jar correct?", cause2);
        }
    }
    
    static {
        typeSystem = loadTypeSystem();
    }
}
