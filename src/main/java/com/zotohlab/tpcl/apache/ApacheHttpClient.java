/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


package com.zotohlab.tpcl.apache;

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
 * Detect http redirect from server.  This is meant to fix a bug
 * in the apache http client.
 * @author kenl
 */
public enum ApacheHttpClient {
;

  public static final Logger TLOG=getLogger(lookup().lookupClass());

  public static void cfgForRedirect( HttpClientBuilder cli) {
    cli.setRedirectStrategy(new DefaultRedirectStrategy() {
      public boolean isRedirected(HttpRequest request,
          HttpResponse response,
          HttpContext context) {
        boolean isRedirect=false;
        try {
          isRedirect = super.isRedirected( request, response, context);
        } catch (ProtocolException e) {
          TLOG.warn("",e);
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


