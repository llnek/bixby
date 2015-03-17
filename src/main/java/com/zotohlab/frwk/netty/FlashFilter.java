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
// Copyright (c) 2013, Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.netty;

import static com.zotohlab.frwk.netty.NettyFW.*;

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

  private static final String FLASH_POLICY_REQ_WITH_NULL = "<policy-file-request/>\0";
  private static final String FLASH_POLICY_REQ = "<policy-file-request/>";
  private static final char[] FLASH_CHS = FLASH_POLICY_REQ.toCharArray();
  private static final int FLASH_LEN = FLASH_CHS.length;

  private static final AttributeKey<Integer> HINT = AttributeKey.valueOf("flash-hint");
  private static final FlashFilter shared = new FlashFilter();

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(FlashFilter.class.getSimpleName(), shared);
    return pipe;
  }

  protected FlashFilter() {
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf bbuf = (msg instanceof ByteBuf) ? (ByteBuf)msg : null;
    if (bbuf == null || !bbuf.isReadable()) {
      return;
    }

    Integer hint = (Integer) getAttr(ctx,HINT);
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
          finito(ctx, msg, true);
          state= 1;
          break;
        }
      } else {
        finito(ctx,msg,false);
        state= 0;
        break;
      }
    }

    if (state < 0) {
      // not done testing yet...
      if (hint < FLASH_LEN) {
        setAttr(ctx, HINT, hint);
      }
    }

  }

  private void finito(ChannelHandlerContext ctx, Object msg, boolean success) {

    delAttr(ctx, HINT);

    if (success) {

      ctx.writeAndFlush(Unpooled.copiedBuffer(XML,
            CharsetUtil.US_ASCII)).addListener(ChannelFutureListener.CLOSE);

    } else {

      ctx.fireChannelRead(msg);
      ctx.pipeline().remove(this);
    }

  }

}

