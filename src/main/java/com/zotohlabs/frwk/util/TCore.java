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



package com.zotohlabs.frwk.util;

import java.util.concurrent.*;
import org.slf4j.*;
import java.util.concurrent.ExecutorService;

/**
 * @author kenl
 */
public class TCore implements RejectedExecutionHandler {

  private static final Logger _log = LoggerFactory.getLogger(TCore.class);
  public static Logger tlog() { return _log; }

  //private val serialVersionUID = 404521678153694367L

  private ExecutorService _scd;
  private String _id ="";
  private int _tds = 4;

  public TCore (String id, int tds) {
    _tds= Math.max(4,tds);
    _id=id;
  }

  public void start() {
    activate();
  }

  public void stop() {}

  public void dispose() {
    //_scd.shutdownNow()
    _scd.shutdown();
  }

  public void schedule(Runnable work) {
    _scd.execute(work);
  }

  public void rejectedExecution(Runnable r, ThreadPoolExecutor x) {
    //TODO: deal with too much work for the core...
    tlog().error("\"{}\" rejected work - threads/queue are max'ed out" , _id);
  }

  private void activate() {
//    _scd= Executors.newCachedThreadPool( new TFac(_id) )
    _scd= new ThreadPoolExecutor( _tds, _tds, 5000L,
        TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
        new TFac(_id) , this );
    tlog().info("Core \"{}\" activated with threads = {}" , _id , "" + _tds, "");
  }

}

