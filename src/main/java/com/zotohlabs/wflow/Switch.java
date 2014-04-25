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

import java.util.HashMap;
import java.util.Map;

/**
 * @author kenl
 *
 */
public class Switch extends Activity {

  public Switch(SwitchChoiceExpr expr) {
    _expr= expr;
  }

  private Map<Object,Activity> _choices= new HashMap<Object,Activity>();
  private SwitchChoiceExpr _expr;
  private Activity _def = null;

  public Switch withChoice(Object matcher, Activity body) {
    _choices.put(matcher, body);
    return this;
  }

  public Switch withDef(Activity a) {
    _def=a;
    return this;
  }

  public FlowPoint reifyPoint(FlowPoint cur) { return new SwitchPoint(cur, this); }

  public void realize(FlowPoint fp) {
    Map<Object,FlowPoint> t= new HashMap<Object,FlowPoint>();
    SwitchPoint p= (SwitchPoint) fp;
    FlowPoint nxt= p.nextPoint();

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
