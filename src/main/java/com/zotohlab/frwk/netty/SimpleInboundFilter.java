/* Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


package com.zotohlab.frwk.netty;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.slf4j.Logger;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCounted;


/**
 * Wrapper so that we can do some reference count debugging.
 *
 * @author kenl
 */
@SuppressWarnings("rawtypes")
public abstract class SimpleInboundFilter extends SimpleChannelInboundHandler {

  public static final Logger TLOG = getLogger(lookup().lookupClass());

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
    super.channelRead(ctx, obj);
    try {
      if (obj instanceof ReferenceCounted) {
        ReferenceCounted c = (ReferenceCounted) obj ;
        TLOG.debug("Object {}: after channelRead() has ref-count = {}" ,
            c.toString(), c.refCnt());
      }
    }
    catch (Throwable t) {}
  }

}


