;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.sys.runc

  (:require [czlab.horde.dbio.connect :refer [dbopen<+>]]
            [czlab.xlib.resources :refer [loadResource]]
            [czlab.xlib.scheduler :refer [scheduler<>]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.horde.dbio.core
             :refer [dbspec<>
                     dbpool<>
                     dbschema<>]])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.wabbit.io.core]
        [czlab.wabbit.io.loops]
        [czlab.wabbit.io.mails]
        [czlab.wabbit.io.files]
        [czlab.wabbit.io.jms]
        [czlab.wabbit.io.http]
        [czlab.wabbit.io.socket]
        [czlab.wabbit.mvc.ftl])

  (:import [czlab.wabbit.base Gist ServiceError ConfigError]
           [czlab.wabbit.pugs PluginFactory Plugin]
           [czlab.xlib I18N Activable Disposable]
           [czlab.horde Schema JdbcPool DbApi]
           [java.io File StringWriter]
           [czlab.wabbit.server
            Execvisor
            Cljshim
            Service
            Container]
           [freemarker.template
            Configuration
            Template
            DefaultObjectWrapper]
           [java.util Locale]
           [java.net URL]
           [czlab.wabbit.io IoEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getPodKeyFromEvent
  "Get the secret application key"
  ^String [^IoEvent evt] (.. evt source server podKey))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool
  ""
  ^JdbcPool
  [^Container co gid]
  (get
    (.getv (.getx co) :dbps)
    (keyword (stror gid dft-dbid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI
  ""
  ^DbApi
  [^Container co ^String gid]
  (when-some
    [p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (dbopen<+> p
               (.getv (.getx co) :schema))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources
  ""
  [^Container co]
  (log/info "pod releasing system resources")
  (if-some
    [sc (.getv (.getx co) :core)]
    (.dispose ^Disposable sc))
  (doseq [[k v]
          (.getv (.getx co) :dbps)]
    (log/debug "shutting down dbpool %s" (name k))
    (.shutdown ^JdbcPool v))
  (some-> (.cljrt co) (.close)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- svc<>
  ""
  ^Service
  [^Container co svc nm cfg0]
  (let
    [^Execvisor exe (.parent co)
     bks (.getv (.getx exe) :emitters)]
    (if-some
      [bk (bks svc)]
      (let
        [ios (doto
               (service<> co svc nm bk)
               (.init cfg0))
         cfg0 (.config ios)]
        (log/info "preparing io-service %s..." svc)
        (log/info "config params=\n%s" cfg0)
        (log/info "service - ok")
        ios)
      (trap! ServiceError
             (str "No such service: " svc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ioServices<>
  ""
  [^Container co svcs]
  (->>
    (preduce<map>
      #(let
         [[k cfg] %2
          {:keys [service
                  enabled?]} cfg]
         (if-not (or (false? enabled?)
                     (nil? service))
           (let [v (svc<> co service k cfg)]
             (assoc! %1 (.id v) v))
           %1))
      svcs)
    (.setv (.getx co) :services )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- plugin<>
  ""
  ^Plugin
  [^Container co [kee pc] env]
  (log/info "plugin: %s -> %s" kee pc)
  (let
    [rts (.cljrt co)
     [pn opts]
     (if (string? pc)
       [pc {}] [(:factory pc) pc])
     _ (log/info "plugin fac: %s" pn)
     u (cast? Plugin
              (.callEx rts pn (vargs* Object co)))]
    (log/info "plugin-obj: %s" u)
    (if (some? u)
      (do
        (.init u {:pod env :pug opts})
        (log/info "plugin %s starting..." pn)
        (.start u)
        (log/info "plugin %s started" pn)
        u)
      (do->nil
        (log/warn "failed to create plugin %s" kee)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs
  ""
  [^Container co env]
  (preduce<map>
    #(let
       [[k v] %2]
       (if-not (false? (:enabled? v))
         (let
           [pwd (passwd<> (:passwd v)
                          (.podKey co))
            cfg (merge v
                       {:passwd (.text pwd)
                        :id k})]
           (->> (dbpool<> (dbspec<> cfg) cfg)
                (assoc! %1 k)))
         %1))
    (:rdbms env)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init
  ""
  [^Container co ^Execvisor execv]

  (logcomp "comp->init" co)
  (let
    [cpu (scheduler<> (.id co))
     {:keys [env] :as conf}
     (.intern (.getx execv))
     rts (.cljrt co)
     pid (.id co)
     mcz (get-in env
                 [:info :main])
     ^Locale loc (:locale conf)
     res (->>
           (format c-rcprops (.getLanguage loc))
           (io/file (:podDir conf) dn-etc))]
    (.setv (.getx co) :core cpu)
    (if (fileRead? res)
      (->> (loadResource res)
           (I18N/setBundle pid)))
    (log/info "processing db-defs...")
    (doto->>
      (maybeInitDBs co env)
      (.setv (.getx co) :dbps)
      (log/debug "db [dbpools]\n%s"))
    (log/info "processing plugins...")
    (->>
      (preduce<map>
        #(if-some [p (plugin<> co %2 env)]
           (assoc! %1 (first %2) p)
           %1))
      (:plugins env)
      (.setv (.getx co) :plugins))
    ;; build the user data-models?
    (when-some+
      [dmCZ (:data-model env)]
      (log/info "schema-func: %s" dmCZ)
      (if-some
        [sc (cast? Schema
                   (try! (.callEx rts
                                  dmCZ
                                  (vargs* Object co))))]
        (.setv (.getx co) :schema sc)
        (trap! ConfigError
               "Invalid data-model schema ")))
    (.activate ^Activable cpu nil)
    (->> (:services env) (ioServices<> co))
    (if (hgl? mcz) (.call rts mcz))
    (log/info "pod: (%s) initialized - ok" pid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkctr
  ""
  ^Container
  [^Execvisor parObj ^Gist gist]
  (log/info "spawning pod: <<%s>>" (.id gist))
  (let
    [pid (str (.id gist) "@" (seqint2))
     rts (Cljshim/newrt (getCldr) pid)
     ctx (.getx gist)
     podPath (io/file (.getv ctx :path))
     pub (io/file podPath cfg-pub-pages)
     ftlCfg (genFtlConfig {:root pub})
     impl (muble<> {:services {}})]
    (with-meta
      (reify Container
        (podKeyBits [this] (bytesify (.podKey this)))
        (podKey [_] (.getv impl :digest))
        (podDir [this] podPath)
        (cljrt [_] rts)
        (getx [_] impl)
        (version [_] (.version gist))
        (id [_] pid)
        (name [_] (.getv impl :name))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))
        (acquireDbPool [this] (maybeGetDBPool this ""))
        (acquireDbAPI [this] (maybeGetDBAPI this ""))

        (setParent [_ x] (throwUOE "can't setParent!"))
        (parent [_] parObj)

        (loadTemplate [_ tpath ctx]
          (let
            [ts (str "/" (triml tpath "/"))
             out (renderFtl ftlCfg ts ctx)]
            {:data (xdata<> out)
             :ctype
             (cond
               (.endsWith ts ".json") "application/json"
               (.endsWith ts ".xml") "application/xml"
               (.endsWith ts ".html") "text/html"
               :else "text/plain")}))

        (isEnabled [_] true)

        (service [_ sid]
          ((.getv impl :services) (keyword sid)))

        (hasService [_ sid]
          (in? (.getv impl :services) (keyword sid)))

        (core [_]
          (.getv impl :core))

        (podConfig [_]
          (.getv impl :podConf))

        (init [this arg] (init this arg))

        (start [_ _]
          (let [svcs (.getv impl :services)]
            (log/info "pod starting io#services...")
            (doseq [[k v] svcs]
              (log/info "io-service: %s to start" k)
              (.start ^Service v))))

        (restart [_ _] (throwUOE "Can't restart"))

        (stop [_]
          (let [svcs (.getv impl :services)
                pugs (.getv impl :plugins)]
            (log/info "container stopping io#services...")
            (doseq [[k v] svcs]
              (.stop ^Service v))
            (log/info "container stopping plugins...")
            (doseq [[k v] pugs]
              (.stop ^Plugin v))
            (log/info "container stopping...")))

        (dispose [this]
          (let [svcs (.getv impl :services)
                pugs (.getv impl :plugins)]
            (log/info "container dispose(): io#services")
            (doseq [[k v] svcs]
              (.dispose ^Service v))
            (log/info "container dispose(): plugins")
            (doseq [[k v] pugs]
              (.dispose ^Plugin v))
            (releaseSysResources this))))

    {:typeid ::Container})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn container<>
  "Create an application container"
  ^Container
  [^Execvisor exe ^Gist gist]
  (doto (mkctr exe gist) (.init exe)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


