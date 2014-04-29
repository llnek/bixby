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

package com.zotohlabs.gallifrey.io;

import java.io.IOException;
import java.io.Serializable;

import org.slf4j.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;
import com.zotohlabs.frwk.io.XData;

/**
 * @author kenl
 *
 */
public class WEBServlet extends HttpServlet implements Serializable {

  private static Logger _log = LoggerFactory.getLogger(WEBServlet.class);
  private static final long serialVersionUID= -3862652820921092885L;
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



