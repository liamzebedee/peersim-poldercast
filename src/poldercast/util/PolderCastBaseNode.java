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
import java.util.HashSet;

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

    public HashSet<NodeProfile> getUnionOfAllViews() {
        HashSet<NodeProfile> union = new HashSet<NodeProfile>();
        if(this.getCyclonProtocol() != null) union.addAll(this.getCyclonProtocol().getRoutingTableCopy());
        if(this.getVicinityProtocol() != null) union.addAll(this.getVicinityProtocol().getRoutingTableCopy());
        //if(this.getRingsProtocol() != null) union.addAll(this.getRingsProtocol().getLinearView());
        return union;
    }

    public String toString() {
        return this.nodeProfile.toString();
    }

    @Override
    public void publish(ID topic, byte[] event) {
        this.getRingsProtocol().publishEvent(this, topic, event);
    }

    @Override
    public void subscribe(ID topic) {
        this.nodeProfile.addSubscription(topic);
        this.getRingsProtocol().changeInSubscriptions(this);
    }
}
