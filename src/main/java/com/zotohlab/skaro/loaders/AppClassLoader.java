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

//import org.apache.commons.io.{FileUtils=>FUT}
import java.io.File;

/**
 * @author kenl
 */
public class AppClassLoader extends AbstractClassLoader {

  
  public AppClassLoader(ExecClassLoader par) {
    super(par);
  }

  public void configure(String appDir) {
    File c= new File(appDir, "POD-INF/classes");
    File p= new File(appDir, "POD-INF/patch");
    File b= new File(appDir, "POD-INF/lib");
   
    if (!_loaded) {
      findUrls(p);
      addUrl(c);
      findUrls(b);
      
//      if ( new File(appDir, "WEB-INF").exists() ) {
//        addUrl( new File(appDir, "WEB-INF/classes"));
//        findUrls(new File(appDir, "WEB-INF/lib"));
//      }
    }
    _loaded=true;
  }

}

