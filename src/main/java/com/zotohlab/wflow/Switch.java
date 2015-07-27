// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.wflow;

import java.util.HashMap;
import java.util.Map;

/**
 * @author kenl
 *
 */
public class Switch extends Activity {

  public static Switch apply(String name, ChoiceExpr e) {
    return new Switch(name, e);
  }

  public static Switch apply(ChoiceExpr e) {
    return new Switch(e);
  }

  public Switch(String name, ChoiceExpr expr) {
    super(name);
    _expr= expr;
  }

  public Switch(ChoiceExpr expr) {
    this("",expr);
  }

  private Map<Object,Activity> _choices= new HashMap<>();
  private ChoiceExpr _expr;
  private Activity _def = null;

  public Switch withChoice(Object matcher, Activity body) {
    _choices.put(matcher, body);
    return this;
  }

  public Switch withDft(Activity a) {
    _def=a;
    return this;
  }

  public FlowDot reifyDot(FlowDot cur) {
    return new SwitchDot(cur, this);
  }

  public void realize(FlowDot n) {
    Map<Object,FlowDot> t= new HashMap<>();
    SwitchDot p= (SwitchDot) n;
    FlowDot nxt= p.next();

    for (Map.Entry<Object,Activity> en: _choices.entrySet()) {
      t.put(en.getKey(), en.getValue().reify(nxt) );
    }

    p.withChoices(t);

    if (_def != null) {
      p.withDef( _def.reify(nxt));
    }

    p.withExpr(_expr);

  }

}



/**
 *
 * @author kenl
 *
 */
class SwitchDot extends FlowDot {

  private Map<Object,FlowDot> _cs = new HashMap<>();
  private ChoiceExpr _expr= null;
  private FlowDot _def = null;

  public SwitchDot withChoices(Map<Object,FlowDot> cs) {
    _cs.putAll(cs);
    return this;
  }

  public SwitchDot(FlowDot c, Activity a) {
    super(c,a);
  }

  public SwitchDot withDef(FlowDot c) {
    _def=c;
    return this;
  }

  public SwitchDot withExpr(ChoiceExpr e) {
    _expr=e;
    return this;
  }

  public Map<Object,FlowDot> choices() { return  _cs; }

  public FlowDot defn() { return  _def; }

  public FlowDot eval(Job j) {
    Object m= _expr.choice(j);
    FlowDot a= null;

    if (m != null) {
      a = _cs.get(m);
    }

    // if no match, try default?
    if (a == null) {
      a=_def;
    }

    realize();

    return a;
  }

}



