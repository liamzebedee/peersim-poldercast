package tests.controls;

public class ResilienceStatistics extends BaseControl {
    public ResilienceStatistics(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        // given a set number of nodes leaving the network each cycle (Skype dataset)
        // measure the percentage of subscribers that miss an event published after the average time period for
        // the majority of nodes to receive an event in each design has passed
        return false;
    }
}
