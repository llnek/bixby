// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package com.zotohlab.frwk.netty;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;

import io.netty.channel.ChannelHandlerContext;


/**
 * Base class for handling inbound messages.
 * 
 * @author kenl
 */
public abstract class AuxHttpFilter extends SimpleInboundFilter {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  public String getName() { 
    return getClass().getSimpleName(); 
  }
  
  @SuppressWarnings("unchecked")
  public void channelReadXXX(ChannelHandlerContext ctx, Object msg) throws Exception {
    channelRead0(ctx, msg);
  }

  public void XXXchannelReadComplete(ChannelHandlerContext ctx)
      throws Exception                    {
    tlog().debug("{}.channelRead - complete called().", getClass().getSimpleName() );
    //super.channelReadComplete(ctx);
    ctx.flush();
  }

}


