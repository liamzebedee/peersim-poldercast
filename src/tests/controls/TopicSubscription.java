package tests.controls;

public class TopicSubscription extends BaseControl {
    public TopicSubscription(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        /*
         * Measure speed (in cycles) at which a node can subscribe to a new topic and start receiving events published to it
         * That is, the number of cycles it takes for a new node to start receiving events on the topic.
         * One event published from a random subscriber each cycle.
         */
        return false;
    }
}
