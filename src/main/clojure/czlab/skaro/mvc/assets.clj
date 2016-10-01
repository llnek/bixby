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
    [czlab.xlib.io :refer [slurpBytes writeFile]]
    [czlab.xlib.core :refer [do->nil try!! fpath]]
    [czlab.net.mime :refer [guessContentType]]
    [czlab.xlib.str :refer [lcase ewicAny?]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.netty.core
     :refer :all
     :rename {slurpBytes xxx}]
    [czlab.xlib.io :refer [streamify]])

  (:use [czlab.skaro.io.http])

  (:import
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
     HttpHeaders]
    [io.netty.channel Channel]
    [czlab.skaro.net
     WebContent
     WebAsset
     RangeInput]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cache-assets-flag (atom false))
(def ^:private asset-cache (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn setCacheAssetsFlag

  "Toggle caching of assets"
  [cacheFlag?]

  (reset! cache-assets-flag (true? cacheFlag?))
  (log/info "web assets caching set to %s" @cache-assets-flag))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn webContent<>

  ""
  ^WebContent
  [^File f]

  (if (and (some? f)
           (.canRead f))
    (reify WebContent
      (contentType [_]
        (guessContentType f "utf-8"))
      (body [_]
        (slurpBytes f)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro getLocalFile

  ""
  [appDir fname]

  `(webContent<> (io/file ~appDir ~fname)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cached?

  "cache certain files"
  [^File fp]

  (if @cache-assets-flag
    (-> (fpath fp)
        (ewicAny? [".css"
                   ".gif"
                   ".jpg"
                   ".jpeg"
                   ".png"
                   ".js"]))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn webAsset<>

  ""
  ^WebAsset
  [^File file]

  (if (and (some? file)
           (.canRead file))
    (let [ct (guessContentType file
                               "utf-8" "text/plain")
          ts (.lastModified file)]
      (reify WebAsset
        (contentType [_] ct)
        (file [_] file)
        (body [_] (slurpBytes file))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAsset

  ""
  ^WebAsset
  [^File file]

  (when (and (.exists file)
             (.canRead file))
    (webAsset<> file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAndSetAsset

  ""
  ^WebAsset
  [^File f]

  (if-some [wa (fetchAsset f)]
    (let [fp (fpath f)]
      (log/debug "asset-cache: new file: %s" fp)
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
       [(guessContentType f "utf-8" "text/plain")
        (getFileInput f gist rsp)]
       [(.contentType asset)
        (ChunkedStream.
          (streamify (.body asset)))])
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


