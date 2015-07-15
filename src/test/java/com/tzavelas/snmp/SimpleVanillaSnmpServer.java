package com.tzavelas.snmp;
import java.io.IOException;

import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

/**
 * A simple dummy snmmp server, it can be tested with net-snmp using
 *
 *  snmpget -v 2c -c public localhost:2001 1.3.6.1.4.1.29506.1.0
 *
 */
public class SimpleVanillaSnmpServer {
    public static void main(String[] args) throws IOException{
        VanillaSNMPAgent agent = new VanillaSNMPAgent("0.0.0.0/2001");
        agent.start();

        OID oid = new OID("1.3.6.1.4.1.29506.1.0");
        MOScalar testString = new MOScalar(oid, MOAccessImpl.ACCESS_READ_ONLY,
                new OctetString("Test String for SimpleVanillaSnmpServer"));
        agent.registerManagedObject(testString);

        try{
            while(true){
                Thread.sleep(5000);
                System.out.println("SNMP Agent is running");
            }
        }catch(InterruptedException e){
            agent.stop();
            System.out.println("SNMP Agent is stopped");
        }
    }
}
