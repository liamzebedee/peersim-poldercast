package poldercast.controls;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;
import peersim.util.FileNameGenerator;
import poldercast.util.NodeProfile;
import poldercast.util.PolderCastNode;

import java.io.PrintStream;

public class ViewStats extends BaseControl implements Control {
    static {
        logName = "view-stats";
    }

    public ViewStats(String prefix) {
        super(prefix);
    }

    public boolean execute() {
        if((CommonState.getTime() == CommonState.getEndTime() - 1)) {
            this.startNewLog();
            int n = Network.size();
            this.out.println("Network size: "+n+"\n");
            this.summariseViewDegrees();
            this.out.close();
        }
        return false;
    }

    public void summariseViewDegrees() {
        int n = Network.size();
        for(int i=0; i<n; i++) {
            PolderCastNode node = (PolderCastNode) Network.get(i);
            this.out.println("[" + node.toString() + "]");
            out.println("-Cyclon : " + node.getCyclonProtocol().degree());
            for(NodeProfile profile : node.getCyclonProtocol().getRoutingTableCopy()) {
                out.println("-- "+profile.toString());
            }
            out.println("-Vicinity : " + node.getVicinityProtocol().degree());
            for(NodeProfile profile : node.getVicinityProtocol().getRoutingTableCopy()) {
                out.println("-- "+profile.toString());
            }
            this.out.println();
            this.out.flush();
        }
    }
}
