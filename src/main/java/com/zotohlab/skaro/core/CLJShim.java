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


package com.zotohlab.skaro.core;

import org.projectodd.shimdandy.ClojureRuntimeShim;


/**
 * @author kenl
 */
public class CLJShim {

  /**
   *
   * @param cl
   * @param name
   */
  public static CLJShim newrt(ClassLoader cl, String name) {
    return new CLJShim(ClojureRuntimeShim.newRuntime(cl, name));
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

  private CLJShim(ClojureRuntimeShim s) {
    _shim=s;
  }

  private ClojureRuntimeShim _shim;

}


