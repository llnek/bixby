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
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.netty;

import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import com.zotohlab.frwk.net.ULFormItems;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import static com.zotohlab.frwk.netty.NettyFW.*;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public abstract class FormPostFilter extends AuxHttpFilter {

  protected FormPostFilter() {
  }

  public abstract void handleFormPost(ChannelHandlerContext ctx , Object msg)
    throws IOException;

  public abstract void handleFormChunk(ChannelHandlerContext ctx, Object msg)
    throws IOException;

  public void resetAttrs(ChannelHandlerContext ctx) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr(ctx, FORMDEC_KEY);
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);
    delAttr(ctx, FORMITMS_KEY);
    delAttr(ctx, FORMDEC_KEY);
    if (fis != null) {
      fis.destroy();
    }
    if (dc != null) {
      dc.destroy();
    }
    super.resetAttrs(ctx);
  }

}


