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
// Copyright (c) 2013, Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.wflow;

/**
 * @author kenl
 *
 */
public class If extends Conditional {

  public static If apply(BoolExpr expr,Activity thenCode, Activity elseCode) {
    return new If(expr,thenCode,elseCode);
  }

  private Activity _thenCode;
  private Activity _elseCode;

  public If(BoolExpr expr,Activity thenCode, Activity elseCode) {
    super(expr);
    _elseCode= elseCode;
    _thenCode= thenCode;
  }

  public If(BoolExpr expr,Activity thenCode) {
    this(expr, thenCode, null );
  }

  public FlowNode reifyNode(FlowNode cur) { return new IfNode(cur,this); }

  public void realize(FlowNode fp) {
    IfNode s= (IfNode) fp;
    FlowNode np= s.nextNode();
    s.withElse( (_elseCode ==null) ? np : _elseCode.reify(np) );
    s.withThen( _thenCode.reify(np));
    s.withTest( expr());
  }

}


