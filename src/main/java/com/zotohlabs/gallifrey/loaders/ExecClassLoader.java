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

package com.zotohlabs.gallifrey.loaders;

import java.net.URLClassLoader;
import java.io.File;
import java.net.URL;

/**
 * @author kenl
 */
public class ExecClassLoader extends AbstractClassLoader {

  public ExecClassLoader(ClassLoader par) {
    super( new RootClassLoader(par));

    String base=System.getProperty("skaro.home","");
    if (base.length() > 0) { load(base); }
  }

  private void load(String base) {
    File p= new File(base, "exec");

    if (p.exists() && !_loaded) {
      findUrls(p);
    }

    _loaded=true;
  }

  public void configure(String baseDir) {
    if (baseDir != null) {
      load(baseDir);
    }
  }

}

