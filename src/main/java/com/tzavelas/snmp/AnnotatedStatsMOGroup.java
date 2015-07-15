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

public class AnnotatedStatsMOGroup implements MOGroup {
    private static interface MemberSubAccessor {
        public Class<?> getValueType(Object obj);
        public MibObject getMibObjectAnnotation(Object obj);
        public boolean isSynthetic(Object obj);
        public VariantVariableCallback createVariantCallback(Object obj, Object clsMember, Logger logger);

    }

    private static MemberSubAccessor FieldSubAccessor = new MemberSubAccessor(){
        @Override
        public Class<?> getValueType(Object obj) {
            return ((Field)obj).getType();
        }

        @Override
        public MibObject getMibObjectAnnotation(Object obj) {
            return ((Field)obj).getAnnotation(MibObject.class);
        }

        @Override
        public boolean isSynthetic(Object obj) {
            return ((Field)obj).isSynthetic();
        }

        @Override
        public VariantVariableCallback createVariantCallback(Object obj,
                Object clsMember, Logger logger) {
            return new DynamicVariantVariableCallback(obj, (Field)clsMember, logger);
        }
    };

    public static MemberSubAccessor MethodSubAccessor = new MemberSubAccessor(){
        @Override
        public Class<?> getValueType(Object obj) {
            return ((Method)obj).getReturnType();
        }

        @Override
        public MibObject getMibObjectAnnotation(Object obj) {
            return ((Method)obj).getAnnotation(MibObject.class);
        }

        @Override
        public boolean isSynthetic(Object obj) {
            return ((Method)obj).isSynthetic();
        }

        @Override
        public VariantVariableCallback createVariantCallback(Object obj,
                Object clsMember, Logger logger) {
            return new DynamicVariantVariableCallback(obj, (Method)clsMember, logger);
        }
    };

    private static String EMPTY_OID = "";
    private static String EMPTY_TYPE = "";
    private static Map<String, Class<?>> VALID_TYPES =
            new ImmutableMap.Builder<String,Class<?>>()
            .put("integer32", Integer32.class )
            .put("counter64", Counter64.class)
            .put("octetstring", OctetString.class)
            .put(Byte.class.getName(), Integer32.class)     //class type
            .put(Short.class.getName(), Integer32.class)
            .put(Integer.class.getName(), Integer32.class)
            .put(Long.class.getName(), Counter64.class)
            .put(String.class.getName(), OctetString.class)
            .put("byte", Integer32.class)           //primitive type
            .put("short", Integer32.class)
            .put("int", Integer32.class)
            .put("long", Counter64.class)
            .build();

    private List<MOScalar> _managedObjects = new ArrayList<MOScalar>();
    private Logger _logger = null;


    public AnnotatedStatsMOGroup(){
        _logger = Logger.getLogger(this.getClass().getName());
        _logger.setUseParentHandlers(false);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new VerySimpleFormatterWithTimestamp());
        _logger.addHandler(consoleHandler);
    }

    private Class<?> getMibObjectType(String givenObjType, Class<?> valType){
        String typeNorm = givenObjType.trim().toLowerCase();
        Class<?> ret = null;
        if( typeNorm.equals(EMPTY_TYPE) ){
            ret = VALID_TYPES.get(valType.getName());
            if(ret == null){
                ret = OctetString.class;
            }
            _logger.info("Autodetected oid type: "+ret+", from type: "+valType.getName());
        }else if( VALID_TYPES.containsKey(typeNorm) ){
            ret = VALID_TYPES.get(valType.getName());
        }
        return ret;
    }

    private boolean isAnnotationValid(MibObject annotation){
        if(annotation == null){
            return false;
        }else if( annotation.oid().trim().equals(EMPTY_OID) ){
            _logger.warning("Empty OID provided");
            return false;
        }
        return true;
    }

    private int processObjectFields(
            Object obj,
            String oidPrefix,
            Object[] members,
            MemberSubAccessor accessor) throws InstantiationException, IllegalAccessException{
        int ret = 0;
        for(Object member: members){
            Variable var = null;
            Class<?> mibObjCls = null;
            String oidSuffix = "";
            MibObject mibAnnotation = accessor.getMibObjectAnnotation(member);

            if( !isAnnotationValid(mibAnnotation) || accessor.isSynthetic(member) ){
                continue;
            }

            mibObjCls = getMibObjectType( mibAnnotation.type(), accessor.getValueType(member));
            if(mibObjCls == null){
                //TODO: log unsupported types
                continue;
            }

            var = new VariantVariable(
                    (Variable) mibObjCls.newInstance(),
                    accessor.createVariantCallback(obj, member, _logger) );

            //For Scalar value, .0 ending is needed
            if( ! mibAnnotation.oid().endsWith(".0") ){
                oidSuffix = ".0";
            }

            OID oidObj = new OID(oidPrefix + mibAnnotation.oid() + oidSuffix);
            _managedObjects.add(
                    new MOScalar(
                            oidObj,
                            MOAccessImpl.ACCESS_READ_ONLY,
                            var));
            _logger.info(""+oidObj+", varType: "+((VariantVariable)var).getVariable().getClass().getName());
            ret +=1;
        }
        return ret;
    }

    public int addAnnotatedMibObject( Object obj){
        return addAnnotatedMibObject( obj, "");
    }

    public int addAnnotatedMibObject( Object obj, String oidPrefix) {
        int moAdded = 0;
        Class<?> aCls = obj.getClass();
        try{
            moAdded += processObjectFields( obj, oidPrefix, aCls.getDeclaredFields(), FieldSubAccessor );
            moAdded += processObjectFields( obj, oidPrefix, aCls.getDeclaredMethods(), MethodSubAccessor );
        } catch (IllegalArgumentException
                | IllegalAccessException
                | InstantiationException e) {
            _logger.warning( String.format("Exception occurred: %s", e.getMessage()) );
            _logger.throwing(this.getClass().getName(), "addAnnotatedMibObject", e);
            Throwables.propagate(e);
        }
        return moAdded;
    }

    @Override
    public void registerMOs(MOServer server, OctetString arg1)
            throws DuplicateRegistrationException {
        for(MOScalar mo:_managedObjects){
            server.register(mo, arg1);
        }
    }

    @Override
    public void unregisterMOs(MOServer server, OctetString arg1) {
        for(MOScalar mo:_managedObjects){
            server.unregister(mo, arg1);
        }
    }
}
