package tests.controls;

import net.sourceforge.sizeof.SizeOf;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import poldercast.util.PolderCastBaseNode;

public class MemoryStatistics extends BaseControl {
    public MemoryStatistics(String prefix) {
        super(prefix);
    }
    static {
        SizeOf.skipStaticField(true); //java.sizeOf will not compute static fields
        SizeOf.skipFinalField(true); //java.sizeOf will not compute final fields
        SizeOf.skipFlyweightObject(false); //java.sizeOf will not compute well-known flyweight objects
    }

    @Override
    public boolean execute() {
        if(CommonState.getTime() == CommonState.getEndTime() - Configuration.getInt("CYCLE")) {
            System.out.println("Logging MemoryStats");
            long memoryConsumed = 0;
            int n = Network.size();
            for(int i=0; i<n; i++) {
                PolderCastBaseNode node = (PolderCastBaseNode) Network.get(i);
                long nodeMem = 0;
                nodeMem += SizeOf.deepSizeOf(node.getCyclonProtocol().routingTable)
                        + SizeOf.deepSizeOf(node.getVicinityProtocol().routingTable)
                        + SizeOf.deepSizeOf(node.getRingsProtocol().routingTable);
                memoryConsumed += nodeMem;
            }
            memoryConsumed /= n;

            out.println("MB consumed on average by routing table = "+SizeOf.humanReadable(memoryConsumed));
        }
        return false;
    }
}
