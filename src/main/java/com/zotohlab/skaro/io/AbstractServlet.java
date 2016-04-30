/*
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.skaro.io;

import java.io.Serializable;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 *
 */
public abstract class AbstractServlet extends HttpServlet implements Serializable {

  private static final long serialVersionUID= -3862652820921092885L;

  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }


  public void destroy() {
    tlog().debug("AbstractServlet: destroy()");
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    doInit();
  }

  protected abstract void doInit();
  protected AbstractServlet() {}

}



