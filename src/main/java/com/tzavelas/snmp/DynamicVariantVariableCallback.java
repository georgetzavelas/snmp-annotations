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

//Intentionally protected class
class DynamicVariantVariableCallback extends ReadonlyVariableCallback {
    private Object _reprObj = null;
    private Field _field = null;
    private Method _method = null;
    private Logger _logger = null;

    private DynamicVariantVariableCallback(Object obj, Field field, Method method, Logger logger){
        _reprObj = obj;
        _field = field;
        _method = method;
        _logger = logger;
    }

    public DynamicVariantVariableCallback(Object obj, Field field){
        this(obj, field, null, null);
    }

    public DynamicVariantVariableCallback(Object obj, Field field, Logger logger){
        this(obj, field, null, logger);
    }

    public DynamicVariantVariableCallback(Object obj, Method method){
        this(obj, null, method, null);
    }

    public DynamicVariantVariableCallback(Object obj, Method method, Logger logger){
        this(obj, null, method, logger);
    }

    private Object getObjCurrentValue()
            throws IllegalArgumentException,
            IllegalAccessException,
            InvocationTargetException{
        Object ret = null;

        if(_field != null){
            boolean accessible = _field.isAccessible();
            try{
                _field.setAccessible(true);
                ret = _field.get(_reprObj);
            }finally{
                _field.setAccessible(accessible);
            }
        }else{
            boolean accessible = _method.isAccessible();
            try{
                _method.setAccessible(true);
                ret = _method.invoke(_reprObj);
            }finally{
                _method.setAccessible(accessible);
            }
        }

        return ret;
    }

    @Override
    public void updateVariable(VariantVariable variable){
        Variable inVar = variable.getVariable();
        try {
            Object val = getObjCurrentValue();
            if( inVar instanceof AssignableFromInteger ){
                AssignableFromInteger convVar = (AssignableFromInteger)inVar;
                Number number = (Number)val;
                convVar.setValue( number.intValue() );

            }else if( inVar instanceof AssignableFromLong ){
                AssignableFromLong convVar = (AssignableFromLong)inVar;
                Number number = (Number)val;
                convVar.setValue( number.longValue() );

            }else if( inVar instanceof AssignableFromString ){
                AssignableFromString convVar = (AssignableFromString)inVar;
                convVar.setValue( val.toString() );

            }else{
                //No update on variable if there is no matching type.
            }
        } catch (IllegalArgumentException | IllegalAccessException
                | InvocationTargetException e) {
            if(_logger != null){
                _logger.info(String.format( "Encountered Exception: %s", e));
                _logger.info(e.getMessage());
            }else{
                e.printStackTrace();
            }
        }
    }
}