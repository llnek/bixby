/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.wabbit.server;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

/**
 * @author Kenneth Leung
 */
@SuppressWarnings("unused")
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
    _refer = RT.var("clojure.core", "refer");
    _loader=cl;
    _name=name;
  }

  private ClassLoader _loader;
  private Var _require;
  private Var _refer;
  private Var _resolve;
  private String _name;

}


