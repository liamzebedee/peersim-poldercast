package poldercast.util;

public class PublishMsg extends NetworkMsg {
    private final byte[] event;
    private final ID topic;

    public PublishMsg(byte[] event, ID topic, PolderCastBaseNode sender) {
        super(sender);
        this.event = event;
        this.topic = topic;
    }

    public byte[] getEvent() { return this.event; }

    public ID getTopic() { return this.topic; }

    public int getUniqueIdentifier() {
        return java.util.Arrays.hashCode(this.event);
    }

    @Override
    public int getSizeInBits() {
        return this.event.length * 8;
    }
}
