;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.net.comms

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.str
         :only [lcase hgl? strim Embeds? HasNocase?]]
        [czlabclj.xlib.util.core :only [ThrowIOE Try!]]
        [czlabclj.xlib.util.mime :only [GetCharset]])

  (:import  [java.security.cert X509Certificate CertificateException]
            [javax.net.ssl SSLContext SSLEngine X509TrustManager
             TrustManagerFactorySpi TrustManager
             ManagerFactoryParameters]
            [org.apache.commons.codec.binary Base64]
            [java.security KeyStoreException KeyStore
             InvalidAlgorithmParameterException]
            [com.zotohlab.tpcl.apache ApacheHttpClient ]
            [com.zotohlab.frwk.net SSLTrustMgrFactory]
            [com.zotohlab.frwk.io XData]
            [org.apache.commons.lang3 StringUtils]
            [java.io File IOException]
            [java.net URL URI]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]
            [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

(def ^:private ^String AUTH "Authorization")
(def ^:private ^String BASIC "Basic")

(def ^:dynamic *socket-timeout* 5000)
(def ^String LOOPBACK "127.0.0.1")
(def ^String LHOST "localhost")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defrecord HTTPMsgInfo
  [^String protocol
   ^String method
   ^String uri
  is-chunked
  keep-alive
  clen
  headers
  params])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFormUploads "Only return file uploads."

  [^ULFormItems items]

  (filter #(not (.isFormField ^ULFileItem %))
          (.intern items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFormFields "Only return the form fields."

  [^ULFormItems items]

  (filter #(.isFormField ^ULFileItem %)
          (.intern items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseBasicAuth "Parse line looking for basic authentication info."

  [^String line]

  (when-let [s (StringUtils/split line)]
    (cond
      (and (== 2 (count s))
           (= "Basic" (first s))
           (hgl? (last s)))
      (let [tail (Base64/decodeBase64 ^String (last s))
            rc (StringUtils/split tail ":" 1) ]
        (if (== 2 (count rc))
          {:principal (first rc)
           :credential (last rc) }
          nil))
      :else
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- clean-str ""

  ^String
  [^String s]

  (StringUtils/stripStart (StringUtils/stripEnd s ";,") ";,"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseIE "Parse user agent line looking for IE."

  [^String line]

  (let [p1 #".*(MSIE\s*(\S+)\s*).*"
        m1 (re-matches p1 line)
        p2 #".*(Windows\s*Phone\s*(\S+)\s*).*"
        m2 (re-matches p2 line)
        bw "IE"
        dt (if (HasNocase? "iemobile") :mobile :pc) ]
    (let [bv (if (and (not (empty? m1))
                      (> (count m1) 2))
               (clean-str (nth m1 2))
               "")
          dev (if (and (not (empty? m2))
                       (> (count m2) 2))
                {:device-version (clean-str (nth m1 2))
                 :device-moniker "windows phone"
                 :device-type :phone }
                {} ) ]
      (merge {:browser :ie :browser-version bv :device-type dt} dev)
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseChrome "Parse user agent line looking for chrome."

  [^String line]

  (let [p1 #".*(Chrome/(\S+)).*"
        m1 (re-matches p1 line)
        bv (if (and (not (empty? m1))
                    (> (count m1) 2))
             (clean-str (nth m1 2))
             "") ]
    {:browser :chrome :browser-version bv :device-type :pc }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseKindle "Parse header line looking for Kindle."

  [^String line]

  (let [p1 #".*(Silk/(\S+)).*"
        m1 (re-matches p1 line)
        bv (if (and (not (empty? m1))
                    (> (count m1) 2))
             (clean-str (nth m1 2))
             "") ]
    {:browser :silk :browser-version bv :device-type :mobile :device-moniker "kindle"}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseAndroid "Parse header line looking for Android."

  [^String line]

  (let [p1 #".*(Android\s*(\S+)\s*).*"
        m1 (re-matches p1 line)
        bv (if (and (not (empty? m1))
                    (> (count m1) 2))
             (clean-str (nth m1 2))
             "") ]
   {:browser :chrome :browser-version bv :device-type :mobile :device-moniker "android" }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseFFox "Parse header line looking for Firefox."

  [^String line]

  (let [p1 #".*(Firefox/(\S+)\s*).*"
        m1 (re-matches p1 line)
        bv (if (and (not (empty? m1))
                    (> (count m1) 2))
             (clean-str (nth m1 2))
             "") ]
    {:browser :firefox :browser-version bv :device-type :pc}
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseSafari "Parse header line looking for Safari."

  [^String line]

  (let [p1 #".*(Version/(\S+)\s*).*"
        m1 (re-matches p1 line)
        bv (if (and (not (empty? m1))
                    (> (count m1) 2))
             (clean-str (nth m1 2))
             "")
        rc {:browser :safari :browser-version bv :device-type :pc} ]
    (cond
      (HasNocase? line "mobile/") (merge rc {:device-type :mobile })
      (HasNocase? line "iphone") (merge rc {:device-type :phone :device-moniker "iphone" } )
      (HasNocase? line "ipad") (merge rc {:device-type :mobile :device-moniker "ipad" } )
      (HasNocase? line "ipod") (merge rc {:device-type :mobile :device-moniker "ipod" } )
      :else rc )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseUserAgentLine "Retuns a map of browser/device attributes."

  [^String agentLine]

  (let [line (strim agentLine) ]
    (cond
      (and (Embeds? line "Windows") (Embeds? line "Trident/"))
      (ParseIE line)

      (and (Embeds? line "AppleWebKit/")(Embeds? line "Safari/")
           (Embeds? line "Chrome/"))
      (ParseChrome line)

      (and (Embeds? line "AppleWebKit/") (Embeds? line "Safari/")
           (Embeds? line "Android"))
      (ParseAndroid line)

      (and (Embeds? line "AppleWebKit/")(Embeds? line "Safari/")
           (Embeds? line "Silk/"))
      (ParseKindle line)

      (and (Embeds? line "Safari/")(Embeds? line "Mac OS X"))
      (ParseSafari)

      (and (Embeds? line "Gecko/")(Embeds? line "Firefox/"))
      (ParseFFox)

      :else
      {} )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(ns-unmap *ns* '->HTTPMsgInfo)
(def ^:private comms-eof nil)

