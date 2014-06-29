package poldercast.util;

import peersim.config.Configuration;
import peersim.config.FastConfig;
import peersim.core.Node;
import peersim.transport.Transport;

import java.util.HashSet;
import java.util.Iterator;

public class Util {
    public static Transport getTransportForProtocol(Node node, int pid) {
        return (Transport) node.getProtocol(FastConfig.getTransport(pid));
    }

    public static void sendMsg(Node from, Node to, Object msg, int protocolID) {
        Transport t = getTransportForProtocol(from, protocolID);
        t.send(from, to, msg, protocolID);
    }

    public static HashSet<NodeProfile> copyOfNodeProfiles(HashSet<NodeProfile> profiles) {
        HashSet<NodeProfile> copy = new HashSet<NodeProfile>();
        copy.addAll(profiles);
        return copy;
    }

    public static void removeFirstInHashSet(HashSet<?> set) {
        Iterator<?> iter = set.iterator();
        iter.next();
        iter.remove();
    }

    public static int getCycleLength() {
        return Configuration.getInt("CYCLE");
    }

    public static int getRingsViewSize() { return Configuration.getInt("rings.maxViewSize"); }
}
