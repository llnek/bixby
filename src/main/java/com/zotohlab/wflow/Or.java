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

/**
 * @author kenl
 *
 */
public class Or extends Merge {

  public Or(String name, Activity b) {
    super(name,b);
  }

  public Or(Activity b) {
    this("",b);
  }

  public FlowNode reifyNode(FlowNode cur) { 
    return new OrNode(cur, this); 
  }

  public void realize(FlowNode fp) {
    OrNode s= (OrNode) fp;
    if (_body != null) {
      s.withBody( _body.reify(s.next() ));
    }
    s.withBranches(_branches);
  }

}


