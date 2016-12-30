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

package czlab.wabbit.etc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Kenneth Leung
 */
public class BootAppMain {

  /**/
  public static Object invokeStatic(ClassLoader cl,
      String mz, String[] args)
  throws Exception {
    Class<?> z = Class.forName(mz, true, cl);
    Method m= z.getDeclaredMethod("main", String[].class);
    return m.invoke(null, new Object[] {args});
  }

  /**
   */
  public static void main(String[] args) {
    try {
      ClassLoader cl= Thread.currentThread().getContextClassLoader();
      boolean skip=false;
      if (args.length > 2 &&
          "task".equals(args[1])) {
        List<String> t = new ArrayList<>();
        t.addAll(Arrays.asList(args));
        t.remove(0);
        t.remove(0);
        skip=true;
        invokeStatic(cl, "boot.App", t.toArray(new String[0]));
      }
      if (!skip) {
        invokeStatic(cl, "czlab.wabbit.etc.cons", args);
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

}


