package poldercast.util;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.GeneralNode;
import poldercast.protocols.CyclonProtocol;
import poldercast.protocols.RingsProtocol;
import poldercast.protocols.VicinityProtocol;
import tests.util.BaseNode;

import java.util.ArrayList;
import java.util.HashMap;

public class PolderCastBaseNode extends BaseNode  {
    private NodeProfile nodeProfile;

    public PolderCastBaseNode(String configPrefix) {
        super(configPrefix);
    }

    public void initModules() {
        this.nodeProfile = new NodeProfile(this, new ID(CommonState.r), new HashMap<ID, Byte>());
    }

    public NodeProfile getNodeProfile() { return this.nodeProfile; }

    public CyclonProtocol getCyclonProtocol() {
        return (CyclonProtocol) this.getProtocol(Configuration.lookupPid(CyclonProtocol.CYCLON));
    }

    public VicinityProtocol getVicinityProtocol() {
        return (VicinityProtocol) this.getProtocol(Configuration.lookupPid(VicinityProtocol.VICINITY));
    }

    public RingsProtocol getRingsProtocol() {
        return (RingsProtocol) this.getProtocol(Configuration.lookupPid(RingsProtocol.RINGS));
    }

    public ArrayList<NodeProfile> getUnionOfAllViews() {
        ArrayList<NodeProfile> union = new ArrayList<NodeProfile>();
        union.addAll(this.getCyclonProtocol().routingTable);
        union.addAll(this.getVicinityProtocol().routingTable);
        union.addAll(this.getRingsProtocol().getLinearView());
        return union;
    }

    public String toString() {
        return this.nodeProfile.toString();
    }

    @Override
    public void publish(ID topic, Object event) {
        this.getRingsProtocol().publishEvent(topic, event);
    }

    @Override
    public void subscribe(ID topic) {
        this.nodeProfile.addSubscription(topic);
        this.getRingsProtocol().changeInSubscriptions(this);
    }
}
