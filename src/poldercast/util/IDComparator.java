package poldercast.util;

import java.util.Comparator;

public class IDComparator implements Comparator<NodeProfile> {
    @Override
    public int compare(NodeProfile nodeA, NodeProfile nodeB) {
        return nodeA.compare(nodeB);
    }
}
