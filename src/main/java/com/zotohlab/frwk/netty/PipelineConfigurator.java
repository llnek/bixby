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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;


/**
 * Use to configure the Netty Pipeline.
 *
 * @author kenl
 */
public abstract class PipelineConfigurator {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  public ChannelHandler configure(final Object  options) {
    return new ChannelInitializer<Channel>() {
      public void initChannel(Channel ch) {
        mkInitor(ch.pipeline(), options);
      }
    };
  }

  protected abstract void assemble(ChannelPipeline pipe, Object  options);

  protected void mkInitor(ChannelPipeline pipe, Object  options) {
    assemble(pipe, options);
    tlog().debug("ChannelPipeline: assembled handlers= {}", StringUtils.join(pipe.names(), "|"));
  }

  protected PipelineConfigurator() {}

}


