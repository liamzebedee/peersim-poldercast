package poldercast.util;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.GeneralNode;
import poldercast.protocols.CyclonProtocol;
import poldercast.protocols.VicinityProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PolderCastNode extends GeneralNode {
    private NodeProfile nodeProfile;

    public PolderCastNode(String configPrefix) {
        super(configPrefix);
    }

    public void initModules() {
        this.nodeProfile = new NodeProfile(this, new ID(CommonState.r), new HashMap<ID, Byte>());
    }

    public NodeProfile getNodeProfile() { return this.nodeProfile; }

    public CyclonProtocol getCyclonProtocol() {
        return (CyclonProtocol) this.getProtocol(Configuration.lookupPid(CyclonProtocol.CYCLON));
    }

    public VicinityProtocol getVicinityProtocol() {
        return (VicinityProtocol) this.getProtocol(Configuration.lookupPid(VicinityProtocol.VICINITY));
    }

    public ArrayList<NodeProfile> getUnionOfAllViews() {
        ArrayList<NodeProfile> union = new ArrayList<NodeProfile>();
        union.addAll(this.getCyclonProtocol().routingTable);
        union.addAll(this.getVicinityProtocol().routingTable);
        return union;
    }

    public String toString() {
        return this.nodeProfile.toString();
    }
}
