/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlab.frwk.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.codec.net.URLCodec;
import org.slf4j.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;

/**
 * @author kenl
 */
public enum CoreUtils {
;

  private static Logger _log= LoggerFactory.getLogger(CoreUtils.class);
  public static Logger tlog() { return _log; }

  public static void main(String[] args) {
    try {
      //System.out.println(shuffle("0123456789AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz"));
      URLCodec cc = new URLCodec("utf-8");
      System.out.println(cc.encode("hello\u0000world"));
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public static void blockAndWait(Object lock, long waitMillis) {
    try {
      synchronized (lock) {
        if (waitMillis > 0L) {
          lock.wait(waitMillis);
        } else {
          lock.wait();
        }
      }
    }
    catch (Throwable e)
    {}
  }

  public static void unblock(Object lock) {
    try {
      synchronized (lock) {
        lock.notifyAll();
      }
    }
    catch (Throwable e)
    {}
  }

  public static Object asJObj(Object a) {
    return a;
  }

  public static String nsb(Object x) {
    return x==null ? "" : x.toString();
  }

  public static String shuffle(String s) {
    List<Character> lst = new ArrayList<Character>();
    char[] cs= s.toCharArray();
    for (int n= 0; n < cs.length; ++n) {
      lst.add(cs[n]);
    }
    Collections.shuffle(lst);
    for (int n= 0; n < lst.size(); ++n) {
      cs[n] = lst.get(n).charValue();
    }
    return new String(cs);
  }

  public static void blockForever() {
    while (true) try {
      Thread.sleep(8000);
    }
    catch (Throwable e)
    {}
  }

  public static JsonElement readJson(File f) {
    try {
      return readJson( FileUtils.readFileToString(f, "utf-8"));
    } catch (IOException e) {
      tlog().error("",e);
      return null;
    }
  }

  public static JsonElement readJson(String s) {
    return new JsonParser().parse(s);
  }

  public static String[] splitNull(String s) {
    return StringUtils.split( nsb(s), "\u0000");
  }

  public static void syncExec (Object syncObj, Runnable r) {
    synchronized(syncObj) {
      r.run();
    }
  }

}

