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

package com.zotohlab.mock.jms;

import java.util.Random;


/**
 * @author kenl
 *
 */
public enum MockUtils {
;

  /**
   * @return
   */
  public static String makeNewTextMsg_plus() {
    Random r= new Random();
    int a=r.nextInt(100);
    int b=r.nextInt(100);
    long c= 0L + a + b;
    return "Calc.  " + a + " + " + b + " = " + c;
  }

  /**
   * @return
   */
  public static String makeNewTextMsg_x(){
    Random r= new Random();
    int a=r.nextInt(100);
    int b=r.nextInt(100);
    long c= 1L * a * b;
    return "Calc.  " + a + " * " + b + " = " + c;
  }

}

