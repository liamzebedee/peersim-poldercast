package poldercast.util;

import poldercast.protocols.RingsProtocol;
import poldercast.protocols.VicinityProtocol;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class VicinityComparator implements Comparator<NodeProfile> {
    private NodeProfile perspectiveNode;
    // Priority is defined by the number of nodes one needs to fill their Rings view for that topic
    public final byte MAX_TOPIC_PRIORITY;

    public VicinityComparator(NodeProfile perspectiveNode, byte maxTopicPriority) {
        this.perspectiveNode = perspectiveNode;
        this.MAX_TOPIC_PRIORITY = maxTopicPriority;
        if(this.perspectiveNode.getSubscriptions().isEmpty()) throw new RuntimeException("Perspective node doesn't have subscriptions - shouldn't be so");
    }

    /*
     * Ranks the closeness of nodes according to priority/number of common topics
     * Nodes that are closer rank higher
     */
    @Override
    public int compare(NodeProfile nodeA, NodeProfile nodeB) {
        if(nodeA.equals(nodeB)) throw new RuntimeException("Attempting to compare the same two nodes - duplicate in node set");
        if(this.perspectiveNode.equals(nodeA) || this.perspectiveNode.equals(nodeB)) throw new RuntimeException("Attempting to compare node with perspective node - duplicate in node set");

        // By default, the nodes are assumed to be equally ranked
        int retVal = 0;

        int nodeATopicPriorityScore = 0;
        int nodeBTopicPriorityScore = 0;
        int nodeANumberOfSharedTopics = 0;
        int nodeBNumberOfSharedTopics = 0;
        for(Map.Entry<ID, Byte> subscription : this.perspectiveNode.getSubscriptions().entrySet()) {
            int normalisedPriority = subscription.getValue().intValue() % MAX_TOPIC_PRIORITY;
            if(nodeA.getSubscriptions().containsKey(subscription.getKey())) {
                nodeANumberOfSharedTopics++;
                nodeATopicPriorityScore += normalisedPriority;
            }
            if(nodeB.getSubscriptions().containsKey(subscription.getKey())) {
                nodeBNumberOfSharedTopics++;
                nodeBTopicPriorityScore += normalisedPriority;
            }
        }

        if(nodeATopicPriorityScore != nodeBTopicPriorityScore) {
            // candidates subscribed to topics annotated with higher priority by the
            // target node are ranked closer compared to candidates of lower priority topics.
            retVal = nodeATopicPriorityScore > nodeBTopicPriorityScore ? 1 : -1;
        } else {
            // Among candidate nodes that rank equally in terms of topic priorities, proximity
            // is determined by the number of topics shared with the target node: the more
            // shared topics, the closer their ranking.
            if(nodeANumberOfSharedTopics != nodeBNumberOfSharedTopics)
                retVal = nodeANumberOfSharedTopics > nodeBNumberOfSharedTopics ? 1 : -1;
        }

        return retVal;
    }
}