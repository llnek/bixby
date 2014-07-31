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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

/**
 * Refer to http://www.adobe.com/devnet/articles/crossdomain_policy_file_spec.html
 *
 * @author kenl
 */
@ChannelHandler.Sharable
public class FlashFilter extends ChannelInboundHandlerAdapter {

  private static final String _XML = "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>";
  private static final String XML= "<?xml version=\"1.0\"?>\r\n"
      + "<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">\r\n"
      + "<cross-domain-policy>\r\n"
      + "  <site-control permitted-cross-domain-policies=\"master-only\"/>\r\n"
      + "  <allow-access-from domain=\"*\" to-ports=\"" + "*" + "\" />\r\n"
      + "</cross-domain-policy>\r\n";

  private static final String FLASH_POLICY_REQ = "<policy-file-request/>\0";
  private static final AttributeKey HINT = AttributeKey.valueOf("FlashHint");

  private static final FlashFilter shared = new FlashFilter();
  public static FlashFilter getInstance() {
    return shared;
  }

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(FlashFilter.class.getSimpleName(), shared);
    return pipe;
  }

  public FlashFilter() {
  }

  @SuppressWarnings("unchecked")
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf bbuf = (msg instanceof ByteBuf) ? (ByteBuf)msg : null;
    if (bbuf == null || !bbuf.isReadable()) {
      return;
    }

    Integer b1 = (Integer) ctx.attr(HINT).get();
    int num= bbuf.readableBytes();
    int pos = bbuf.readerIndex();
    int nn;

    if (num > 0) {
      nn = bbuf.getUnsignedByte(pos++);
      --num;
      if (b1 == null) {
        // first byte
        if (nn != '<') {
          finito(ctx, msg);
          bbuf=null;
        } else {
          b1= new Integer(nn);
          ctx.attr(HINT).set(b1);
        }
      } else {
        // check 2nd byte
        if (nn != 'p') {
          finito(ctx, msg);
        } else {
          ctx.writeAndFlush(Unpooled.copiedBuffer(XML, CharsetUtil.UTF_8)).addListener(ChannelFutureListener.CLOSE);
        }
      }
    }
    if (num > 0) {
      nn = bbuf.getUnsignedByte(pos++);
      --num;
      if (b1 != null) {
        // check 2nd byte
        if (nn != 'p') {
          finito(ctx, msg);
        } else {
          ctx.writeAndFlush(Unpooled.copiedBuffer(XML, CharsetUtil.UTF_8)).addListener(ChannelFutureListener.CLOSE);
        }
      }
    }
  }

  private void finito(ChannelHandlerContext ctx, Object msg) {
    ctx.fireChannelRead(msg);
    ctx.pipeline().remove(this);
  }

}

