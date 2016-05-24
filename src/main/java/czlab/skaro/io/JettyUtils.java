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


package czlab.skaro.io;

import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;


/**
 * @author kenl
 */
public class JettyUtils {

  public static ServerConnector makeConnector(Server svr, HttpConfiguration conf) {
    return new ServerConnector(svr , new HttpConnectionFactory(conf)) ;
  }

  public static void replyRedirect(HttpServletRequest req,
                                   HttpServletResponse rsp, String path) throws IOException {
    try {
      rsp.sendRedirect(path);
    }
    finally {
      ContinuationSupport.getContinuation(req).complete();
    }

  }

  public static void replyXXX(HttpServletRequest req, HttpServletResponse rsp, int code) throws IOException {
    try {
      rsp.setContentLength(0);
      rsp.setStatus(code);
      rsp.flushBuffer();
    } finally {
      ContinuationSupport.getContinuation(req).complete();
    }
  }

  public static WebAppContext newWebAppContext( final File warPath, final String ctxPath,
      final String attr, final Object obj) throws IOException {

    String cp = ctxPath == null ?  "" :  ctxPath;

    return new WebAppContext(warPath.toURI().toURL().toString(), cp) {

      public void setContextPath(String s) {
        super.setContextPath(s);
        _scontext.setAttribute(attr, obj);
      }

    };

  }

}


