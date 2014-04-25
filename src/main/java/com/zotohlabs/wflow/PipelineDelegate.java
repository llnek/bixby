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

package com.zotohlabs.wflow;

import com.zotohlabs.frwk.util.CoreUtils.*;
import org.slf4j.*;
import com.zotohlabs.frwk.util.Schedulable;
import java.util.concurrent.atomic.AtomicLong;
import com.zotohlabs.wflow.core.Job;
import com.zotohlabs.frwk.core.Startable;
import com.zotohlabs.frwk.server.ServerLike;


public interface PipelineDelegate {

  public Activity getStartActivity(Pipeline p);
  public void onStop(Pipeline p);
  public Activity onError(Throwable e, FlowPoint cur);

}

