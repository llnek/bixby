/*??
*
* Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
*
* This library is distributed in the hope that it will be useful
* but without any warranty; without even the implied warranty of
* merchantability or fitness for a particular purpose.
*
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
*
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
*
 ??*/

package com.zotohlabs.frwk.netty;

import com.google.gson.JsonObject;
import io.netty.channel.*;

/**
 * @author kenl
 */
public abstract class PipelineConfigurator {

  protected PipelineConfigurator() {
  }

  public ChannelHandler configure(JsonObject options) {
    return new ChannelInitializer() {
      public void initChannel(Channel ch) {
        assemble(ch.pipeline(), options);
      }
    };
  }

  protected abstract void assemble(ChannelPipeline pipe, JsonObject options);

}


