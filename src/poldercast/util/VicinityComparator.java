package poldercast.util;

import poldercast.protocols.RingsProtocol;
import poldercast.protocols.VicinityProtocol;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class VicinityComparator implements Comparator<NodeProfile> {
    private NodeProfile perspectiveNode;
    // Priority is defined by the number of nodes one needs to fill their Rings view for that topic
    public static final int MAX_TOPIC_PRIORITY = RingsProtocol.MAX_VIEW_SIZE;

    public VicinityComparator(NodeProfile perspectiveNode) {
        this.perspectiveNode = perspectiveNode;
    }

    @Override
    public int compare(NodeProfile nodeA, NodeProfile nodeB) {
        if(this.perspectiveNode.getSubscriptions().isEmpty()) return 0;

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
            retVal = nodeANumberOfSharedTopics > nodeBNumberOfSharedTopics ? 1 : -1;
        }

        return retVal;
    }
}
