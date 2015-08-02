// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.skaro.loaders;

import java.io.File;

/**
 * @author kenl
 */
public class RootClassLoader extends AbstractClassLoader {

  private File _baseDir=null;

  public RootClassLoader(ClassLoader par) {
    super(par);
    configure(new File(System.getProperty("skaro.home","")));
  }

  public File baseDir() { return _baseDir; }

  public void configure(File baseDir) {
    if (baseDir != null) {
      load( baseDir);
    }
  }

  private void load(File baseDir) {

    File p= new File(baseDir, "patch");
    File b= new File(baseDir, "lib");

    if (!_loaded) {
      findUrls(p).findUrls(b);
    }

    _baseDir=baseDir;
    _loaded=true;
  }

}


