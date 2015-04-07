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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCounted;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 */
@SuppressWarnings("rawtypes")
public abstract class SimpleInboundFilter extends SimpleChannelInboundHandler {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
    try {
      super.channelRead(ctx, obj);
      if (obj instanceof ReferenceCounted) {
        ReferenceCounted c = (ReferenceCounted) obj ;
        String os = "???";
        try {
          os= c.toString();
        } catch (Throwable t) {}
        tlog().debug("Object " + os + " : after channelRead() has ref-count = " + c.refCnt());
      }
    }
    catch (Exception e) {
      //tlog().error("Oh No! contexthandler=" + ctx.name() + "\n", e);
      throw e;
    }
  }

}
