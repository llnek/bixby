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

package com.zotohlab.gallifrey.io;

import java.io.IOException;
import java.io.Serializable;

import org.slf4j.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * @author kenl
 *
 */
public abstract class AbstractServlet extends HttpServlet implements Serializable {

  private static Logger _log = LoggerFactory.getLogger(AbstractServlet.class);
  private static final long serialVersionUID= -3862652820921092885L;
  public Logger tlog() { return _log; }

  protected AbstractServlet() {
  }

  public void destroy() {
    tlog().debug("AbstractServlet: destroy()");
  }

  protected abstract void doInit();

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    doInit();
  }

}



