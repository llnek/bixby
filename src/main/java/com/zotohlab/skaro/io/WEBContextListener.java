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

package com.zotohlab.skaro.io;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 */
public class WEBContextListener implements ServletContextListener {

  private static Logger _log=getLogger(lookup().lookupClass());
  private Object _src;
  public Logger tlog() { return _log; }

  public void contextInitialized(ServletContextEvent evt) {

    tlog().info("WEBContextListener: contextInitialized()");

    ServletContext x= evt.getServletContext();
    String ctx="";
    int m= x.getMajorVersion();
    int n= x.getMinorVersion();

    tlog().info("Servlet-Context: major version {}, minor version {}", m, n);

    if (m > 2 || ( m==2 && n > 4)) {
      ctx= x.getContextPath();
    }

    try {
      inizAsJ2EE(x, ctx);
    } catch (Throwable e) {
      tlog().error("", e);
    }

  }

  public void contextDestroyed(ServletContextEvent e) {
    tlog().info("WEBContextListener: contextDestroyed()");
    if (_src!=null) {
      //_src.container.dispose()
    }
    _src=null;
  }

  private void inizAsJ2EE(ServletContext ctx, String ctxPath) {
    tlog().info("inizAsJ2EE - setting up context-path: {}", ctxPath);
    _src = ctx.getAttribute("czchhhiojetty") ;
  }

}

