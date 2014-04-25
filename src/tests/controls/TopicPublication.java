package tests.controls;

public class TopicPublication extends BaseControl {
    public TopicPublication(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        /*
         * measure the dissemination speed through estimating the number of cycles it takes to reach 95%+ of subscribers
         * measure the hit ratio starting at the Nth cycle for 100 cycles
         */
        return false;
    }
}
