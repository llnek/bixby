/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/


package com.zotohlab.frwk.netty;

import com.google.gson.JsonObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author kenl
 */
public enum ServerSide {
;
  private static Logger _log = LoggerFactory.getLogger(ServerSide.class);
  public static Logger tlog() { return _log; }

  public static ServerBootstrap initServerSide(PipelineConfigurator cfg,
                                               JsonObject options) {
    ServerBootstrap bs= new ServerBootstrap();
    bs.group( new NioEventLoopGroup(), new NioEventLoopGroup() );
    bs.channel(NioServerSocketChannel.class);
    bs.option(ChannelOption.SO_REUSEADDR,true);
    bs.option(ChannelOption.SO_BACKLOG,100);
    bs.childOption(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024);
    bs.childOption(ChannelOption.TCP_NODELAY,true);
    bs.childHandler( cfg.configure(options));
    return bs;
  }

  public static Channel start(ServerBootstrap bs, String host, int port) throws IOException {
    InetAddress ip = (host==null || host.length()==0)
                     ? InetAddress.getLocalHost()
                     : InetAddress.getByName( host);
    Channel ch= null;
    try {
      ch = bs.bind( new InetSocketAddress(ip, port)).sync().channel();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
    tlog().debug("netty-xxx-server: running on host " + ip +  ", port " + port);
    return ch;
  }

  public static void stop(final ServerBootstrap bs, Channel ch) {
    ch.close().addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture ff) {
        EventLoopGroup gc = bs.childGroup();
        EventLoopGroup gp = bs.group();
        if (gp != null) try { gp.shutdownGracefully(); } catch (Throwable e) {}
        if (gc != null) try { gc.shutdownGracefully(); } catch (Throwable e) {}
      }
    });
  }

}

