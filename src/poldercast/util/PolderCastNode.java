package poldercast.util;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.GeneralNode;
import poldercast.protocols.CyclonProtocol;

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

    public String toString() {
        return this.nodeProfile.toString();
    }
}
