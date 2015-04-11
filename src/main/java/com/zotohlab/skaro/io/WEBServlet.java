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

import java.io.Serializable;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 *
 */
public class WEBServlet extends HttpServlet implements Serializable {

  private static final long serialVersionUID= -3862652820921092885L;
  
  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }
  private ServletEmitter _src;

  public WEBServlet(ServletEmitter src) {
    _src= src;
  }

  public WEBServlet() {
    this(null);
  }

  public void destroy() {
    tlog().debug("WEBServlet: destroy()");
  }

  public void service(ServletRequest request, ServletResponse response) {
    HttpServletResponse rsp= (HttpServletResponse) response;
    HttpServletRequest req= (HttpServletRequest) request;

    tlog().debug("{}\n{}\n{}",
    "********************************************************************",
      req.getRequestURL(),
    "********************************************************************");

    Continuation c = ContinuationSupport.getContinuation(req);
    if (c.isInitial() ) try {
      _src.doService(req,rsp);
    }
    catch (Throwable e) {
      tlog().error("",e);
    }
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    ServletContext ctx= config.getServletContext();
    Object x= ctx.getAttribute( "czchhhiojetty");
    if (x instanceof ServletEmitter) {
      _src = (ServletEmitter) x;
    }

    try {
      tlog().debug("{}\n{}{}\n{}\n{}{}",
        "********************************************************************",
        "Servlet Container: ",
        ctx.getServerInfo(),
        "********************************************************************",
        "Servlet:iniz() - servlet:" ,
        getServletName());
    }
    catch (Throwable e)
    {}

  }

}



