package tests.controls;

public class MemoryStatistics extends BaseControl {
    public MemoryStatistics(String prefix) {
        super(prefix);
    }

    @Override
    public boolean execute() {
        // Start recording data after Nth cycle:
        //  - measure how much memory is consumed by the routing table on average
        //  - measure how a node's subscription size affects its memory consumption
        return false;
    }
}
