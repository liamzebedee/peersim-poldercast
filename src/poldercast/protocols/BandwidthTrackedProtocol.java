package poldercast.protocols;

import poldercast.util.NetworkMsg;

public abstract class BandwidthTrackedProtocol {
    public int bitsSent = 0;
    public int bitsReceived = 0;
    public int messagesSent = 0;
    public int messagesReceived = 0;

    public void messageSent(NetworkMsg msg) {
        bitsSent += msg.getSizeInBits();
        messagesSent++;
    }

    public void messageReceived(NetworkMsg msg) {
        bitsReceived += msg.getSizeInBits();
        messagesReceived++;
    }
}
