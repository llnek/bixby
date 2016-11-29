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


package czlab.wabbit.mock.jms;


import java.util.Random;


/**
 * @author Kenneth Leung
 *
 */
public enum MockUtils {
;

  /**
   * @return
   */
  public static String makeNewTextMsg_plus() {
    Random r= new Random();
    int a=r.nextInt(100);
    int b=r.nextInt(100);
    long c= 0L + a + b;
    return "Calc.  " + a + " + " + b + " = " + c;
  }

  /**
   * @return
   */
  public static String makeNewTextMsg_x(){
    Random r= new Random();
    int a=r.nextInt(100);
    int b=r.nextInt(100);
    long c= 1L * a * b;
    return "Calc.  " + a + " * " + b + " = " + c;
  }

}


