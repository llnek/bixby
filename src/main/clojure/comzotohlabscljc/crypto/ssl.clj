;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.crypto.ssl

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (javax.net.ssl X509TrustManager TrustManager))
  (:import (javax.net.ssl SSLEngine SSLContext))
  (:import (java.net URL))
  (:import (javax.net.ssl KeyManagerFactory TrustManagerFactory))

  (:use [comzotohlabscljc.crypto.stores :only [MakeCryptoStore] ])
  (:use [comzotohlabscljc.crypto.core :only [PkcsFile? GetJksStore
                                             GetPkcsStore GetSRand MakeSimpleTrustMgr] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSslContext "Make a server-side SSLContext."

  (^SSLContext [^URL keyUrl ^comzotohlabscljc.crypto.codec.Password pwdObj]
   (MakeSslContext keyUrl pwdObj "TLS"))

  (^SSLContext [^URL keyUrl ^comzotohlabscljc.crypto.codec.Password pwdObj ^String flavor]
    (let [ ctx (SSLContext/getInstance flavor)
           ks (with-open [ inp (.openStream keyUrl) ]
                (if (PkcsFile? keyUrl)
                    (GetPkcsStore inp pwdObj)
                    (GetJksStore inp pwdObj)))
           cs (MakeCryptoStore ks pwdObj)
           ^TrustManagerFactory tmf   (.trustManagerFactory cs)
           ^KeyManagerFactory kmf   (.keyManagerFactory cs) ]
      (.init ctx (.getKeyManagers kmf) (.getTrustManagers tmf) (GetSRand))
      ctx)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSslClientCtx "Make a client-side SSLContext."

  ^SSLContext
  [ssl]

  (if (not ssl)
      nil
      (let [ ctx (SSLContext/getInstance "TLS") ]
        (.init ctx nil (into-array TrustManager [(MakeSimpleTrustMgr)]) nil)
        ctx)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ssl-eof nil)

