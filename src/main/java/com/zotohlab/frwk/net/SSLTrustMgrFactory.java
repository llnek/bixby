// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015, Ken Leung. All rights reserved.

package com.zotohlab.frwk.net;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;



/**
 * A simple trust manager.
 *
 * @author kenl
 *
 */
public class SSLTrustMgrFactory extends TrustManagerFactorySpi {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  public static TrustManager[] getTrustManagers() {
    return new TrustManager[] { new X509TrustManager() {
      public void checkClientTrusted( X509Certificate[] chain, String authType) {
        tlog().warn("SkipCheck: CLIENT CERTIFICATE: {}" , chain[0].getSubjectDN() );
      }
      public void checkServerTrusted( X509Certificate[] chain, String authType) {
        tlog().warn("SkipCheck: SERVER CERTIFICATE: {}" , chain[0].getSubjectDN() );
      }
      public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }};
  }

  public TrustManager[] engineGetTrustManagers() { return SSLTrustMgrFactory.getTrustManagers(); }
  public void engineInit(KeyStore ks) {}
  public void engineInit(ManagerFactoryParameters p) {}
}

