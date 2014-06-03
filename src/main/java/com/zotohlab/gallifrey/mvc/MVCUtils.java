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

package com.zotohlab.gallifrey.mvc;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author kenl
 */
public enum MVCUtils {
;

  private static ThreadLocal<SimpleDateFormat> _fmt = new ThreadLocal<SimpleDateFormat>() {

    public SimpleDateFormat initialValue() {
      SimpleDateFormat f= new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
      f.setTimeZone(TimeZone.getTimeZone("GMT"));
      return f;
    }

  };

  public static SimpleDateFormat getSDF() { return _fmt.get(); }

}



