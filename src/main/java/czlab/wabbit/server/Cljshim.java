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

package czlab.wabbit.server;

import clojure.lang.Symbol;
import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Var;

/**
 * @author Kenneth Leung
 */
public class Cljshim implements java.io.Closeable {

  /**
   *
   * @param cl
   * @param name
   */
  public static Cljshim newrt(ClassLoader cl, String name) {
    return new Cljshim(cl,name);
  }

  /**
   */
  public void require(String... namespaces) {
    for (String ns : namespaces) {
      _require.invoke(Symbol.create(ns));
    }
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

    IFn fn= (IFn) varIt(func);
    int cnt = args.length;
    Object ret=null;

    switch (cnt) {
      case 0:ret= fn.invoke(); break;
      case 1:ret= fn.invoke(args[0]); break;
      case 2:ret= fn.invoke(args[0], args[1]); break;
      case 3:ret= fn.invoke(args[0], args[1],args[2]); break;
      case 4:ret= fn.invoke(args[0], args[1],args[2], args[3]); break;
      case 5:ret= fn.invoke(args[0], args[1],args[2], args[3],args[4]); break;
      case 6:ret= fn.invoke(args[0], args[1],args[2], args[3],args[4], args[5]); break;
      default:
      throw new IllegalArgumentException("too many arguments to invoke");
    }

    return ret;
  }

  /**/
  public Object call(String func) {
    return this.callEx(func);
  }

  /**
   */
  public void close() {
  }

  /**
   */
  private Var varIt(String fname) {
    try {
      Var var = (Var) _resolve.invoke(Symbol.create(fname));
      if (var == null) {
        String[] ss = fname.split("/");
        _require.invoke(Symbol.create(ss[0]));
        var = RT.var(ss[0], ss[1]);
      }
      if (var == null) {
        throw new Exception("not found");
      }
      return var;
    }
    catch (Exception e) {
      throw new RuntimeException("can't load var: " + fname, e);
    }
  }

  /**
   */
  private Cljshim(ClassLoader cl, String name) {
    _require = RT.var("clojure.core", "require");
    _resolve = RT.var("clojure.core", "resolve");
    _loader=cl;
    _name=name;
  }

  private ClassLoader _loader;
  private Var _require;
  private Var _resolve;
  private String _name;

}


