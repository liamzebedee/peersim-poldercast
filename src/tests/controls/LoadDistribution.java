package tests.controls;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import tests.util.BaseNode;

public class LoadDistribution extends BaseControl {
    public LoadDistribution(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        // Run at the final cycle to get the best representation of the load distribution of the network
        if(CommonState.getTime() == CommonState.getEndTime() - Configuration.getInt("CYCLE")) {
            // - calculate the Gini coefficient based the load value of a node
            double meanLoad = 0;
            int networkSize = Network.size();
            for(int i = 0; i < networkSize; i++) {
                BaseNode node = (BaseNode) Network.get(i);
                meanLoad += node.load;
            }
            meanLoad /= networkSize;

            // Calculate the total difference of load between every possible pair of nodes
            int totalDifferenceOfLoad = 0;
            for(int i = 0; i < networkSize; i++) {
                for(int j = 0; j < networkSize; j++) {
                    BaseNode nodeI = (BaseNode) Network.get(i);
                    BaseNode nodeJ = (BaseNode) Network.get(j);
                    totalDifferenceOfLoad += (nodeI.load - nodeJ.load);
                }
            }

            // the mean of the difference of the load between every possible pair of peers divided by their mean load
            double giniCoefficient = (1 / (2*meanLoad))  *  (1 / (Math.pow(networkSize, 2.0)))  *  totalDifferenceOfLoad;
            out.println(giniCoefficient);
        }

        // TopicPublication.loadEstimates = when a publish message is sent
        // TopicSubscription.loadEstimates = when a SCRIBE{CREATE, SUBSCRIBE, HEARTBEAT} is sent
        return false;
    }
}
