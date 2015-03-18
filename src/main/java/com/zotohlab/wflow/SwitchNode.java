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

package com.zotohlab.wflow;

import java.util.HashMap;
import java.util.Map;

import com.zotohlab.wflow.core.Job;

/**
 * @author kenl
 *
 */
public class SwitchNode extends FlowNode {

  private Map<Object,FlowNode> _cs = new HashMap<Object,FlowNode>();
  private SwitchChoiceExpr _expr= null;
  private FlowNode _def = null;

  public SwitchNode withChoices(Map<Object,FlowNode> cs) {
    _cs.putAll(cs);
    return this;
  }

  public SwitchNode(FlowNode s, Activity a) {
    super(s,a);
  }

  public SwitchNode withDef(FlowNode s) {
    _def=s;
    return this;
  }

  public SwitchNode withExpr(SwitchChoiceExpr e) {
    _expr=e;
    return this;
  }

  public Map<Object,FlowNode> choices() { return  _cs; }

  public FlowNode defn() { return  _def; }

  public FlowNode eval(Job j) {
    Object m= _expr.getChoice(j);
    Object c= popClosureArg();
    FlowNode a= null;
    if (m != null) {
      a = _cs.get(m);
    }

    // if no match, try default?
    if (a == null) {
      a=_def;
    }

    if (a != null) {
      a.attachClosureArg(c);
    }

    realize();

    return a;
  }

}


