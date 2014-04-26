package poldercast.util;

public abstract class NetworkMsg implements SizeInBits {
    private final PolderCastBaseNode sender;

    public NetworkMsg(PolderCastBaseNode sender) {
        this.sender = sender;
    }

    public PolderCastBaseNode getSender() {
        return this.sender;
    }
}
