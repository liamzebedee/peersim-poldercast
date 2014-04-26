package poldercast.initializers;

import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.PolderCastBaseNode;

public class PolderCastIdAssigner implements Control, NodeInitializer {
    public PolderCastIdAssigner(String prefix) {
        super();
    }

    @Override
    public boolean execute() {
        boolean stop = false;
        int n = Network.size();
        for(int i = 0; i < n; i++) {
            PolderCastBaseNode node = (PolderCastBaseNode) Network.get(i);
            node.initModules();
        }
        return stop;
    }

    @Override
    public void initialize(Node n) {
        PolderCastBaseNode node = (PolderCastBaseNode) n;
        node.initModules();
    }
}
