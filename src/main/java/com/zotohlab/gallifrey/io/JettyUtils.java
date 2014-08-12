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

package com.zotohlab.gallifrey.io;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
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
    return new ServerConnector( svr , new HttpConnectionFactory(conf)) ;
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
      rsp.setStatus(code);
      rsp.setContentLength(0);
      rsp.flushBuffer();
    } finally {
      ContinuationSupport.getContinuation(req).complete();
    }
  }

  public static WebAppContext newWebAppContext( final File warPath, final String ctxPath, final String attr, final Object obj) throws IOException {

    String cp = "";

    if (ctxPath != null) {
      cp = ctxPath;
    }

    return new WebAppContext(warPath.toURI().toURL().toString(), cp) {

      public void setContextPath(String s) {
        super.setContextPath(s);
        _scontext.setAttribute(attr, obj);
      }

    };

  }

}

