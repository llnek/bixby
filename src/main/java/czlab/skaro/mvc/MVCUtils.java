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


package czlab.skaro.mvc;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author Kenneth Leung
 */
public enum MVCUtils {
;

  private static ThreadLocal<SimpleDateFormat> _fmt = new ThreadLocal<SimpleDateFormat>() {

    public SimpleDateFormat initialValue() {
      SimpleDateFormat f= new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
      f.setTimeZone(TimeZone.getTimeZone("GMT"));
      return f;
    }

  };


  public static SimpleDateFormat getSDF() {
    return _fmt.get();
  }

}



