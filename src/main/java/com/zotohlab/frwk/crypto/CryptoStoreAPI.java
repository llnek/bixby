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

package com.zotohlab.frwk.crypto;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 *
 * @author kenl
 *
 */
public interface CryptoStoreAPI {

  public void addKeyEntity(byte[] keyBits, PasswordAPI pwdObj);
  public void addCertEntity(byte[] certBits);

  public TrustManagerFactory trustManagerFactory();
  public KeyManagerFactory keyManagerFactory();

  public List<String> certAliases();
  public List<String> keyAliases();

  public KeyStore.PrivateKeyEntry keyEntity(String nm, PasswordAPI pwdObj);
  public KeyStore.TrustedCertificateEntry certEntity(String nm);
  public void removeEntity(String nm);

  public List<X509Certificate> intermediateCAs();
  public List<X509Certificate> rootCAs();
  public List<X509Certificate> trustedCerts();

  public void addPKCS7Entity(byte[] pkcs7Bits);

}

