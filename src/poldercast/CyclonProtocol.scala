package poldercast

import peersim.edsim.EDProtocol
import peersim.cdsim.CDProtocol
import peersim.core.Node

class CyclonProtocol(s: String) extends EDProtocol with CDProtocol {
  def nextCycle(node: Node, protocolID: Int): Unit = {}
  def processEvent(node: Node, pid: Int, event: Object): Unit = {}

  override def clone: Object = {
    var cyclonProtocol: CyclonProtocol = null
    try {
      cyclonProtocol = super.clone.asInstanceOf[CyclonProtocol]
    } catch {
      case e : CloneNotSupportedException => {
        e.printStackTrace();
      }
    }
    cyclonProtocol
  }

/*  public Object clone()
  {
    Gossip gossip = null;
    try {
      gossip = (Gossip)super.clone(); } catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }*/
}
