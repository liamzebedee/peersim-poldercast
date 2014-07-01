package tests.controls;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import poldercast.util.PolderCastBaseNode;

public class NetworkStatistics extends BaseControl {
    public NetworkStatistics(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        if(CommonState.getTime() == CommonState.getEndTime() - Configuration.getInt("CYCLE")) {
            System.out.println("Logging NetStats");
            int n = Network.size();
            double totalMessagesSent = 0;
            double totalMessagesReceived = 0;
            double totalBitsSent = 0;
            double totalBitsRecv = 0;
            double irrelevantTraffic = 0;

            for(int i=0; i<n; i++) {
                PolderCastBaseNode node = (PolderCastBaseNode) Network.get(i);
                totalMessagesSent += node.getCyclonProtocol().messagesSent
                        + node.getVicinityProtocol().messagesSent + node.getRingsProtocol().messagesSent;
                totalMessagesReceived += node.getCyclonProtocol().messagesReceived
                        + node.getVicinityProtocol().messagesReceived + node.getRingsProtocol().messagesReceived;
                totalBitsSent += node.getCyclonProtocol().bitsSent
                        + node.getVicinityProtocol().bitsSent + node.getRingsProtocol().bitsSent;
                totalBitsRecv += node.getCyclonProtocol().bitsReceived
                        + node.getVicinityProtocol().bitsReceived + node.getRingsProtocol().bitsReceived;
            }

            out.println("Total messages sent = "+totalMessagesSent);
            out.println("Total messages recv = "+totalMessagesReceived);
            out.println("Total MB sent = "+totalBitsSent/8/1000/1000);
            out.println("Total MB recv = "+totalBitsRecv/8/1000/1000);
            out.println("Irrelevant traffic = "+irrelevantTraffic);
        }

        // Measure how much network bandwidth is consumed in terms of megabytes sent and received
        // Measure how many individual messages sent and received
        // Measure the proportion of messages received that were unwanted / not relevant to the node's subscriptions
        return false;
    }
}
