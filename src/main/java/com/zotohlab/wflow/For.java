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

package com.zotohlab.wflow;

import com.zotohlab.wflow.core.Job;

/**
 * A For is treated sort of like a while with the test-condition being (i &lt; upperlimit).
 * 
 * @author kenl
 *
 */
public class For extends While {

  private ForLoopCountExpr _loopCntr;

  public For(ForLoopCountExpr loopCount, Activity body) {
    super(body);
    _loopCntr = loopCount;
  }

  public FlowPoint reifyPoint(FlowPoint cur) { return new ForPoint(cur,this); }

  public void realize(FlowPoint fp) {
    ForPoint p= (ForPoint) fp;
    super.realize(fp);
    p.withTest( new ForLoopExpr(p, _loopCntr));
  }

}

/**
 * @author kenl
 *
 */
class ForLoopExpr implements BoolExpr {

  public ForLoopExpr(FlowPoint pt, ForLoopCountExpr cnt) {
    _point = pt;
    _cnt= cnt;
  }

  private ForLoopCountExpr _cnt;
  private FlowPoint _point;

  private boolean _started=false;
  private int _loop=0;

  public boolean evaluate(Job j) {
    try {
      if (!_started) {
        _loop=_cnt.getCount(j);
        _started=true;
      }
      _point.tlog().debug("ForLoopExpr: loop {}", _loop);
      return _loop > 0;
    } finally {
      _loop -= 1;
    }
  }

}


