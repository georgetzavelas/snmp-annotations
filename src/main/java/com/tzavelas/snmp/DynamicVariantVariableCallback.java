package com.tzavelas.snmp;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.snmp4j.smi.AssignableFromInteger;
import org.snmp4j.smi.AssignableFromLong;
import org.snmp4j.smi.AssignableFromString;
import org.snmp4j.smi.ReadonlyVariableCallback;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariantVariable;

/**
 * Callback to monitor any change in the value of the variable.
 * Used by AnnotatedStatsMOGroup hence why its not public
 */
class DynamicVariantVariableCallback extends ReadonlyVariableCallback {
    private Object reprObj = null;
    private Field field = null;
    private Method method = null;
    private Logger logger = null;

    private DynamicVariantVariableCallback(DynamicVariantVariableCallbackBuilder builder) {
        reprObj = builder.obj;
        field = builder.field;
        method = builder.method;
        logger = builder.logger;
    }

    private Object getObjCurrentValue() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException {
        Object ret = null;

        if (field != null) {
            boolean accessible = field.isAccessible();
            try {
                field.setAccessible(true);
                ret = field.get(reprObj);
            } finally {
                field.setAccessible(accessible);
            }
        } else {
            boolean accessible = method.isAccessible();
            try {
                method.setAccessible(true);
                ret = method.invoke(reprObj);
            } finally {
                method.setAccessible(accessible);
            }
        }

        return ret;
    }

    @Override
    public void updateVariable(VariantVariable variable){
        Variable inVar = variable.getVariable();
        try {
            Object val = getObjCurrentValue();
            if (inVar instanceof AssignableFromInteger) {
                AssignableFromInteger convVar = (AssignableFromInteger)inVar;
                Number number = (Number)val;
                convVar.setValue(number.intValue());
            } else if (inVar instanceof AssignableFromLong) {
                AssignableFromLong convVar = (AssignableFromLong)inVar;
                Number number = (Number)val;
                convVar.setValue( number.longValue() );
            } else if(inVar instanceof AssignableFromString){
                AssignableFromString convVar = (AssignableFromString)inVar;
                convVar.setValue( val.toString() );
            } else {
                //No update on variable if there is no matching type.
            }
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            if (logger != null) {
                logger.info(String.format("Encountered Exception: %s", e));
                logger.info(e.getMessage());
            } else {
                e.printStackTrace();
            }
        }
    }

    public static class DynamicVariantVariableCallbackBuilder {
        // required parameters
        private final Object obj;
        // other parameters
        private Field field;
        private Method method;
        private Logger logger;

        public DynamicVariantVariableCallbackBuilder(Object obj) {
            this.obj = obj;
        }

        public DynamicVariantVariableCallbackBuilder field(Field field) {
            this.field = field;
            return this;
        }

        public DynamicVariantVariableCallbackBuilder method(Method method) {
            this.method = method;
            return this;
        }

        public DynamicVariantVariableCallbackBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public DynamicVariantVariableCallback build() {
            return new DynamicVariantVariableCallback(this);
        }
    }
}