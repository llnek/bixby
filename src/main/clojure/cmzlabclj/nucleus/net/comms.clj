;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.nucleus.net.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.str :only [strim Embeds? HasNocase?] ]
        [cmzlabclj.nucleus.util.core :only [ThrowIOE Try!] ]
        [cmzlabclj.nucleus.util.mime :only [GetCharset] ])

  (:import  [java.security.cert X509Certificate CertificateException]
            [java.security KeyStoreException KeyStore
                          InvalidAlgorithmParameterException]
            [javax.net.ssl SSLContext SSLEngine X509TrustManager
                          TrustManagerFactorySpi TrustManager
                          ManagerFactoryParameters]
            [com.zotohlab.frwk.apache ApacheFW ]
            [com.zotohlab.frwk.net SSLTrustMgrFactory]
            [com.zotohlab.frwk.io XData]
            [org.apache.commons.lang3 StringUtils]
            [org.apache.http.client.config RequestConfig]
            [org.apache.http.client HttpClient]
            [org.apache.http.client.methods HttpGet HttpPost]
            [org.apache.http.impl.client HttpClientBuilder]
            [org.apache.http Header StatusLine HttpEntity HttpResponse]
            [java.io File IOException]
            [org.apache.http.util EntityUtils]
            [java.net URL URI]
            [org.apache.http.params HttpConnectionParams]
            [org.apache.http.entity InputStreamEntity]
            [com.zotohlab.frwk.net ULFormItems ULFileItem]
            [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *socket-timeout* 5000)
(def ^String LOOPBACK "127.0.0.1")
(def ^String LHOST "localhost")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defrecord HTTPMsgInfo [^String protocol ^String method ^String uri
                        is-chunked
                        keep-alive
                        clen
                        headers
                        params] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFormUploads "Only return file uploads."

  [^ULFormItems items]

  (filter #(not (.isFormField ^ULFileItem %))
          (.getAll items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetFormFields "Only return the form fields."

  [^ULFormItems items]

  (filter #(.isFormField ^ULFileItem %)
          (.getAll items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; internal functions to support apache http client.
(defn- mkApacheClientHandle ""

  ^HttpClient
  []

  (let [cli (HttpClientBuilder/create)
        cfg (-> (RequestConfig/custom)
                (.setConnectTimeout (int *socket-timeout*))
                (.setSocketTimeout (int *socket-timeout*))
                (.build)) ]
    (.setDefaultRequestConfig cli ^RequestConfig cfg)
    (ApacheFW/cfgForRedirect cli)
    (.build cli)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- get-bits ""

  ^bytes
  [^HttpEntity ent]

  (if (nil? ent)
    nil
    (EntityUtils/toByteArray ent)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- get-str ""

  ^String
  [^HttpEntity ent]

  (EntityUtils/toString ent "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- processOK ""

  [^HttpResponse rsp]

  (let [ent (.getEntity rsp)
        ct (if (nil? ent) nil (.getContentType ent))
        cv (if (nil? ct) "" (strim (.getValue ct)))
        cl (cstr/lower-case cv) ]
    (Try!
      (log/debug "Http-response: content-encoding: "
                 (.getContentEncoding ent)
                 "\n"
                 "Content-type: " cv))
    (let [bits (get-bits ent)
          clen (if (nil? bits) 0 (alength bits)) ]
      {:encoding (GetCharset cv)
       :content-type cv
       :data (if (== clen 0) nil (XData. bits)) } )
  ))
    ;;(cond
      ;;(or (.startsWith cl "text/")
          ;;(.startsWith cl "application/xml")
          ;;(.startsWith cl "application/json")) (get-bits ent) ;;(get-str ent)
      ;;:else (get-bits ent))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- processError ""

  [^HttpResponse rsp ^Throwable exp]

  (Try! (EntityUtils/consumeQuietly (.getEntity rsp)))
  (throw exp))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- processRedirect ""

  [^HttpResponse rsp]

  ;;TODO - handle redirect
  (processError rsp (ThrowIOE "Redirect not supported.")) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- processReply ""

  [^HttpResponse rsp]

  (let [st (.getStatusLine rsp)
        msg (if (nil? st) "" (.getReasonPhrase st))
        rc (if (nil? st) 0 (.getStatusCode st)) ]
    (cond
      (and (>= rc 200) (< rc 300))
      (processOK rsp)

      (and (>= rc 300) (< rc 400))
      (processRedirect rsp)

      :else
      (processError rsp (ThrowIOE (str "Service Error: " rc ": " msg))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doPOST ""

  [^URL targetUrl ^String contentType ^XData rdata beforeSendFunc]

  (let [^HttpClient cli (mkApacheClientHandle) ]
    (try
      (let [ent (InputStreamEntity. (.stream rdata) (.size rdata))
            p (HttpPost. (.toURI targetUrl)) ]
        (.setEntity p (doto ent
                            (.setContentType contentType)
                            (.setChunked true)))
        (when (fn? beforeSendFunc) (beforeSendFunc p))
        (processReply (.execute cli p)))
      (finally
        (.. cli getConnectionManager shutdown)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doGET ""

  [^URL targetUrl beforeSendFunc]

  (let [^HttpClient cli (mkApacheClientHandle) ]
    (try
      (let [g (HttpGet. (.toURI targetUrl)) ]
        (when (fn? beforeSendFunc) (beforeSendFunc g))
        (processReply (.execute cli g)))
      (finally
        (.. cli getConnectionManager shutdown)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncPost "Perform a http-post on the target url."

  ([^URL targetUrl contentType
    ^XData rdata]
   (SyncPost targetUrl contentType rdata nil))

  ([^URL targetUrl contentType
    ^XData rdata b4SendFn]
   (doPOST targetUrl contentType rdata b4SendFn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncGet "Perform a http-get on the target url."

  ([^URL targetUrl]
   (SyncGet targetUrl nil))

  ([^URL targetUrl b4SendFn]
   (doGET targetUrl b4SendFn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSimpleClientSSL "Simple minded, trusts everyone."

  ^SSLContext
  []

  (doto (SSLContext/getInstance "TLS")
    (.init nil (SSLTrustMgrFactory/getTrustManagers) nil)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- clean-str ""

  ^String
  [^String s]

  (StringUtils/stripStart (StringUtils/stripEnd s ";,") ";,"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ParseIE ""

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
(defn ParseChrome ""

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
(defn ParseKindle ""

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
(defn ParseAndroid ""

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
(defn ParseFFox ""

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
(defn ParseSafari ""

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
(def ^:private comms-eof nil)

