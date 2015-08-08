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

package com.zotohlab.skaro.etc;

import org.projectodd.shimdandy.ClojureRuntimeShim;


/**
 * @author kenl
 */
public class CliMain {

  /**
   * 
   * @param cl
   * @param name
   */
  public static CliMain newrt(ClassLoader cl, String name) {
    return new CliMain(ClojureRuntimeShim.newRuntime(cl, name));
  }

  /**
   * 
   * @param func
   * @param args
   * @return
   */
  public Object callEx(String func, Object... args) {
    if (func==null || func.length()==0) {
      return null;
    }
    int cnt = args.length;
    Object ret=null;
    switch (cnt) {
    case 0:ret= _shim.invoke(func); break;
    case 1:ret= _shim.invoke(func, args[0]); break;
    case 2:ret= _shim.invoke(func, args[0], args[1]); break;
    case 3:ret= _shim.invoke(func,args[0], args[1],args[2]); break;
    case 4:ret= _shim.invoke(func,args[0], args[1],args[2], args[3]); break;
    case 5:ret= _shim.invoke(func,args[0], args[1],args[2], args[3],args[4]); break;
    case 6:ret= _shim.invoke(func,args[0], args[1],args[2], args[3],args[4], args[5]); break;
    default:
      throw new IllegalArgumentException("too many arguments to invoke");
    }
    return ret;
  }
  
  public Object call(String func) {
    return this.callEx(func);
  }
  
  private CliMain(ClojureRuntimeShim s) {
    _shim=s;    
  }
	
  private ClojureRuntimeShim _shim;

}


