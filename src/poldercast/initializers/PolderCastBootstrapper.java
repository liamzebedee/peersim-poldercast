package poldercast.initializers;

import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import peersim.dynamics.NodeInitializer;
import poldercast.util.PolderCastNode;

/*
 * Responsible for bootstrapping the PolderCast network, by inserting one other node in the view of every node
 */
public class PolderCastBootstrapper implements NodeInitializer {
    @Override
    public void initialize(Node n) {
        PolderCastNode node = (PolderCastNode) n;

    }
}
