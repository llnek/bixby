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


package com.zotohlab.frwk.util;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;
import java.lang.reflect.InvocationTargetException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.zotohlab.frwk.core.CallableWithArgs;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


/**
 * @author kenl
 */
@SuppressWarnings("unused")
public enum CU {
;

  public static final Logger TLOG=getLogger(lookup().lookupClass());

  private static final AtomicInteger _si= new AtomicInteger(0);
  private static final AtomicLong _sn= new AtomicLong(0L);

  public static void main(String[] args) {
    try {
/*
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
      "`~!@#$%^&*()-_+={}[]|\:;',.<>?/'"
*/
//      String script = "(fn [_] {:a 1} )";
//      IFn fn = (IFn)RT.var("clojure.core", "eval").invoke(RT.var("clojure.core","read-string").invoke(script));
//      Object obj = fn.invoke("Hello");
//      Map<?,?> m= (Map<?,?>)obj;
//      Keyword k= Keyword.intern("a");
//      System.out.println("obj= " + m.get(k));
      //System.out.println(shuffle("0123456789AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz"));
//      URLCodec cc = new URLCodec("utf-8");
//      System.out.println(cc.encode("hello\u0000world"));
//      String[] rc= StringUtils.split(",;,;,;", ",;");
      String rc= new Locale("en").toString();
      rc=null;

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
    catch (Throwable e) {
      TLOG.error("", e);
    }
  }

  public static void unblock(Object lock) {
    try {
      synchronized (lock) {
        lock.notifyAll();
      }
    }
    catch (Throwable e) {
      TLOG.error("", e);
    }
  }

  public static Object asJObj(Object a) {
    return a;
  }

  public static String nsb(Object x) {
    return x==null ? "" : x.toString();
  }

  /**
   * Shuffle characters in this string.
   *
   * @param s
   * @return
   */
  public static String shuffle(String s) {
    List<Character> lst = new ArrayList<>();
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
    try {
      Thread.currentThread().join();
    } catch (Throwable e) {
      TLOG.error("", e);
    }
    /*
    while (true) try {
      Thread.sleep(8000);
    }
    catch (Throwable e)
    {}
    */
  }

  public static JsonElement readJson(File f) {
    try {
      return readJson(FileUtils.readFileToString(f, "utf-8"));
    } catch (IOException e) {
      TLOG.error("",e);
      return null;
    }
  }

  public static JsonElement readJson(String s) {
    return new JsonParser().parse(s);
  }

  public static String[] splitNull(String s) {
    return StringUtils.split( nsb(s), "\u0000");
  }

  public static Class<?> loadClass(String cz) throws ClassNotFoundException {
    return Thread.currentThread().getContextClassLoader().loadClass(cz);
  }

  public static Object dftCtor(String cz) throws InstantiationException, IllegalAccessException,
  IllegalArgumentException, InvocationTargetException,
  NoSuchMethodException, SecurityException, ClassNotFoundException  {
    return loadClass(cz).getDeclaredConstructor().newInstance();
  }

  public static Object syncExec(Object syncObj, CallableWithArgs  r, Object a1, Object... args) throws Exception {
    synchronized(syncObj) {
      return r.run(a1, args);
    }
  }

  public static long nextSeqLong() { return _sn.incrementAndGet(); }
  public static int nextSeqInt() { return _si.incrementAndGet(); }

}


