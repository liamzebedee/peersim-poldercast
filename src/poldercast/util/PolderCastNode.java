package poldercast.util;

import peersim.core.CommonState;
import peersim.core.GeneralNode;

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
}
