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

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;
import java.io.Serializable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;


/**
 * @author kenl
 *
 */
public abstract class AbstractServlet extends HttpServlet implements Serializable {

  public static final Logger TLOG = getLogger(lookup().lookupClass());
  private static final long serialVersionUID= -3862652820921092885L;

  public void destroy() {
    TLOG.debug("AbstractServlet: destroy()");
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    doInit();
  }

  protected abstract void doInit();
  protected AbstractServlet() {}

}



