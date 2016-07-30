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

  czlab.skaro.impl.ext

  (:require
    [czlab.xlib.resources :refer [loadResource]]
    [czlab.xlib.scheduler :refer [scheduler<>]]
    [czlab.xlib.str :refer [stror lcase strim]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.dbio.connect :refer [dbopen<+>]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.xlib.files
     :refer [writeFile
             readFile
             fileRead?]]
    [czlab.xlib.core
     :refer [loadJavaProps
             convToJava
             muble<>
             doto->>
             juid
             fpath
             cast?
             trap!
             try!!
             nbf
             seqlong
             convLong
             bytesify]]
    [czlab.dbio.core
     :refer [dbspec<>
             dbpool<>
             dbschema<>]])

  (:use
    [czlab.skaro.core.consts]
    [czlab.skaro.io.core]
    [czlab.skaro.impl.dfts]
    [czlab.skaro.io.loops]
    [czlab.skaro.io.mails]
    [czlab.skaro.io.files]
    [czlab.skaro.io.jms]
    [czlab.skaro.io.http]
    [czlab.skaro.io.netty]
    [czlab.skaro.io.socket]
    [czlab.skaro.mvc.filters]
    [czlab.skaro.mvc.ftlshim]
    [czlab.skaro.core.sys])

  (:import
    [czlab.server EventEmitter ServiceHandler]
    [czlab.skaro.runtime AppMain]
    [czlab.skaro.etc PluginFactory Plugin]
    [czlab.dbio Schema JDBCPool DBAPI]
    [java.io File StringWriter]
    [czlab.skaro.server
     CLJShim
     Context
     Container
     ConfigError]
    [freemarker.template
     Configuration
     Template
     DefaultObjectWrapper]
    [java.util Locale]
    [java.net URL]
    [czlab.xlib
     Versioned
     Hierarchial
     XData
     Schedulable
     CU
     Mutable
     Muble
     I18N
     Morphable
     Activable
     Startable
     Disposable
     Identifiable]
    [czlab.skaro.server
     Registry
     Service
     Component
     ServiceError]
    [czlab.skaro.io IOEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getAppKeyFromEvent

  "Get the secret application key"
  ^String
  [^IOEvent evt]

  (.getAppKey ^Container (.. evt emitter container)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool

  ""
  ^JDBCPool
  [^Muble co ^String gid]

  (let [dk (stror gid DEF_DBID)]
    (-> (.getv co K_DBPS)
        (get (keyword dk)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI

  ""
  ^DBAPI
  [^Muble co ^String gid]

  (let [mcache (.getv co K_MCACHE)
        p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (when (some? p)
      (dbopen<+> p mcache {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources

  ""
  [^Muble co]

  (let [^Schedulable sc (.getv co K_SCHEDULER)
        dbs (.getv co K_DBPS)]
    (log/info "container releasing all system resources")
    (when (some? sc) (.dispose sc))
    (doseq [[k v] dbs]
      (log/debug "shutting down dbpool %s" (name k))
      (.shutdown ^JDBCPool v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkctr

  ""
  ^Container
  [appDir options]

  (log/info "creating a container: %s" (:name options))
  (let [pub (io/file appDir DN_PUBLIC DN_PAGES)
        ftlCfg (genFtlConfig :root pub)
        pid (str "c#" (seqint))
        impl (muble<>)]
    (with-meta
      (reify

        Context

        (getx [_] impl)

        Container

        (getAppKeyBits [this] (bytesify (.getAppKey this)))
        (getAppDir [this] (.getv impl K_APPDIR))
        (getAppKey [_] (:appKey options))
        (name [_] (:name options))
        (getCljRt [_] (.getv impl :cljshim))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))

        (loadTemplate [_ tpath ctx]
          (let
            [tpl (str tpath)
             ts (str (if (.startsWith tpl "/") "" "/") tpl)
             out (renderFtl ftlCfg ts ctx)]
            {:data (xdata<> out)
             :ctype
             (cond
               (.endsWith tpl ".json") "application/json"
               (.endsWith tpl ".xml") "application/xml"
               (.endsWith tpl ".html") "text/html"
               :else "text/plain")} ))

        (isEnabled [_]
          (let [env (.getv impl K_ENVCONF)
                c (:container env) ]
            (not (false? (:enabled c)))))

        (getService [_ sid]
          (let [^Registry
                srg (.getv impl K_SVCS)]
            (.lookup srg (keyword sid))))

        (hasService [_ sid]
          (let [^Registry
                srg (.getv impl K_SVCS)]
            (.has srg (keyword sid))))

        (core [this]
          (.getv impl K_SCHEDULER))

        (getEnvConfig [_]
          (.getv impl K_ENVCONF))

        (getAppConfig [_]
          (.getv impl K_APPCONF))

        Component

        (version [_] "1.0")
        (id [_] pid)

        Hierarchial

        (parent [_] nil)

        Startable

        (start [this]
          (let [^Registry
                srg (.getv impl K_SVCS)
                main (.getv impl :main-app) ]
            (log/info "container starting all services...")
            (doseq [[k v] (.iter srg)]
              (log/info "service: %s about to start..." k)
              (.start ^Startable v))
            (log/info "container starting main app...")
            (when (some? main)
              (.start ^Startable main))))

        (stop [this]
          (let [^Registry
                srg (.getv impl K_SVCS)
                pls (.getv impl K_PLUGINS)
                main (.getv impl :main-app)]
            (log/info "container stopping all services...")
            (doseq [[k v] (.iter srg)]
              (.stop ^Startable v))
            (log/info "container stopping all plugins...")
            (doseq [[k v] pls]
              (.stop ^Plugin v))
            (log/info "container stopping...")
            (when (some? main)
              (.stop ^Startable main))))

        Disposable

        (dispose [this]
          (let [^Registry
                srg (.getv impl K_SVCS)
                pls (.getv impl K_PLUGINS)
                main (.getv impl :main-app)]
            (log/info "container dispose(): all services")
            (doseq [[k v] (.iter srg)]
              (.dispose ^Disposable v))
            (log/info "container dispose(): all plugins")
            (doseq [[k v] pls]
              (.dispose ^Disposable v))
            (when (some? main)
              (.dispose ^Disposable main))
            (log/info "container dispose() - main app disposed")
            (releaseSysResources this) )))

    {:typeid ::Container})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- service<>

  ""
  ^EventEmitter
  [^Context co svc nm cfg0]

  (let
    [^Registry
     bks (-> ^Registry
             (.getv (.getx co) K_COMPS)
             (.lookup K_BLOCKS))]
    (if-some
      [^Muble
       bk (.lookup bks (keyword svc))]
      (let
        [cfg (merge (.getv bk :dftOptions) cfg0)
         pkey (.getAppKey ^Container co)
         eid (.id ^Identifiable bk)
         hid (:handler cfg)
         obj (emitter<> co eid nm)]
        (log/info "about to synthesize emitter: %s" eid)
        (log/info "emitter meta: %s" (meta obj))
        (log/info "config params =\n%s" cfg)
        (comp->synthesize
          obj
          {:ctx co
          :props (assoc cfg :appkey pkey) })
        (log/info "emitter - ok. handler => %s" hid)
        obj)
      (trap! ServiceError (str "No such Service: " svc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- oneService<>

  [^Muble co nm cfg]

  (let [^Registry srg (.getv co K_SVCS)
        {:keys [service enabled]}
        cfg ]
    (if-not (or (false? enabled)
                (empty? service))
      (->> (service<> co service nm cfg)
           (.reg srg )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- services<>

  ""
  [^Muble co]

  (let [env (.getv co K_ENVCONF)
        s (:services env) ]
    (if-not (empty? s)
      (doseq [[k v] s]
        (oneService<> co k v)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn container<>

  "Create an application container"
  ^Container
  [^Context co options]

  (let [c (mkctr appDir options)
        ctx (.getx co)]
    (->>
      (-> ^Registry
          (.getv ctx K_COMPS)
          (.lookup K_APPS))
      (comp->compose c))
    (comp->contextualize c ctx)
    (comp->configure c options)
    (comp->initialize c)
    (.start ^Startable c)
    c))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseConfile

  ""
  [^File appDir ^String conf]

  (-> (readFile (io/file appDir conf))
      (subsVar)
      (cs/replace "${appdir}" (fpath appDir))
      (readEdn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  :czlab.skaro.impl.ext/Container
  [^Muble co props]

  (let [srg (registry<> ::EventEmitters K_SVCS "1.0" co)
        appDir (K_APPDIR props)
        cfgDir (io/file appDir DN_CONF)
        envConf (parseConfile appDir CFG_ENV_CF)
        appConf (parseConfile appDir CFG_APP_CF)]
    ;; make registry to store services
    (comp->synthesize srg {})
    ;; store references to key attributes
    (doto co
      (.setv K_APPDIR appDir)
      (.setv K_SVCS srg)
      (.setv K_ENVCONF envConf)
      (.setv K_APPCONF appConf))
    (log/info "container: configured app: %s" (.id ^Identifiable co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doCljApp

  ""
  [ctr opts ^AppMain obj]

  (.contextualize obj ctr)
  (.configure obj opts)
  (.initialize obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname

  ""
  ^File
  [^String v ^File appDir]

  (->> (cs/replace v #"[\./]+" "")
       (io/file appDir "modules" )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pluginInited?

  ""
  [v appDir]

  (.exists (fmtPluginFname v appDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postInitPlugin

  ""
  [^String v ^File appDir]

  (let [pfile (fmtPluginFname v appDir)]
    (writeFile pfile "ok")
    (log/info "initialized plugin: %s" v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin

  ""
  ^Plugin
  [^Muble co ^String v ^File appDir env app]

  (let [^CLJShim rts (.getv co :cljshim)
        pf (try!! nil (.call rts v))
        u (when (inst? PluginFactory pf)
            (.createPlugin ^PluginFactory pf
                           ^Container co))]
    (when-some [^Plugin p (cast? Plugin u)]
      (log/info "calling plugin-factory: %s" v)
      (.configure p { :env env :app app })
      (if (pluginInited? v appDir)
        (log/info "plugin %s already initialized" v)
        (do
          (.initialize p)
          (postInitPlugin v appDir)))
      (log/info "plugin %s starting..." v)
      (.start p)
      (log/info "plugin %s started" v)
      p)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitPoolSize

  ""
  [^String s]

  (let [pos (.indexOf s (int \:)) ]
    (if (< pos 0)
      [ 1 (convLong (strim s) 4) ]
      [ (convLong (strim (.substring s 0 pos)) 4)
        (convLong (strim (.substring s (+ pos 1))) 1) ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs

  ""
  [^Container co env app]

  (with-local-vars [p (transient {}) ]
    (let [cfg (get-in env [:databases :jdbc])
          pkey (.getAppKey co) ]
      (doseq [[k v] cfg]
        (when-not (false? (:status v))
          (let [[t c]
                (splitPoolSize (str (:poolsize v)))]
            (var-set p
                     (->> (mkDbPool
                            (mkJdbc k v
                                      (pwdify (:passwd v) pkey))
                                      {:max-conns c
                                       :min-conns 1
                                       :partitions t
                                       :debug (nbf (:debug v)) })
                          (assoc! @p k)))))))
    (persistent! @p)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkDftAppMain ""

  []

  (reify AppMain
    (contextualize [_ ctr] )
    (initialize [_])
    (configure [_ cfg] )
    (start [_] )
    (stop [_])
    (dispose [_] )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compInitialize :czc.skaro.ext/Cocoon

  [^Muble co]

  (let [cl (-> (Thread/currentThread)
               (.getContextClassLoader))
        rts (CLJShim/newrt cl (juid))
        appDir (.getv co K_APPDIR)
        pid (.id ^Component co)]
    (log/info "initializing container: %s" pid)
    (.setv co :cljshim rts)
    (let [cpu (mkScheduler (str pid))
          env (.getv co K_ENVCONF)
          app (.getv co K_APPCONF)
          mCZ (strim (get-in app [:info :main]))
          dmCZ (str (:data-model app))
          reg (.getv co K_SVCS)
          cfg (:container env)
          lg (lcase (or (get-in env [K_LOCALE K_LANG]) "en"))
          cn (lcase (get-in env [K_LOCALE K_COUNTRY]))
          loc (if (empty? cn)
                (Locale. lg)
                (Locale. lg cn))
          res (io/file appDir "i18n"
                       (str "Resources_"
                            (.toString loc) ".properties")) ]
      (when (fileRead? res)
        (when-some [rb (loadResource res)]
          (I18N/setBundle (.id ^Identifiable co) rb)))

      (doto->> (maybeInitDBs co env app)
               (.setv co K_DBPS )
               (log/debug "db [dbpools]\n%s" ))

      ;; handle the plugins
      (.setv co K_PLUGINS
             (persistent!
               (reduce
                 #(->> (doOnePlugin co
                                    (last %2)
                                    appDir env app)
                       (assoc! %1
                               (first %2)))
                 (transient {})
                 (seq (:plugins app))) ))
      (.setv co K_SCHEDULER cpu)

      ;; build the user data-models or create a default one
      (log/info "application data-model schema-class: %s" dmCZ)
      (let [sc (trycr nil (.call rts dmCZ))]
        (when (and (some? sc)
                   (not (instance? Schema sc)))
          (trap! ConfigError (str "Invalid Schema Class " dmCZ)))
        (.setv co
               K_MCACHE
               (mkMetaCache (or sc (mkDbSchema [])))))

      (when (empty? mCZ) (log/warn "============> NO MAIN-CLASS DEFINED"))
      ;;(test-nestr "Main-Class" mCZ)

      (with-local-vars
        [obj (trycr nil (.call rts mCZ))]
        (when (nil? @obj)
          (log/warn "failed to create main class: %s" mCZ)
          (var-set obj (mkDftAppMain)))
        (if
          (instance? AppMain @obj)
          (doCljApp co app @obj)
          ;else
          (trap! ConfigError (str "Invalid Main Class " mCZ)))

        (.setv co :main-app @obj)
        (log/info "application main-class %s%s"
                  (stror mCZ "???")
                  " created and invoked"))

      (let [svcs (:services env) ]
        (if (empty? svcs)
          (log/warn "no system service defined in env.conf")
          (reifyServices co)))

      ;; start the scheduler
      (.activate ^Activable cpu cfg)

      (log/info "container app class-loader: %s"
                (-> cl
                    (.getClass)
                    (.getName)))
      (log/info "initialized app: %s" (.id ^Identifiable co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


