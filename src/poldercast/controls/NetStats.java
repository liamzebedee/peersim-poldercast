package poldercast.controls;

import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;
import poldercast.util.PolderCastBaseNode;
import tests.controls.BaseControl;

public class NetStats extends BaseControl implements Control {
    public NetStats(String prefix) {
        super(prefix);
    }

    public boolean execute() {
        if(CommonState.getTime() == CommonState.getEndTime() - 10) {
            outputMessageStats();
            this.out.close();
        }
        return false;
    }

    public void outputMessageStats() {
        int n = Network.size();
        for(int i=0; i<n; i++) {
            PolderCastBaseNode node = (PolderCastBaseNode) Network.get(i);
            out.println(node.toString() + " sent:" + node.getCyclonProtocol().messagesSent + " recv:" + node.getCyclonProtocol().messagesReceived);
        }
    }
}
