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

import java.net.MalformedURLException;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Kenneth Leung
 */
public abstract class AbstractClassLoader extends URLClassLoader {

  protected boolean _loaded=false;

  /**
   */
  protected AbstractClassLoader(ClassLoader par) {
    super(new URL[]{}, par);
  }

  /**
   */
  public AbstractClassLoader findUrls(File dir) {
    if (dir.exists() ) {
      dir.listFiles(new FilenameFilter() {
        public boolean accept(File f,String n) {
          if (n.endsWith(".jar")) {
            addUrl(new File(f,n));
          }
          return false;
        }
      });
    }
    return this;
  }

  /**
   */
  public AbstractClassLoader addUrl(File f) {
    if (f.exists())
    try {
      addURL( f.toURI().toURL() );
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return this;
  }

}


