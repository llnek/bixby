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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;

import java.util.Map;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 */
public abstract class PipelineConfigurator {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }
  
  public ChannelHandler configure(final Map<?,?> options) {
    return new ChannelInitializer<Channel>() {
      public void initChannel(Channel ch) {
        mkInitor(ch.pipeline(), options);
      }
    };
  }
  
  protected void mkInitor(ChannelPipeline pipe, Map<?,?> options) {
    assemble(pipe, options);
    NettyFW.dbgPipelineHandlers(pipe);
  }

  protected abstract void assemble(ChannelPipeline pipe, Map<?,?> options);

  protected PipelineConfigurator() {}
}


