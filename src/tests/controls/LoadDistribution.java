package tests.controls;

public class LoadDistribution extends BaseControl {
    public LoadDistribution(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        // Run at the final cycle to get the best representation of the load distribution of the network
        //  - calculate the Gini coefficient based the load value of a node

        // TopicPublication.loadEstimates = { Message.PUBLISH }
        // TopicSubscription.loadEstimates = { SCRIBE{CREATE, SUBSCRIBE, HEARTBEAT}, POLDERCAST{GOSSIP.Rings} }
        return false;
    }
}
