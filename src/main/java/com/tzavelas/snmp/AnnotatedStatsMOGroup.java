package com.tzavelas.snmp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import org.snmp4j.agent.DuplicateRegistrationException;
import org.snmp4j.agent.MOGroup;
import org.snmp4j.agent.MOServer;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariantVariable;
import org.snmp4j.smi.VariantVariableCallback;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

/**
 * Simplifies the registration of @MibObject annotated members within a class by registering them as a group.
 *
 * Usage:
 *         ObjectWithMibObjects obj = new ObjectWithMibObjects();
 *         ...
 *         AnnotatedStatsMOGroup moGroup = new AnnotatedStatsMOGroup();
 *         moGroup.addAnnotatedMibObject(obj);
 *         ...
 *         moGroup.registerMOs(agent.getServer(), null);
 */
public class AnnotatedStatsMOGroup implements MOGroup {
    /**
     * Interface for accessors
     */
    private static interface MemberSubAccessor {
        public Class<?> getValueType(Object obj);
        public MibObject getMibObjectAnnotation(Object obj);
        public boolean isSynthetic(Object obj);
        public VariantVariableCallback createVariantCallback(Object obj, Object clsMember, Logger logger);

    }

    /**
     * Accessor that is aware of fields that have been annotated with @MibObject
     */
    private static MemberSubAccessor FieldSubAccessor = new MemberSubAccessor() {
        public Class<?> getValueType(Object obj) {
            return ((Field)obj).getType();
        }

        public MibObject getMibObjectAnnotation(Object obj) {
            return ((Field)obj).getAnnotation(MibObject.class);
        }

        public boolean isSynthetic(Object obj) {
            return ((Field)obj).isSynthetic();
        }

        public VariantVariableCallback createVariantCallback(Object obj,
                Object clsMember, Logger logger) {
            return new DynamicVariantVariableCallback
                    .DynamicVariantVariableCallbackBuilder(obj)
                    .field((Field)clsMember)
                    .logger(logger)
                    .build();
        }
    };

    /**
     * Accessor that is aware of methods that have been annotated with @MibObject
     */
    public static MemberSubAccessor MethodSubAccessor = new MemberSubAccessor() {
        public Class<?> getValueType(Object obj) {
            return ((Method)obj).getReturnType();
        }

        public MibObject getMibObjectAnnotation(Object obj) {
            return ((Method)obj).getAnnotation(MibObject.class);
        }

        public boolean isSynthetic(Object obj) {
            return ((Method)obj).isSynthetic();
        }

        public VariantVariableCallback createVariantCallback(Object obj,
                Object clsMember, Logger logger) {
            return new DynamicVariantVariableCallback
                    .DynamicVariantVariableCallbackBuilder(obj)
                    .method((Method)clsMember)
                    .logger(logger)
                    .build();
        }
    };

    private static final String EMPTY_OID = "";
    private static final String EMPTY_TYPE = "";
    private static Map<String, Class<?>> OBJECT_CLASS_TO_MIB_CLASS_MAPPING =
            new ImmutableMap.Builder<String,Class<?>>()
            .put("integer32", Integer32.class )
            .put("counter64", Counter64.class)
            .put("octetstring", OctetString.class)
            .put(Byte.class.getName(), Integer32.class)     //class type
            .put(Short.class.getName(), Integer32.class)
            .put(Integer.class.getName(), Integer32.class)
            .put(Long.class.getName(), Counter64.class)
            .put(String.class.getName(), OctetString.class)
            .put("byte", Integer32.class)                   //primitive type
            .put("short", Integer32.class)
            .put("int", Integer32.class)
            .put("long", Counter64.class)
            .build();

    private List<MOScalar> _managedObjects = new ArrayList<>();
    private Logger _logger = null;

    /**
     * Constructor
     */
    public AnnotatedStatsMOGroup(){
        _logger = Logger.getLogger(this.getClass().getName());
        _logger.setUseParentHandlers(false);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new VerySimpleFormatterWithTimestamp());
        _logger.addHandler(consoleHandler);
    }

    /**
     * Get MIB class from @MibObject type
     *
     * @param mibAnnotationType
     * @param mibClass
     * @return MIB Class
     */
    private Class<?> getMibObjectType(String mibAnnotationType, Class<?> mibClass){
        String typeNorm = mibAnnotationType.trim().toLowerCase();
        Class<?> ret = null;
        if (typeNorm.equals(EMPTY_TYPE) ){
            ret = OBJECT_CLASS_TO_MIB_CLASS_MAPPING.get(mibClass.getName());
            if (ret == null) {
                ret = OctetString.class;
            }
            _logger.info("Autodetected oid type: " + ret + ", from type: " + mibClass.getName());
        } else if (OBJECT_CLASS_TO_MIB_CLASS_MAPPING.containsKey(typeNorm)) {
            ret = OBJECT_CLASS_TO_MIB_CLASS_MAPPING.get(mibClass.getName());
        }
        return ret;
    }

    /**
     * Determine if the annotation is valid
     *
     * @param annotation
     * @return
     */
    private boolean isAnnotationValid(MibObject annotation) {
        if (annotation == null) {
            return false;
        } else if (annotation.oid().trim().equals(EMPTY_OID)) {
            _logger.warning("Empty OID provided");
            return false;
        }
        return true;
    }

    /**
     * Process all the members to find the @MibOject fields in the object
     *
     * @param annotatedMibObject
     * @param oidPrefix
     * @param members
     * @param accessor
     * @return number of valid @MibObjects processed
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private int processObjectFields(Object annotatedMibObject, String oidPrefix, Object[] members,
            MemberSubAccessor accessor) throws InstantiationException, IllegalAccessException {
        int processCount = 0;

        for (Object member: members) {
            Variable var = null;
            Class<?> mibObjCls = null;
            String oidSuffix = "";
            MibObject mibAnnotation = accessor.getMibObjectAnnotation(member);

            if (!isAnnotationValid(mibAnnotation) || accessor.isSynthetic(member)) {
                continue;
            }

            mibObjCls = getMibObjectType(mibAnnotation.type(), accessor.getValueType(member));
            if (mibObjCls == null) {
                // TODO: log unsupported types
                continue;
            }

            var = new VariantVariable((Variable) mibObjCls.newInstance(),
                    accessor.createVariantCallback(annotatedMibObject, member, _logger));

            // For Scalar value, .0 ending is needed
            if (!mibAnnotation.oid().endsWith(".0")) {
                oidSuffix = ".0";
            }

            OID oidObj = new OID(oidPrefix + mibAnnotation.oid() + oidSuffix);
            _managedObjects.add(new MOScalar(oidObj, MOAccessImpl.ACCESS_READ_ONLY, var));
            _logger.info("" + oidObj + ", varType: " + ((VariantVariable)var).getVariable().getClass().getName());
            processCount +=1;
        }
        return processCount;
    }

    /**
     * Add the @MibObject annotated members to the group to be registered.
     *
     * @param annotatedMibObject
     * @return the number of annotated fields and methods added
     */
    public int addAnnotatedMibObject(Object annotatedMibObject) {
        return addAnnotatedMibObject(annotatedMibObject, "");
    }

    /**
     * Add the @MibObject annotated members to the group to be registered with an OID prefix
     *
     * @param annotatedMibObject
     * @param oidPrefix
     * @return the number of annotated fields and methods added
     */
    public int addAnnotatedMibObject(Object annotatedMibObject, String oidPrefix) {
        int moAdded = 0;
        Class<?> aCls = annotatedMibObject.getClass();
        try {
            moAdded += processObjectFields( annotatedMibObject, oidPrefix, aCls.getDeclaredFields(), FieldSubAccessor );
            moAdded += processObjectFields( annotatedMibObject, oidPrefix, aCls.getDeclaredMethods(), MethodSubAccessor );
        } catch (IllegalArgumentException
                | IllegalAccessException
                | InstantiationException e) {
            _logger.warning(String.format("Exception occurred: %s", e.getMessage()));
            _logger.throwing(this.getClass().getName(), "addAnnotatedMibObject", e);
            Throwables.propagate(e);
        }
        return moAdded;
    }

    @Override
    public void registerMOs(MOServer server, OctetString arg1)
            throws DuplicateRegistrationException {
        for (MOScalar mo:_managedObjects) {
            server.register(mo, arg1);
        }
    }

    @Override
    public void unregisterMOs(MOServer server, OctetString arg1) {
        for (MOScalar mo:_managedObjects) {
            server.unregister(mo, arg1);
        }
    }
}
