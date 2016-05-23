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

  public static final Logger TLOG=getLogger(lookup().lookupClass());
  private Object _src;

  public void contextInitialized(ServletContextEvent evt) {

    TLOG.info("WEBContextListener: contextInitialized()");

    ServletContext x= evt.getServletContext();
    String ctx="";
    int m= x.getMajorVersion();
    int n= x.getMinorVersion();

    TLOG.info("Servlet-Context: major version {}, minor version {}", m, n);

    if (m > 2 || ( m==2 && n > 4)) {
      ctx= x.getContextPath();
    }

    try {
      inizAsJ2EE(x, ctx);
    } catch (Throwable e) {
      TLOG.error("", e);
    }

  }

  public void contextDestroyed(ServletContextEvent e) {
    TLOG.info("WEBContextListener: contextDestroyed()");
    if (_src!=null) {
      //_src.container.dispose()
    }
    _src=null;
  }

  private void inizAsJ2EE(ServletContext ctx, String ctxPath) {
    TLOG.info("inizAsJ2EE - setting up context-path: {}", ctxPath);
    _src = ctx.getAttribute("czchhhiojetty") ;
  }

}

