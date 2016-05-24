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

  public static final Logger TLOG = getLogger(lookup().lookupClass());
  private ServletEmitter _src;

  public WEBServlet(ServletEmitter src) {
    _src= src;
  }

  public WEBServlet() {
    this(null);
  }

  public void destroy() {
    TLOG.debug("WEBServlet: destroy()");
  }

  public void service(ServletRequest request, ServletResponse response) {
    HttpServletResponse rsp= (HttpServletResponse) response;
    HttpServletRequest req= (HttpServletRequest) request;

    TLOG.debug("{}\n{}\n{}",
    "********************************************************************",
      req.getRequestURL(),
    "********************************************************************");

    Continuation c = ContinuationSupport.getContinuation(req);
    if (c.isInitial() ) try {
      _src.doService(req,rsp);
    }
    catch (Throwable e) {
      TLOG.error("",e);
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
      TLOG.debug("{}\n{}{}\n{}\n{}{}",
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



