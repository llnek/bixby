;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for HTTP/MVC service."
      :author "Kenneth Leung"}

  czlab.wabbit.plugs.http

  (:require [czlab.basal.proto :as po]
            [czlab.niou.util :as ct]
            [czlab.basal.util :as u]
            [czlab.niou.routes :as cr]
            [czlab.nettio.server :as sv]
            [czlab.twisty.codec :as co]
            [czlab.nettio.core :as nc]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.niou.core :as cc]
            [czlab.niou.webss :as ss]
            [czlab.twisty.ssl :as ssl]
            [czlab.basal.log :as l]
            [clojure.java.io :as io]
            [czlab.basal.io :as i]
            [clojure.string :as cs]
            [czlab.basal.cljrt :as rt]
            [czlab.basal.str :as s]
            [czlab.wabbit.plugs.mvc :as mvc]
            [czlab.wabbit.plugs.core :as pc]
            [czlab.nettio.apps.discard :as ds]
            [czlab.basal.core :as c :refer [is?]])

  (:import [czlab.niou.core WsockMsg Http1xMsg]
           [java.nio.channels ClosedChannelException]
           [io.netty.handler.codec DecoderException]
           [clojure.lang IDeref Atom APersistentMap]
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
(def ^:private ^String auth-token "authorization")
(def ^:private ^String basic-token "basic")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-protocol xp/PlugletMsg
  WsockMsg
  (get-pluglet [me] (:source me))
  Http1xMsg
  (get-pluglet [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn scan-basic-auth
  "Scan and parse if exists basic authentication."
  [evt] (c/if-some+
          [v (cc/msg-header evt auth-token)] (ct/parse-basic-auth v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-load-routes
  [{:keys [routes]}] (when-not (empty? routes) (cr/load-routes routes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- resume-on-expiry
  [evt]
  (try (nc/reply-status (:socket evt) 500)
       (catch ClosedChannelException _
         (l/warn "channel closed already"))
       (catch Throwable t# (l/exception t#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- wsock-event<>
  "" [plug ch msg]
  (merge msg
         {:id (str "WsockMsg#" (u/seqint2))
          :socket ch
          :source plug
          :ssl? (nc/maybe-ssl? ch)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- http-event<>
  [plug ch req]
  (let [{:keys [cookies]
         {:keys [info]} :route} req
        {:keys [want-session?]
         {:keys [macit?]} :session} (xp/get-conf plug)]
    (merge req
           {:session (if (and (:session? info)
                              (c/!false? want-session?))
                       (ss/upstream (-> plug po/parent
                                        xp/pkey-bytes) cookies macit?))
            :source plug
            :stale? false
            :id (str "HttpMsg#" (u/seqint2))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>
  [plug ch msg]
  (cond (is? WsockMsg msg)
        (wsock-event<> plug ch msg)
        (satisfies? cc/HttpMsgGist msg)
        (http-event<> plug ch msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- funky
  [evt] (if (satisfies? cc/HttpMsgGist evt)
          (let [res (cc/http-result evt)] (fn [h e] (h e res)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- boot!
  [plug]
  (let [asset! #'czlab.wabbit.plugs.mvc/asset-loader
        {:keys [wait-millis] :as cfg} (xp/get-conf plug)]
    (l/debug "boot! http-plug: %s." cfg)
    (sv/tcp-server<>
      (assoc cfg
             :hh1
             (fn [ctx msg]
               (let [ev (evt<> plug (nc/ch?? ctx) msg)
                     {:keys [uri2]
                      {:keys [info status?]} :route} msg
                     {:keys [static? handler]} info
                     hd (if (and static?
                                 (nil? handler)) asset! handler)]
                 (l/debug "route status= %s, info= %s." status? info)
                 ;;(if (spos? wait-millis) (hold-event co ev wait-millis))
                 (if-not status?
                   (pc/error! ev
                              (Exception. (str "Bad route uri: " uri2)))
                   (pc/dispatch! ev {:handler hd
                                     :dispfn (funky ev)}))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn process-orphan
  "" [evt error]
  ;; 500 or 503
  (try (nc/reply-status (:socket evt) 500)
       (finally
         (l/warn "processing orphan/error event: %s." error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn discarder!
  "" [func arg]
  (let [w (ds/discard-httpd<> func arg)] (po/start w arg) #(po/stop w)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- basicfg
  "Basic http config."
  [server conf cfg0]
  (let [pk (xp/pkey-chars server)
        clj (xp/cljrt server)
        {:keys [passwd port
                server-key error] :as cfg} (merge conf cfg0)]
    (if (s/hgl? server-key)
      (c/test-cond "server-key file url"
                   (cs/starts-with? server-key "file:")))
    (merge cfg
           {:port (if-not (c/spos? port) (if (s/hgl? server-key) 443 80) port)
            :routes (maybe-load-routes cfg)
            :error (if-not (and error
                                (not (var? error)))
                     error (rt/var* clj (s/kw->str error)))
            :passwd (co/pw-text (co/pwd<> passwd pk))
            :server-key (if (s/hgl? server-key) (io/as-url server-key))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet
  [server _id spec]
  (let [impl (atom {:info (:info spec)
                    :timer (Timer. true)
                    :conf (:conf spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :handler]))
      (get-conf [_] (:conf @impl))
      (err-handler [_]
        (or (get-in @impl
                    [:conf :error]) (:error spec)))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (let [k (xp/pkey-chars server)
              cfg (b/prevar-cfg (basicfg server k (:conf @impl) arg))
              {:keys [public-root-dir page-dir]} (:wsite cfg)
              pub (io/file (str public-root-dir) (str page-dir))]
          (l/info (str "http-plug: page-dir= %s.\n"
                       "http-plug: pub-dir= %s.") page-dir pub)
          (swap! impl
                 #(assoc %
                         :conf
                         (if-not (i/dir-read-write? pub)
                           cfg
                           (assoc cfg :ftl-cfg (mvc/gen-ftl-config {:root pub})))))))
      po/Finzable
      (finz [me] (po/stop me))
      po/Startable
      (start [_] (po/start _ nil))
      (start [me arg]
        (let [w (boot! me)]
          (swap! impl #(assoc % :boot w))
          (po/start w (:conf @impl))))
      (stop [me]
        (let [{:keys [boot]} @impl] (some-> boot po/stop))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def HTTPSpec {:deps {:$auth [:czlab.wabbit.shiro.core/web-auth]}
               :error :czlab.wabbit.plugs.http/process-orphan
               :info {:name "Web Site"
                      :version "1.0.0"}
               :conf {:max-mem-size (* 1024 1024 4)
                      :$pluggable ::http<>
                      :max-msg-size -1
                      :wait-millis 0
                      :sock-time-out 0
                      :host ""
                      :port 9090
                      :server-key ""
                      :passwd ""
                      :error nil
                      :handler nil
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
                      :wsite {:public-root-dir "${wabbit.user.dir}/public"
                              :media-dir "res"
                              :page-dir "htm"
                              :js-dir "jsc"
                              :css-dir "css"}
                      :routes [{:mount "res/{}" :uri "/(favicon\\..+)"}
                               {:mount "{}" :uri "/public/(.*)"}
                               {:uri "/?" :template  "main/index.html"}]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn http<> ""
  ([_ id]
   (http<> _ id HTTPSpec))
  ([co id spec]
   (let [pspec (update-in spec
                            [:conf] b/expand-vars-in-form)
         clj (xp/cljrt co)
         e (:error pspec)
         e (if e (rt/var* clj (s/kw->str e)))]
     (pluglet co id (assoc pspec :error e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

