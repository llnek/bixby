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
import com.zotohlab.frwk.io.IOUtils;
import com.zotohlab.frwk.io.XData;
import com.zotohlab.frwk.net.ULFileItem;
import com.zotohlab.frwk.net.ULFormItems;
import static com.zotohlab.frwk.util.CoreUtils.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.AttributeKey;
import org.apache.shiro.util.StringUtils;
import io.netty.util.ReferenceCountUtil;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.HashMap;

import static com.zotohlab.frwk.io.IOUtils.streamLimit;
import static com.zotohlab.frwk.netty.NettyFW.*;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public abstract class FormPostDecoder extends AuxHttpDecoder {

  protected FormPostDecoder() {
  }

  protected abstract void handleFormPost(ChannelHandlerContext ctx , Object msg)
    throws IOException;

  protected abstract void handleFormPostChunk(ChannelHandlerContext ctx, Object msg)
    throws IOException;

}


