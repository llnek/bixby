;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.plugs.http

  "Implementation for HTTP/MVC service."

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.nettio.core :as nc]
            [czlab.nettio.server :as sv]
            [czlab.niou.util :as ct]
            [czlab.niou.core :as cc]
            [czlab.niou.webss :as ss]
            [czlab.niou.routes :as cr]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.twisty.ssl :as ssl]
            [czlab.twisty.codec :as co]
            [czlab.wabbit.plugs.core :as pc]
            [czlab.wabbit.plugs.mvc :as mvc]
            [czlab.basal.util :as u]
            [czlab.basal.xpis :as po]
            [czlab.basal.log :as l]
            [czlab.basal.io :as i]
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
  po/Idable
  (id [me] (:id me))
  xp/PlugletMsg
  (get-pluglet [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-type Http1xMsg
  po/Idable
  (id [me] (:id me))
  xp/PlugletMsg
  (get-pluglet [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn scan-basic-auth

  "Scan and parse if exists basic authentication."
  [evt] (c/if-some+
          [v (cc/msg-header evt auth-token)] (ct/parse-basic-auth v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- load-routes??

  [routes] (when-not (empty? routes) (cr/load-routes routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- resume-on-expiry

  [evt]

  (try (nc/reply-status (:socket evt) 500)
       (catch ClosedChannelException _)
         ;(l/warn "channel closed already."))
       (catch Throwable t# (l/exception t#))))

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

  (let [{:keys [cookies]
         {:keys [info]} :route} req
        {:as cfg
         :keys [want-session?]
         {:keys [macit?]} :session} (xp/gconf plug)]
    (assoc req
           :source plug
           :stale? false
           :id (str "HttpMsg#" (u/seqint2))
           :session (if (and (:session? info)
                             (c/!false? want-session?))
                       (ss/upstream (-> plug
                                        po/parent
                                        xp/pkey-bytes) cookies macit?)))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- boot!

  [plug]

  (let [asset! #'czlab.wabbit.plugs.mvc/asset-loader
        {:as cfg :keys [wait-millis]} (xp/gconf plug)]
    (l/debug "boot! http-plug: %s." cfg)
    (sv/web-server-module<>
      (assoc cfg
             :user-cb
             #(let [ev (evt<> plug (:socket %1) %1)
                    {:keys [route uri]} %1
                    {:keys [mount $handler]} route
                    hd (if (c/hgl? mount) asset! $handler)]
                (l/debug "route=%s." route)
                (if route
                  (pc/dispatch! ev
                                {:handler hd :dispfn (funky ev)})
                  (pc/error! ev
                             (Exception. (c/fmt "Bad route uri: %s" uri)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn p-error

  [{:keys [socket] :as evt} error]
  ;; 500 or 503
  (try (nc/reply-status socket 500)
       (finally
         (l/warn "processing orphan/error event: %s." error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn discarder!

  [func arg])
  ;(let [w (ds/discard-httpd<> func arg)] (po/start w arg) #(po/stop w)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- basicfg

  "Basic http config."
  [plug conf cfg0]

  (let [svr (po/parent plug)
        {:as cfg
         :keys [passwd
                routes server-key port]} (merge conf cfg0)]
    (if (c/hgl? server-key)
      (assert (cs/starts-with? server-key "file:")
              (c/fmt "Bad server-key: %s" server-key)))
    (assoc cfg
           :port (if (c/spos? port)
                   port
                   (if (c/hgl? server-key) 443 80))
           :routes (load-routes?? routes)
           :server-key server-key
           :passwd (->> svr
                        xp/pkey-chars
                        (co/pwd<> passwd) co/pw-text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet

  [server _id spec]
  (let [impl (atom {:info (:info spec)
                    :conf (:conf spec)
                    :timer (Timer. true)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :$handler]))
      (err-handler [_] (get-in @impl [:conf :$error]))
      (gconf [_] (:conf @impl))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (let [cfg (-> (basicfg me
                               (:conf @impl) arg)
                      b/expand-vars* b/prevar-cfg)
              {:keys [public-dir page-dir]} (:wsite cfg)
              pub (io/file (str public-dir) (str page-dir))]
          (l/info (str "http-plug: page-dir= %s.\n"
                       "http-plug: pub-dir= %s.") page-dir pub)
          (swap! impl
                 assoc
                 :conf
                 (assoc cfg :ftl-cfg (mvc/ftl-config<> pub))) me))
      po/Finzable
      (finz [me] (po/stop me) me)
      po/Startable
      (start [_] (po/start _ nil))
      (start [me arg]
        (let [w (boot! me)]
          (swap! impl assoc :boot w)
          (po/start w (:conf @impl)) me))
      (stop [me]
        (some-> (:boot @impl) po/stop) me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def HTTPSpec
  {:info {:name "Web Site"
          :version "1.0.0"}
   :conf {:$error :czlab.wabbit.plugs.http/p-error
          :max-mem-size (* 1024 1024 4)
          :$pluggable ::http<>
          :$handler nil
          :max-msg-size -1
          :wait-millis 0
          :sock-time-out 0
          :host ""
          :port 9090
          :server-key ""
          :passwd ""
          :use-etags? false
          :wsock-path ""
          ;;:want-session? true
          :session {;;4weeks
                    :max-age-secs 2419200
                    ;;1week
                    :max-idle-secs 604800
                    :is-hidden? true
                    :ssl-only? false
                    :macit? false
                    :web-auth? true
                    :domain ""
                    :domainPath "/"}
          :wsite {:public-dir "${wabbit.user.dir}/public"
                  :media-dir "res"
                  :page-dir "htm"
                  :js-dir "jsc"
                  :css-dir "css"}
          :routes [{:mount "res/{}" :uri "/(favicon\\..+)"}
                   {:mount "{}" :uri "/public/(.*)"}
                   {:uri "/?" :template  "main/index.html"}]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn http<>

  ([co id spec]
   (pluglet co id spec))
  ([_ id]
   (http<> _ id HTTPSpec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

