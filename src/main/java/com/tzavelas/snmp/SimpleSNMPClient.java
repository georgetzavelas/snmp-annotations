package com.tzavelas.snmp;

import java.io.IOException;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * A very simple minimalistic SNMP Client.
 *
 * @source: http://www.jayway.com/2010/05/21/introduction-to-snmp4j/
 */
public class SimpleSNMPClient {
    private String address = null;
    private Snmp snmp = null;
    private CommunityTarget target = null;
    private TransportMapping transport = null;

    public SimpleSNMPClient(String address) {
        this(address, "public", SnmpConstants.version2c);
    }

    public SimpleSNMPClient(String address,
                            String community, int snmpVersion){
        this.address = address;

        Address targetAddress = GenericAddress.parse(address);
        target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setVersion(snmpVersion);
        target.setRetries(1);
        target.setTimeout(5000);
    }

    public void shutdown() throws IOException {
        snmp.close();
    }

    public void start() throws IOException {
        transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);

        //SNMP uses UDP, therefore we need to listen
        transport.listen();
    }

    //Using OIDs
    public Variable getMibObjectAsVariable(OID oid) throws IOException {
        ResponseEvent event = getMibObjects(oid);
        return event.getResponse().get(0).getVariable();
    }

    public Variable getMibObjectAsVariable(String oid) throws IOException {
        return getMibObjectAsVariable( new OID(oid) );
    }

    public int getMibObjectAsInteger(OID oid) throws IOException {
        return getMibObjectAsVariable(oid).toInt();
    }

    public int getMibObjectAsInteger(String oid) throws IOException {
        return getMibObjectAsVariable(oid).toInt();
    }

    public long getMibObjectAsLong(OID oid) throws IOException {
        return getMibObjectAsVariable(oid).toLong();
    }

    public long getMibObjectAsLong(String oid) throws IOException {
        return getMibObjectAsVariable(oid).toLong();
    }

    public String getMibObjectAsString(OID oid) throws IOException {
        return getMibObjectAsVariable(oid).toString();
    }

    public String getMibObjectAsString(String oid) throws IOException {
        return getMibObjectAsVariable(oid).toString();
    }

    public ResponseEvent getMibObjects(OID ... oids) throws IOException {
       return snmp.send(buildGetPDU(oids), target, null);
    }

    public void getMibObjects(ResponseListener listener, OID ... oids) throws IOException {
        snmp.send(buildGetPDU(oids), target, null, listener);
    }

    private PDU buildGetPDU(OID ... oids) {
        PDU pdu = new PDU();
        for (OID oid : oids) {
            pdu.add(new VariableBinding(oid));
        }

        pdu.setType(PDU.GET);
        return pdu;
    }
}
