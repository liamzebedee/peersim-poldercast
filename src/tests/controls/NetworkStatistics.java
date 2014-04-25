package tests.controls;

public class NetworkStatistics extends BaseControl {
    public NetworkStatistics(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        // Measure how much network bandwidth is consumed in terms of megabytes sent and received
        // Measure how many individual messages sent and received
        // Measure the proportion of messages received that were unwanted / not relevant to the node's subscriptions
        return false;
    }
}
