;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.net.apache

  (:require
    [czlabclj.xlib.util.str
          :refer [lcase hgl? strim Embeds? HasNocase?]]
    [czlabclj.xlib.util.core :refer [ThrowIOE try!]]
    [czlabclj.xlib.util.mime :refer [GetCharset]])

  (:require [czlabclj.xlib.util.logging :as log])

  (:use [czlabclj.xlib.net.comms])

  (:import
    [org.apache.http Header StatusLine HttpEntity HttpResponse]
    [java.security.cert X509Certificate CertificateException]
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
    [org.apache.http.client.config RequestConfig]
    [org.apache.http.client HttpClient]
    [org.apache.http.client.methods HttpGet HttpPost]
    [org.apache.http.impl.client HttpClientBuilder]
    [java.io File IOException]
    [org.apache.http.util EntityUtils]
    [java.net URL URI]
    [org.apache.http.params HttpConnectionParams]
    [org.apache.http.entity InputStreamEntity]
    [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkApacheClientHandle

  "Internal functions to support apache http client"

  ^HttpClient
  []

  (let [cli (HttpClientBuilder/create)
        cfg (-> (RequestConfig/custom)
                (.setConnectTimeout (int *socket-timeout*))
                (.setSocketTimeout (int *socket-timeout*))
                (.build)) ]
    (.setDefaultRequestConfig cli ^RequestConfig cfg)
    (ApacheHttpClient/cfgForRedirect cli)
    (.build cli)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- get-bits ""

  ^bytes
  [^HttpEntity ent]

  (when (some? ent)
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
        ct (when (some? ent) (.getContentType ent))
        cv (if (nil? ct) "" (strim (.getValue ct)))
        cl (lcase cv) ]
    (try!
      (log/debug "response: content-encoding: %s\n%s%s"
                 (.getContentEncoding ent)
                 "Content-type: " cv))
    (let [bits (get-bits ent)
          clen (if (nil? bits) 0 (alength bits)) ]
      {:encoding (GetCharset cv)
       :content-type cv
       :data (if (== clen 0) nil (XData. bits)) } )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- processError ""

  [^HttpResponse rsp ^Throwable exp]

  (try! (EntityUtils/consumeQuietly (.getEntity rsp)))
  (throw exp))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- processRedirect ""

  [^HttpResponse rsp]

  ;;TODO - handle redirect
  (processError rsp (ThrowIOE "Redirect not supported")) )

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

  [^URL targetUrl
   ^String contentType
   ^XData rdata beforeSendFunc]

  (let [^HttpClient cli (mkApacheClientHandle) ]
    (try
      (let [ent (InputStreamEntity. (.stream rdata)
                                    (.size rdata))
            p (HttpPost. (.toURI targetUrl)) ]
        (.setEntity p (doto ent
                            (.setContentType contentType)
                            (.setChunked true)))
        (beforeSendFunc p)
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
        (beforeSendFunc g)
        (processReply (.execute cli g)))
      (finally
        (.. cli getConnectionManager shutdown)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncPost

  "Perform a http-post on the target url"

  [^URL targetUrl ^String contentType
   ^XData rdata & [b4SendFn]]

  (doPOST targetUrl
          contentType
          rdata
          (or b4SendFn (constantly nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncGet

  "Perform a http-get on the target url"

  [^URL targetUrl & [b4SendFn]]

  (doGET targetUrl (or b4SendFn (constantly nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSimpleClientSSL

  "Simple minded, trusts everyone"

  ^SSLContext
  []

  (doto (SSLContext/getInstance "TLS")
        (.init nil (SSLTrustMgrFactory/getTrustManagers) nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

