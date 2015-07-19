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

package com.zotohlab.skaro.loaders;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author kenl
 */
public abstract class AbstractClassLoader extends URLClassLoader {

  protected boolean _loaded=false;

  protected AbstractClassLoader(ClassLoader par) {
    super(new URL[]{}, par);
  }

  public AbstractClassLoader findUrls(File dir) {
    if (dir.exists() ) {
      dir.listFiles( new FilenameFilter() {
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

  public AbstractClassLoader addUrl(File f) {
    if (f.exists()) try {
      addURL( f.toURI().toURL() );
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
    return this;
  }

}

