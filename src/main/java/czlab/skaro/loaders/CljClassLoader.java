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

package czlab.skaro.loaders;

import java.io.File;

/**
 * @author Kenneth Leung
 */
public class CljClassLoader extends BaseClassLoader {

  /**/
  public static CljClassLoader newLoader(File homeDir, File appDir) {
    CljClassLoader c= new CljClassLoader();
    c.configure(homeDir,appDir);
    return c;
  }

  /**
   */
  private CljClassLoader() {
    super(null);
  }

  /**/
  private void cfg0(File baseDir) {
    File p= new File(baseDir, "patch");
    File b= new File(baseDir, "lib");
    File d= new File(baseDir, "dist");
    findUrls(p).findUrls(d).findUrls(b);
  }

  /**
   */
  private CljClassLoader configure(File homeDir, File appDir) {
    File s= new File(appDir, "src/main/clojure");
    File j= new File(appDir, "build/j");
    File c= new File(appDir, "build/c");
    File d= new File(appDir, "build/d");
    File p= new File(appDir, "patch");
    File b= new File(appDir, "target");

    c.mkdirs();
    if (!_loaded) {
      findUrls(p);
      addUrl(s);
      addUrl(j);
      addUrl(c);
      findUrls(d);
      findUrls(b);
      cfg0(homeDir);
    }
    _loaded=true;
    return this;
  }

}


