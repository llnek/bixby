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

import java.lang.reflect.Method;
import java.io.File;
import java.io.Closeable;

/**
 * @author Kenneth Leung
 */
public class Runtime {

  /**/
  public static void main(String[] args) {

    if (args.length < 1) {
      System.out.println("usage: Runtime <wabbit-home>");
    }
    else
    try {
      File cwd= new File(System.getProperty("user.dir"));
      File home= new File(args[0]);
      ClassLoader cl= CljPodLoader.newInstance(home, cwd);
      Thread.currentThread().setContextClassLoader(cl);
      Class<?> z= cl.loadClass("czlab.wabbit.server.Cljshim");
      Method m= z.getDeclaredMethod("newrt",ClassLoader.class, String.class);
      Object clj= m.invoke(null, cl, "wabbit-runner");
      m=z.getDeclaredMethod("callEx", String.class, Object[].class);
      m.invoke(clj, "czlab.wabbit.etc.cons/-main", (Object[])args);
      Closeable c = (Closeable) clj;
      c.close();
      //System.out.println("Runtime has stopped.");
    }
    catch (Throwable t) {
      t.printStackTrace();
    }
    finally {
    }
  }

}


