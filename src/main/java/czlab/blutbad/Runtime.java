/**
 * Copyright © 2013-2019, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.blutbad;

import java.lang.reflect.Method;
import java.io.File;
import java.io.Closeable;

/**
 */
public class Runtime {

  /**/
  public static void main(String[] args) {

    if (args.length < 1) {
      System.out.println("usage: Runtime <blutbad-home>");
    }
    else
    try {
      File cwd= new File(System.getProperty("user.dir"));
      File home= new File(args[0]);
      ClassLoader cl= CljPodLoader.newInstance(home, cwd);
      Thread.currentThread().setContextClassLoader(cl);
      Class<?> z= cl.loadClass("czlab.blutbad.base.Cljshim");
      Method m= z.getDeclaredMethod("newrt",ClassLoader.class, String.class);
      Object clj= m.invoke(null, cl, "blutbad-runner");
      m=z.getDeclaredMethod("callEx", String.class, Object[].class);
      m.invoke(clj, "czlab.blutbad.etc.cons/-main", (Object[])args);
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


