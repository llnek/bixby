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

package com.zotohlab.frwk.apache;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;

/**
 * @author kenl
 */
public enum ApacheFW {
;

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  /**
   * Detect http redirect from server.
   */
  public static void cfgForRedirect( HttpClientBuilder cli) {
    cli.setRedirectStrategy(new DefaultRedirectStrategy() {
      public boolean isRedirected(HttpRequest request, 
          HttpResponse response, 
          HttpContext context) {
        boolean isRedirect=false;
        try {
          isRedirect = super.isRedirected( request, response, context);
        } catch (ProtocolException e) {
          _log.warn("",e);
        }
        if (!isRedirect) {
          int responseCode = response.getStatusLine().getStatusCode();
          if (responseCode == 301 || responseCode == 302 || 
              responseCode == 307 || responseCode == 308) {
            isRedirect= true;
          }
        }
        return isRedirect;
      }
    });
  }

}


