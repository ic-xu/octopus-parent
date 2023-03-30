/**
 * 
 */
package com.octopus.server.rtmp;

import java.util.Date;

/**
 * @author yama
 *
 */
public class RtmpSession {
	ServerHandler serverHandler;

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getBytesRead()
	 */
	public long getBytesRead() {
		return serverHandler.getBytesRead();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getBytesWritten()
	 */
	public long getBytesWritten() {
		return serverHandler.getBytesWritten();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getClientId()
	 */
	public String getClientId() {
		return serverHandler.getClientId();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getPlayName()
	 */
	public String getPlayName() {
		return serverHandler.getPlayName();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getStreamId()
	 */
	public int getStreamId() {
		return serverHandler.getStreamId();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getBufferDuration()
	 */
	public int getBufferDuration() {
		return serverHandler.getBufferDuration();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getRemoteHost()
	 */
	public String getRemoteHost() {
		return serverHandler.getRemoteHost();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getRemotePort()
	 */
	public int getRemotePort() {
		return serverHandler.getRemotePort();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getCreateTime()
	 */
	public Date getCreateTime() {
		return serverHandler.getCreateTime();
	}

	/**
	 * @return
	 * @see com.octopus.server.rtmp.ServerHandler#getTcURL()
	 */
	public String getTcURL() {
		return serverHandler.getTcURL();
	}
	//
	public void close(){
		serverHandler.close();
	}
	//

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "RtmpSession [getBytesRead()=" + getBytesRead()
				+ ", getBytesWritten()=" + getBytesWritten()
				+ ", getClientId()=" + getClientId() + ", getPlayName()="
				+ getPlayName() + ", getStreamId()=" + getStreamId()
				+ ", getBufferDuration()=" + getBufferDuration()
				+ ", getRemoteHost()=" + getRemoteHost() + ", getRemotePort()="
				+ getRemotePort() + ", getCreateTime()=" + getCreateTime()
				+ ", getTcURL()=" + getTcURL() + "]";
	}
	
}
