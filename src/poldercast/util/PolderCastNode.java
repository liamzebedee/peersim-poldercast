package poldercast.util;

import peersim.core.CommonState;
import peersim.core.GeneralNode;

public class PolderCastNode extends GeneralNode {
    private ID nodeId;
    public PolderCastNode(String prefix) {
        super(prefix);
    }

    public void initModules() {
        this.nodeId = new ID(CommonState.r);
    }
}
