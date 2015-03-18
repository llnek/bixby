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

package com.zotohlab.gallifrey.loaders;

import java.io.File;

/**
 * @author kenl
 */
public class RootClassLoader extends AbstractClassLoader {

  public RootClassLoader(ClassLoader par) {
    super(par);

    String base=System.getProperty("skaro.home","");
    if (base.length() > 0) { load(base); }
  }

  public void configure(String baseDir) {
    if (baseDir != null) {
      load( baseDir);
    }
  }

  private void load(String baseDir) {

    File d= new File(baseDir, "dist/exec");
    File p= new File(baseDir, "patch");
    File b= new File(baseDir, "lib");

    if (!_loaded) {
      findUrls(p).findUrls(d).findUrls(b);
    }

    _loaded=true;
  }

}


