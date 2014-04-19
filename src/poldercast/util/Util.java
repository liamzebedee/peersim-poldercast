package poldercast.util;

import peersim.config.FastConfig;
import peersim.core.Node;
import peersim.transport.Transport;

public class Util {
    public static Transport getTransportForProtocol(Node node, int pid) {
        return (Transport) node.getProtocol(FastConfig.getTransport(pid));
    }

    public static void sendMsg(Node from, Node to, Object msg, int protocolID) {
        Transport t = getTransportForProtocol(from, protocolID);
        t.send(from, to, msg, protocolID);
    }
}
