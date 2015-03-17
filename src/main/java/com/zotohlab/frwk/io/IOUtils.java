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
// Copyright (c) 2013, Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.io;

import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import  org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;
import java.io.*;
import java.util.Collection;

import org.slf4j.*;


/**
 * @author kenl
 */
public class IOUtils {

  private static File _wd = new File( System.getProperty("java.io.tmpdir"));
  private static long _DFT= 2L * 1024 * 1024; // 2M
  private static long _streamLimit= _DFT;

  /** beyond this limit, data will be swapped out to disk (temp file)
   */
  public static long streamLimit() { return _streamLimit; }

  public static void setStreamLimit(long n) {
    _streamLimit= (n <= 0L) ? _DFT : n;
  }

  public static File workDir() { return  _wd; }

  public static void setWorkDir(File fpDir) {
    fpDir.mkdirs();
    _wd= fpDir;
  }

  /** Returns a Tuple(2) [ File, OutputStream? ]
   */
  public static Object[] newTempFile(boolean open) throws IOException { // (File,OutputStream)
    Object[] objs= new Object[2];
    File fp = mkTempFile("","");
    objs[0] = fp;
    try {
      objs[1] = open ? new FileOutputStream(fp) : null;
    } catch (FileNotFoundException e) {
      throw new IOException("Failed to create temp file.");
    }
    return objs;
  }

  public static File mkTempFile(String pfx, String sux) throws IOException {
    return File.createTempFile( StringUtils.isEmpty(pfx) ? "temp-" : pfx,
                                StringUtils.isEmpty(sux) ? ".dat" : sux, _wd);
  }

  /** Look for files with certain extension, such as "java", "xml"
   */
  public static Collection<File> listFiles(File dir, String ext, boolean recurse) {
    return FileUtils.listFiles(dir, new String[]{ ext}, recurse);
  }

  /** Look for any files with certain extension, such as "java", "xml"
   */
  public static Collection<File> listAnyFiles(File dir, String[] exts, boolean recurse) {
    return FileUtils.listFiles(dir, exts, recurse);
  }

  /** List directories.
   */
  public static File[] listDirs(File dir) {
    return dir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File f) {
        return f.isDirectory();
      }
    });
  }

  public static void main(String[] args) {
    try {
           for (File f : listDirs(new File("/private/tmp"))) {
                        System.out.println("f = " + f);
           }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}


