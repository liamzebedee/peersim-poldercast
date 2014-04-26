package poldercast.initializers;

import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.PolderCastBaseNode;

/*
 * Responsible for bootstrapping the PolderCast network, by inserting one other node in the view of every node
 */
public class PolderCastBootstrapper implements NodeInitializer {
    @Override
    public void initialize(Node n) {
        PolderCastBaseNode node = (PolderCastBaseNode) n;

    }
}
