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
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/


package demo.flows.core;

import static com.zotohlab.wflow.PTask.*;
import com.zotohlab.wflow.core.Job;
import com.zotohlab.wflow.*;
import static java.lang.System.out;

public enum Auth {
;

  public static Activity getAuthMtd(String t) {

    if (t.equals("facebook")) {
      return PTaskWrapper( (p,j,a) -> {
        out.println("-> using facebook to login.\n");
        return null;
      });
    }

    if (t.equals("google+")) {
      return PTaskWrapper( (p,j,a) -> {
        out.println("-> using google+ to login.\n");
        return null;
      });
    }

    if (t.equals("openid")) {
      return PTaskWrapper( (p,j,a) -> {
        out.println("-> using open-id to login.\n");
        return null;
      });
    }

    return PTaskWrapper( (p,j,a) -> {
      out.println("-> using internal db to login.\n");
      return null;
    });

  }

}

