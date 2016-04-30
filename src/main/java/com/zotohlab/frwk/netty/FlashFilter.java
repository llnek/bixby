/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.frwk.netty;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

/**
 * Refer to http://www.adobe.com/devnet/articles/crossdomain_policy_file_spec.html
 *
 * @author kenl
 */
public class FlashFilter extends InboundAdapter {

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  @SuppressWarnings("unused")
  private static final String _XML = "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>";
  private static final String XML= "<?xml version=\"1.0\"?>\r\n"
      + "<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">\r\n"
      + "<cross-domain-policy>\r\n"
      + "  <site-control permitted-cross-domain-policies=\"master-only\"/>\r\n"
      + "  <allow-access-from domain=\"*\" to-ports=\"" + "*" + "\" />\r\n"
      + "</cross-domain-policy>\r\n";

  @SuppressWarnings("unused")
  private static final String FLASH_POLICY_REQ_WITH_NULL = "<policy-file-request/>\0";
  private static final String FLASH_POLICY_REQ = "<policy-file-request/>";
  private static final char[] FLASH_CHS = FLASH_POLICY_REQ.toCharArray();
  private static final int FLASH_LEN = FLASH_CHS.length;

  private static final AttributeKey<Integer> HINT = AttributeKey.valueOf("flash-hint");
  public static final FlashFilter shared = new FlashFilter();
  public static final String NAME = "FlashFilter";

  public static ChannelPipeline addBefore(ChannelPipeline pipe, String name) {
    pipe.addBefore(name, NAME, shared);
    return pipe;
  }

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(NAME, shared);
    return pipe;
  }

  protected FlashFilter() {}

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf bbuf = (msg instanceof ByteBuf) ? (ByteBuf)msg : null;
    Channel ch = ctx.channel();

    if (bbuf == null || !bbuf.isReadable()) {
      return;
    }

    tlog().debug("FlashFilter:channelRead called.");

    Integer hint = (Integer) ch.attr(HINT).get();
    if (hint==null) {
      hint= new Integer(0);
    }
    int num= bbuf.readableBytes();
    int pos = bbuf.readerIndex();
    int state= -1;
    int c, nn;

    for (int i =0; i < num; ++i) {
      nn = bbuf.getUnsignedByte(pos+ i);
      c= (int) FLASH_CHS[hint];
      if (c == nn) {
        hint += 1;
        if (hint == FLASH_LEN) {
          //matched!
          finito(ctx, ch, msg, true);
          state= 1;
          break;
        }
      } else {
        finito(ctx, ch, msg,false);
        state= 0;
        break;
      }
    }

    if (state < 0) {
      // not done testing yet...
      if (hint < FLASH_LEN) {
        ch.attr(HINT).set(hint);
      }
    }

  }

  private void finito(ChannelHandlerContext ctx,
      Channel ch,
      Object msg, boolean success) {

    ch.attr(HINT).remove();

    if (success) {

      tlog().debug("FlashFilter:channelRead: replying back to Flash with policy info");
      ctx.writeAndFlush(Unpooled.copiedBuffer(XML,
            CharsetUtil.US_ASCII)).addListener(ChannelFutureListener.CLOSE);

    } else {

      tlog().debug("FlashFilter:channelRead: removing self. finito!");
      ctx.fireChannelRead(msg);
      ctx.pipeline().remove(this);
    }

  }

}

