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
// Copyright (c) 2013 Ken Leung. All rights reserved.
 ??*/

package com.zotohlab.frwk.net;

import java.security.cert.*;
import java.security.*;
import javax.net.ssl.*;
import org.slf4j.*;


/**
 * @author kenl
 *
 */
public class SSLTrustMgrFactory extends TrustManagerFactorySpi {

  private static Logger _log=LoggerFactory.getLogger(SSLTrustMgrFactory.class);
  public static Logger tlog() { return SSLTrustMgrFactory._log; }

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

