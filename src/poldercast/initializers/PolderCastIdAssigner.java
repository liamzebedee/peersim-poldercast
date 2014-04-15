package poldercast.initializers;

import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.PolderCastNode;

public class PolderCastIdAssigner implements Control, NodeInitializer {
    public PolderCastIdAssigner(String prefix) {
        super();
    }

    @Override
    public boolean execute() {
        boolean stop = false;
        int n = Network.size();
        for(int i = 0; i < n; i++) {
            PolderCastNode node = (PolderCastNode) Network.get(i);
            node.initModules();
        }
        return stop;
    }

    @Override
    public void initialize(Node n) {
        PolderCastNode node = (PolderCastNode) n;
        node.initModules();
    }
}
