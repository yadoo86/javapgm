/* A peer in the context of the sock is another party on the network sending PGM
 * packets.
 */
package hk.miru.javapgm;

import static hk.miru.javapgm.Preconditions.checkNotNull;

import java.net.InetAddress;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Peer {
       
        private Logger LOG = LogManager.getLogger (Peer.class.getName());
    
	private TransportSessionId tsi = null;
	private InetAddress group_nla = null;
	private InetAddress nla = null, local_nla = null;
	private long lastPacketTimestamp = 0;
	private SequenceNumber spm_sqn = null;
	private ReceiveWindow window;
	private boolean hasPendingLinkData = false;
	private long lastCommit = 0;
	private long lostCount = 0;
	private long lastCumulativeLosses = 0;
	private long spmrExpiration = 0;
	private long expiration = 0;

	public Peer (
                Socket sock,
		TransportSessionId tsi,
                InetAddress src_addr,
                InetAddress dst_addr,
                long now
		)
	{
/* Pre-conditions */
                checkNotNull (sock);
                checkNotNull (tsi);
                checkNotNull (src_addr);
                checkNotNull (dst_addr);
                
                if (LOG.isDebugEnabled())
                        LOG.debug ("newPeer (tsi:{})", tsi);
                
                this.expiration = now + sock.getPeerExpiration();
		this.tsi = tsi;
                this.group_nla = dst_addr;
                this.local_nla = src_addr;
		this.spm_sqn = SequenceNumber.ZERO;
                
		this.window = new ReceiveWindow (tsi,
                                                 sock.getMaximumTpdu(),
                                                 sock.getReceiveWindowSizeInSequenceNumbers(),   /* RXW_SQNS */
                                                 sock.getReceiveWindowSizeInSeconds(),    /* RXW_SECS */
                                                 sock.getMaximumReceiveRate());     /* RXW_MAX_RTE */
                this.spmrExpiration = now + sock.getSpmRequestExpiration();
	}

	public ReceiveWindow.Returns add (SocketBuffer skb, long now, long nak_rb_expiry) {
                checkNotNull (skb);
		return window.add (skb, now, nak_rb_expiry);
	}

	public int update (SequenceNumber txw_lead, SequenceNumber txw_trail, long now, long nak_rb_expiry) {
                checkNotNull (txw_lead);
                checkNotNull (txw_trail);
		return window.update (txw_lead, txw_trail, now, nak_rb_expiry);
	}

	public int read (List<SocketBuffer> skbs) {
		return this.window.read (skbs);
	}

	public TransportSessionId getTransportSessionId() {
		return this.tsi;
	}

	public int getSourcePort() {
		return this.tsi.getSourcePort();
	}
        
        public InetAddress getGroupAddress() {
                return this.group_nla;
        }

	public void setGroupAddress (InetAddress group_nla) {
                checkNotNull (group_nla);
		this.group_nla = group_nla;
	}

	public InetAddress getNetworkLayerAddress() {
		return this.nla;
	}

	public void setNetworkLayerAddress (InetAddress nla) {
                checkNotNull (nla);
		this.nla = nla;
	}

	public boolean hasValidNla() {
		return (null != this.nla);
	}

	public void setLastPacketTimestamp (long lastPacketTimestamp) {
		this.lastPacketTimestamp = lastPacketTimestamp;
	}

	public void setSpmSequenceNumber (SequenceNumber spm_sqn) {
                checkNotNull (spm_sqn);
		this.spm_sqn = spm_sqn;
	}

	public SequenceNumber getSpmSequenceNumber() {
		return this.spm_sqn;
	}

/* Edge triggered has receiver pending events
 */
	public boolean hasPending() {
		if (!hasPendingLinkData() && this.window.hasEvent()) {
			this.window.clearEvent();
			return true;
		}
		return false;
	}

	public void setPendingLinkData() {
		this.hasPendingLinkData = true;
	}

	public void clearPendingLinkData() {
		this.hasPendingLinkData = false;
	}

	public boolean hasPendingLinkData() {
		return this.hasPendingLinkData;
	}

	public boolean hasLastCommit() {
		return this.lastCommit > 0;
	}

	public long getLastCommit() {
		return this.lastCommit;
	}

	public void setLastCommit (long lastCommit) {
		this.lastCommit = lastCommit;
	}

	public void removeCommit() {
		this.window.removeCommit();
	}

	public boolean hasCommitData() {
		return this.window.hasCommitData();
	}

	public boolean hasDataLoss() {
		return (this.lastCumulativeLosses != this.window.getCumulativeLosses());
	}

	public void clearDataLoss() {
		this.lostCount = this.window.getCumulativeLosses() - this.lastCumulativeLosses;
		this.lastCumulativeLosses = this.window.getCumulativeLosses();
	}

	public void markLost (SequenceNumber sequence) {
                checkNotNull (sequence);
		this.window.markLost (sequence);
	}

	public boolean hasSpmrExpiration() {
		return (this.spmrExpiration > 0);
	}

	public long getSpmrExpiration() {
		return this.spmrExpiration;
	}

	public void clearSpmrExpiration() {
		this.spmrExpiration = 0;
	}

	public Queue<SocketBuffer> getNakBackoffQueue() {
		return this.window.getNakBackoffQueue();
	}

	public long firstNakBackoffExpiration() {
		return this.window.firstNakBackoffExpiration();
	}

	public void setBackoffState (SocketBuffer skb) {
                checkNotNull (skb);
		this.window.setBackoffState (skb);
	}

	public Queue<SocketBuffer> getWaitNakConfirmQueue() {
		return this.window.getWaitNakConfirmQueue();
	}

	public long firstNakRepeatExpiration() {
		return this.window.firstNakRepeatExpiration();
	}

	public void setWaitNakConfirmState (SocketBuffer skb) {
                checkNotNull (skb);
		this.window.setWaitNakConfirmState (skb);
	}

	public Queue<SocketBuffer> getWaitDataQueue() {
		return this.window.getWaitDataQueue();
	}

	public long firstRepairDataExpiration() {
		return this.window.firstRepairDataExpiration();
	}

	public long getExpiration() {
		return this.expiration;
	}

	public void setExpiration (long expiration) {
		this.expiration = expiration;
	}

        @Override
	public String toString() {
		return	 "{ " +
			  "\"tsi\": \"" + this.tsi + "\"" +
			", \"group\": " + this.group_nla + "" +
			", \"lastPacketTimestamp\": " + this.lastPacketTimestamp + "" +
			", \"window\": " + this.window + "" +
			 " }";
	}
}

/* eof */