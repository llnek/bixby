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

/**
 * @author kenl
 *
 */
public abstract class Join  extends Activity {

  protected int _branches=0;
  protected Activity _body;

  protected Join(Activity b) {
    _body=b;
  }

  protected Join withBranches(int n) {
    _branches=n;
    return this;
  }

}


