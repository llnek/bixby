// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package com.zotohlab.frwk.util;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zotohlab.frwk.core.CallableWithArgs;

/**
 * @author kenl
 */
@SuppressWarnings("unused")
public enum CU {
;

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

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
      
      FileUtils.copyDirectoryToDirectory(new File("/tmp/poo"), new File("/tmp/shit"));
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
      tlog().error("", e);
    }
  }

  public static void unblock(Object lock) {
    try {
      synchronized (lock) {
        lock.notifyAll();
      }
    }
    catch (Throwable e) {
      tlog().error("", e);
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
      tlog().error("", e);
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

  public static Class<?> loadClass(String cz) throws ClassNotFoundException {
    return Thread.currentThread().getContextClassLoader().loadClass(cz);  
  }
  
  public static Object dftCtor(String cz) throws InstantiationException, IllegalAccessException, 
  IllegalArgumentException, InvocationTargetException, 
  NoSuchMethodException, SecurityException, ClassNotFoundException  {
    return loadClass(cz).getDeclaredConstructor().newInstance();  
  }
    
  public static Object syncExec (Object syncObj, CallableWithArgs r) throws Exception {
    synchronized(syncObj) {
      return r.run(new Object[]{});
    }
  }

  public static long nextSeqLong() { return _sn.incrementAndGet(); }
  public static int nextSeqInt() { return _si.incrementAndGet(); }
  
}

