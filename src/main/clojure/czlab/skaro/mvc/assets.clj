;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "Kenneth Leung" }

  czlab.skaro.mvc.assets

  (:require
    [czlab.net.mime :refer [guessContentType]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io])

  (:use [czlab.skaro.io.http]
        [czlab.netty.core]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import
    [czlab.skaro.net WebContent WebAsset RangeInput]
    [io.netty.channel Channel]
    [io.netty.handler.stream
     ChunkedStream
     ChunkedInput
     ChunkedFile]
    [java.io Closeable File]
    [io.netty.handler.codec.http
     HttpResponseStatus
     HttpRequest
     HttpResponse
     HttpUtil
     HttpHeaders]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cache-assets-flag (atom false))
(def ^:private asset-cache (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn toggleCacheAssetsFlag

  "Toggle caching of assets"
  [cacheFlag?]

  (reset! cache-assets-flag (true? cacheFlag?))
  (log/debug "web assets caching set to %s" @cache-assets-flag))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn webContent<>

  ""
  ^WebContent
  [^File f]

  (if (and (some? f)
           (.canRead f))
    (let
      [ct (memoize guessContentType)
       s (memoize slurpBytes)]
      (reify WebContent
        (contentType [_] (ct f))
        (content [_] (s f))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro getLocalContent

  ""
  [appDir fname]

  `(webContent<> (io/file ~appDir ~fname)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cached?

  "Cache certain files"
  [^File fp]

  (and @cache-assets-flag
       (ewicAny? (fpath fp)
                 [".css" ".gif" ".jpg" ".jpeg" ".png" ".js"])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn webAsset<>

  ""
  ^WebAsset
  [^File f]

  (if (and (some? f)
           (.canRead f))
    (let [ct (memoize guessContentType)
          s (memoize slurpBytes)]
      (reify WebAsset
        (contentType [_] (ct f))
        (file [_] f)
        (content [_] (s f))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private fetchAsset "" [f] `(webAsset<> ~f))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAndSetAsset

  ""
  ^WebAsset
  [^File f]

  (if-some [wa (fetchAsset f)]
    (let [fp (fpath f)]
      (log/debug "asset-cache: file: %s" f)
      (swap! asset-cache assoc fp wa)
      wa)
    (do->nil
      (log/warn "asset-cache: fetch failed: %s" f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAsset

  ""
  ^WebAsset
  [^File f]

  (if @cache-assets-flag
    (let [wa (@asset-cache (fpath f))
          cf (if (some? wa)
               (.file ^WebAsset wa))]
      (if (or (nil? cf)
              (> (.lastModified f)
                 (.lastModified cf)))
        (fetchAndSetAsset f)
        wa))
    (fetchAsset f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getFileInput

  ""
  ^ChunkedInput
  [^File file gist ^HttpResponse rsp]

  (-> (gistHeader gist "range")
      (RangeInput/fileRange file rsp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyFileAsset

  ""
  [^Channel ch gist ^HttpResponse rsp ^File f]

  (let
    [asset (getAsset f)
     fname (.getName f)
     [ctype inp]
     (if (nil? asset)
       [(guessContentType f)
        (getFileInput f gist rsp)]
       [(.contentType asset)
        (ChunkedStream.
          (streamify (.content asset)))])
     clen (.length ^ChunkedInput inp)]
    (setHeader rsp "content-type" ctype)
    (log/debug (str "serving file: %s with "
                    "clen= %s, ctype= %s")
               fname
               clen
               (getHeader rsp "content-type"))
    (addHeader rsp "Accept-Ranges" "bytes")
    (->> (if (= HttpResponseStatus/NOT_MODIFIED
                (.getStatus rsp))
           0
           clen)
         (contentLength! rsp ))
    (let
      [wf1 (.writeAndFlush ch rsp)
       wf2
       (if-not (or (= (:method gist) "HEAD")
                   (noContent? rsp))
         (.writeAndFlush ch inp)
         (try!! nil (.close ^ChunkedInput inp)))]
      (closeCF (or wf2 wf1) (:keepAlive? gist)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


