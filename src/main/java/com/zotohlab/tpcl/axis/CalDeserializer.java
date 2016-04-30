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


package com.zotohlab.tpcl.axis;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.namespace.QName;

import org.apache.axis.encoding.ser.CalendarDeserializer;
import org.apache.axis.i18n.Messages;

/**
 * @author kenl
 */
// axis gives the  wrong timezone, this fixes it
// and get the right timezone
public class CalDeserializer extends CalendarDeserializer {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  private static final long serialVersionUID = 1L;

  public CalDeserializer(Class<?> javaType, QName xmlType) {
    super(javaType,xmlType);
  }

  public Object makeValue(String source) {
    try {
      return _makeValue(source);
    }
    catch (Throwable e) {
      tlog().error("", e);
      return null;
    }
  }

  private Object _makeValue(String srcString) {
    Calendar calendar = Calendar.getInstance();
    String source =srcString;
    boolean bc = false;

    if (source == null || source.length() == 0)
        throw new NumberFormatException(Messages.getMessage("badDateTime00"));

    if (source.charAt(0) == '+')
        source = source.substring(1);

    if (source.charAt(0) == '-')  {
        source = source.substring(1);
        bc = true;
    }

    if (source.length() < 19)
      throw new NumberFormatException(Messages.getMessage("badDateTime00"));

    if (source.charAt(4) != '-' || source.charAt(7) != '-' || source.charAt(10) != 'T')
      throw new NumberFormatException(Messages.getMessage("badDate00"));

    if (source.charAt(13) != ':' || source.charAt(16) != ':')
      throw new NumberFormatException(Messages.getMessage("badTime00"));

    TimeZone tz= TimeZone.getTimeZone("GMT");
    String tzStr="";
    int pos=0;
    Date date= null;
    SimpleDateFormat zulu = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    try {
      zulu.setTimeZone(TimeZone.getTimeZone("GMT"));
      date = zulu.parse(source.substring(0, 19) + ".000Z");
      pos= source.indexOf('+', 19);
      if (pos != -1) {
        tzStr= source.substring(pos);
      }
      pos= source.indexOf('-', 19);
      if (pos != -1) {
        tzStr= source.substring(pos);
      }
      pos=tzStr.indexOf(':');
      if (pos != -1) {
        tzStr= tzStr.substring(0,pos) + tzStr.substring(pos+1);
      }
    }
    catch (Throwable e) {
      throw new NumberFormatException(e.toString());
    }

    pos = 19;
    if (pos < source.length() && source.charAt(pos) == '.') {
      int milliseconds = 0;
      pos += 1;
      int start = pos;

      while ( pos < source.length() && Character.isDigit(source.charAt(pos))) {
        pos += 1;
      }

      String decimal = source.substring(start, pos);
      if (decimal.length() == 3) {
        milliseconds = Integer.parseInt(decimal) ;
      }
      else
      if (decimal.length() < 3) {
        milliseconds = Integer.parseInt((decimal + "000").substring(0, 3));
      }
      else {
        milliseconds = Integer.parseInt(decimal.substring(0, 3));
        if (decimal.charAt(3) >= '5') {
          milliseconds += 1;
        }
      }
      date.setTime(date.getTime() + milliseconds);
    }

    if (pos + 5 < source.length() &&
        (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
      if (!Character.isDigit(source.charAt(pos + 1)) ||
          !Character.isDigit(source.charAt(pos + 2)) ||
          source.charAt(pos + 3) != ':' ||
          !Character.isDigit(source.charAt(pos + 4)) ||
          !Character.isDigit(source.charAt(pos + 5)))
            throw new NumberFormatException(Messages.getMessage("badTimezone00"));

        int hours = ((source.charAt(pos + 1) - 48) * 10 + source.charAt(pos + 2)) - 48;
        int mins = ((source.charAt(pos + 4) - 48) * 10 + source.charAt(pos + 5)) - 48;
        int milliseconds = (hours * 60 + mins) * 60 * 1000;
        if (source.charAt(pos) == '+') {
          milliseconds = -milliseconds;
        }
        date.setTime(date.getTime() + milliseconds);
        pos += 6;
        tz= TimeZone.getTimeZone("GMT"+ tzStr);
    }

    if (pos < source.length() && source.charAt(pos) == 'Z') {
      pos += 1;
    }

    calendar.setTimeZone(tz);

    if (pos < source.length())  {
      throw new NumberFormatException(Messages.getMessage("badChars00"));
    }

    calendar.setTime(date);

    if (bc) {  calendar.set(0, 0); }
    if (this.javaType == Date.class) {
      return date;
    } else {
      return calendar;
    }
  }

}



