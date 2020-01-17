;; Copyright © 2013-2020, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.plugs.http

  "Implementation for HTTP/MVC service."

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.niou.util :as ct]
            [czlab.niou.core :as cc]
            [czlab.niou.webss :as ss]
            [czlab.niou.routes :as cr]
            [czlab.twisty.ssl :as ssl]
            [czlab.twisty.codec :as co]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.nettio.core :as nc]
            [czlab.nettio.server :as sv]
            [czlab.blutbad.core :as b]
            [czlab.blutbad.web.ftl :as t]
            [czlab.basal.core :as c :refer [is?]])

  (:import [czlab.niou.core WsockMsg Http1xMsg]
           [java.nio.channels ClosedChannelException]
           [io.netty.handler.codec DecoderException]
           [io.netty.handler.codec.http.websocketx
            TextWebSocketFrame
            WebSocketFrame
            BinaryWebSocketFrame]
           [io.netty.handler.codec.http.cookie
            ServerCookieDecoder
            ServerCookieEncoder]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.buffer ByteBuf Unpooled]
           [io.netty.handler.ssl SslHandler]
           [czlab.nettio InboundHandler]
           [java.util Timer TimerTask]
           [io.netty.handler.codec.http
            HttpResponseStatus
            HttpRequest
            HttpUtil
            HttpResponse
            DefaultHttpResponse
            FullHttpRequest
            HttpVersion
            HttpRequestDecoder
            HttpResponseEncoder
            DefaultCookie
            HttpHeaderValues
            HttpHeaderNames
            LastHttpContent
            HttpHeaders
            Cookie
            QueryStringDecoder]
           [java.io
            Closeable
            File
            IOException
            RandomAccessFile]
           [java.net
            HttpCookie
            URI
            URL
            InetAddress
            SocketAddress
            InetSocketAddress]
           [io.netty.channel
            Channel
            ChannelHandler
            ChannelFuture
            ChannelFutureListener
            ChannelPipeline
            ChannelHandlerContext
            SimpleChannelInboundHandler]
           [io.netty.handler.stream
            ChunkedStream
            ChunkedFile
            ChunkedInput
            ChunkedWriteHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(c/def- ^String auth-token "authorization")
(c/def- ^String basic-token "basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-type WsockMsg
  c/Idable
  (id [me] (:id me))
  c/Hierarchical
  (parent [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-type Http1xMsg
  c/Idable
  (id [me] (:id me))
  c/Hierarchical
  (parent [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn scan-basic-auth

  "Scan and parse if exists basic authentication."
  {:arglists '([evt])}
  [evt]

  (c/if-some+
    [v (cc/msg-header evt auth-token)] (ct/parse-basic-auth v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- load-routes??

  [routes] (when-not (empty? routes) (cr/load-routes routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- strip-url-crap??

  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg."
  ^String
  [^String path]

  (let [pos (cs/last-index-of path \/)]
    (if-not (c/spos? pos)
      path
      (let [p1 (cs/index-of path \? pos)
            p2 (cs/index-of path \& pos)
            p3 (cond (and (c/spos? p1)
                          (c/spos? p2))
                     (min p1 p2)
                     (c/spos? p1) p1
                     (c/spos? p2) p2 :else -1)]
        (if (c/spos? p3) (subs path 0 p3) path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- resume-on-expiry

  [evt]

  (try (nc/reply-status (:socket evt) 500)
       (catch ClosedChannelException _)
       (catch Throwable t# (c/exception t#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- wsock-event<>

  [plug ch msg]

  (assoc msg
         :socket ch
         :source plug
         :ssl? (some? (nc/get-ssl?? ch))
         :id (str "WsockMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- http-event<>

  [plug ch req]

  (let [{:keys [cookies route]} req
        {:as cfg
         :keys [want-session?]
         {:keys [crypt?]} :session} (:conf plug)]
    (assoc req
           :source plug
           :stale? false
           :id (str "HttpMsg#" (u/seqint2))
           :session (if (and (c/!false? want-session?)
                             (:session? route))
                       (ss/upstream (-> plug c/parent b/pkey) cookies crypt?)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [plug ch msg]

  (cond (is? WsockMsg msg)
        (wsock-event<> plug ch msg)
        (c/sas? cc/HttpMsgGist msg)
        (http-event<> plug ch msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- funky

  [evt] (if (c/sas? cc/HttpMsgGist evt)
          (let [res (cc/http-result evt)] (fn [h e] (h e res)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn p-error

  [{:keys [socket] :as evt} error]
  ;; 500 or 503
  (try (nc/reply-status socket 500)
       (finally
         (c/warn "processing orphan/error event: %s." error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn asset-loader

  "Load a file from the public folder."
  {:arglists '([evt])}
  [{:keys [uri2 route] :as evt}]

  (letfn
    [(reply-static [res file]
       (cc/reply-result
         (try (if (i/file-read? file)
                (cc/res-body-set res file)
                (cc/res-status-set res 404))
              (catch Throwable _
                (c/error _ "get: %s." uri2)
                (-> res
                    (cc/res-body-set nil)
                    (cc/res-status-set 500))))))]
    (let [path (c/stror (:rewrite route)
                        (c/_1 (cc/decoded-path uri2)))
          res (cc/http-result evt)
          plug (c/parent evt)
          {:keys [public-dir
                  uri-prefix
                  file-access-check?]}
          (get-in plug [:conf :wsite])
          pub-dir (io/file public-dir)
          home-dir (-> plug
                       c/parent
                       b/home-dir u/fpath)]
      (or (if (cs/starts-with? path uri-prefix)
            (let [fp (cs/replace-first path
                                       uri-prefix "")
                  ffile (io/file pub-dir fp)]
              (c/debug "request for asset: %s." ffile)
              (if (or (false? file-access-check?)
                      (cs/starts-with? (u/fpath ffile)
                                       (u/fpath pub-dir)))
                (do (reply-static res ffile) true)
                (do (c/warn "illegal uri access: %s." fp) false))))
          (-> (cc/res-status-set res 403) cc/reply-result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- boot!

  [{:keys [conf] :as plug}]

  (let [{:keys [uri-prefix]} (:wsite conf)]
    (sv/web-server-module<>
      (assoc conf
             :user-cb
             #(let [ev (evt<> plug (:socket %1) %1)
                    {:keys [uri2]
                     {:keys [rewrite
                             info params]} :route} %1
                    path (first (cc/decoded-path uri2))]
                (b/dispatch ev
                            (cond (cs/starts-with?
                                    (c/stror rewrite path) uri-prefix)
                                  #'czlab.blutbad.plugs.http/asset-loader
                                  (:redirect info)
                                  #'czlab.nettio.core/redirector
                                  :else
                                  (:handler info))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn discarder!

  [func arg])
  ;(let [w (ds/discard-httpd<> func arg)] (po/start w arg) #(po/stop w)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- basicfg

  "Basic http config."
  [cfg server]

  (let [{:keys [passwd
                routes server-key port]} cfg]
    (if (c/hgl? server-key)
      (assert (cs/starts-with? server-key "file:")
              (c/fmt "Bad server-key: %s" server-key)))
    (assoc cfg
           :port (if (c/spos? port)
                   port
                   (if (c/hgl? server-key) 443 80))
           ;:routes (load-routes?? routes)
           :server-key server-key
           :passwd (->> server
                        b/pkey
                        i/x->chars
                        (co/pwd<> passwd) co/pw-text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord HTTPPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (let [{:as cfg
           {:keys[template-engine
                  public-dir page-dir]} :wsite}
          (-> (c/merge+ conf arg)
              (basicfg server)
              b/expand-vars* b/prevar-cfg)
          pub (io/file (str public-dir)
                       (str page-dir))
          eng (if
                (c/or?? [= template-engine]
                        :default :freemarker)
                (t/ftl-config<> pub))]
      (c/info "page-dir= %s." page-dir)
      (c/info "pub-dir= %s." pub)
      (assoc me
             :conf (assoc cfg :template-engine eng))))
  c/Finzable
  (finz [me] (c/stop me))
  c/Startable
  (stop [me]
    (some-> (:boot me) c/stop) me)
  (start [_]
    (c/start _ nil))
  (start [me arg]
    (assoc me :boot (-> (boot! me) (c/start conf)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def
  ^{:doc ""}

  HTTPSpec

  {:info {:name "Web Site"
          :version "1.0.0"}
   :conf {:max-mem-size (* 4 c/MegaBytes)
          :$pluggable ::http<>
          :$error ::p-error
          :$action nil
          :max-msg-size -1
          :wait-millis 0
          :sock-time-out 0
          :host ""
          :port 9090
          :server-key ""
          :passwd ""
          :use-etags? false
          ;:wsock-path ""
          ;:want-session? true
          :session {;;4weeks
                    :max-age-secs 2419200
                    ;;1week
                    :max-idle-secs 604800
                    :hidden? true
                    :secure? false
                    :crypt? false
                    :domain ""
                    :domainPath "/"}
          :wsite {:public-dir "${blutbad.user.dir}/public"
                  :file-access-check? true
                  :template-engine :default
                  :uri-prefix "/public/"
                  :media-dir "res"
                  :page-dir "htm"
                  :js-dir "src"
                  :css-dir "css"}
          :routes [{:name :favicon
                    :pattern "/{f}"
                    :remap "/public/res/{f}"
                    :groups {:f "favicon\\.[a-zA-Z]{3}"}}
                   {:name :index
                    :pattern "/{x}"
                    :remap "/public/htm/{x}"
                    :groups {:x "index\\.html?"}}
                   {:name :home
                    :pattern "/?"
                    :redirect {:status 307 :location "/index.html"}}]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn http<>

  "Create a HTTP Plugin."
  {:arglists '([server id]
               [server id options])}

  ([_ id]
   (http<> _ id HTTPSpec))

  ([co id {:keys [info conf]}]
   (HTTPPlugin. co id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

