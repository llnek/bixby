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

  czlab.skaro.sys.ext

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
             seqint2
             convLong
             bytesify]]
    [czlab.dbio.core
     :refer [dbspec<>
             dbpool<>
             dbschema<>]])

  (:use
    [czlab.skaro.sys.core]
    [czlab.skaro.io.core]
    [czlab.skaro.sys.dfts]
    [czlab.skaro.io.loops]
    [czlab.skaro.io.mails]
    [czlab.skaro.io.files]
    [czlab.skaro.io.jms]
    [czlab.skaro.io.http]
    [czlab.skaro.io.netty]
    [czlab.skaro.io.socket]
    [czlab.skaro.mvc.filters]
    [czlab.skaro.mvc.ftlshim])

  (:import
    [czlab.skaro.etc PluginFactory Plugin]
    [czlab.server Service ServiceHandler]
    [czlab.skaro.runtime AppMain]
    [czlab.dbio Schema JDBCPool DBAPI]
    [java.io File StringWriter]
    [czlab.skaro.server
     Component
     Cljshim
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
     Schedulable
     Versioned
     Hierarchial
     XData
     CU
     Muble
     I18N
     Morphable
     Activable
     Startable
     Disposable
     Identifiable]
    [czlab.skaro.io IOEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getAppKeyFromEvent

  "Get the secret application key"
  ^String
  [^IOEvent evt]

  (.getAppKey ^Container (.. evt emitter server)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool

  ""
  ^JDBCPool
  [^Container co ^String gid]

  (let [dk (stror gid DEF_DBID)]
    (-> (.getv (.getx co) :dbps)
        (get (keyword dk)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI

  ""
  ^DBAPI
  [^Container co ^String gid]

  (when-some [p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (->> (.getv (.getx co) :schema)
         (dbopen<+> p ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources

  ""
  [^Container co]

  (log/info "container releasing all system resources")
  (when-some [sc (.getv (.getx co) :core)]
    (.dispose ^Disposable sc))
  (doseq [[k v]
          (.getv (.getx co) :dbps)]
    (log/debug "shutting down dbpool %s" (name k))
    (.shutdown ^JDBCPool v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn container<>

  ""
  ^Container
  [^Execvisor parObj ^AppGist gist]

  (log/info "creating a container: %s" (.id gist))
  (let
    [pid (format "%s#%d" (.id gist) (seqint2))
     ctx (.getx gist)
     appDir (io/file (.getv ctx :path))
     pub (io/file appDir DN_PUBLIC DN_PAGES)
     ftlCfg (genFtlConfig :root pub)
     impl (muble<> {:services {}})]
    (with-meta
      (reify

        Container

        (appKeyBits [this] (bytesify (.appKey this)))
        (appKey [_] (.getv impl :disposition ))
        (appDir [this] appDir)
        (getx [_] impl)
        (version [_] (.version gist))
        (id [_] pid)
        (name [_] (.getv impl :name))
        (cljrt [_] (.getv impl :shim))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))

        (setParent [_ x])
        (parent [_] parObj)

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
          (let [c (->> (.getv impl :envConf)
                       (:container ))]
            (not (false? (:enabled c)))))

        (service [_ sid]
          (-> (.getv impl :services)
              (get (keyword sid))))

        (hasService [_ sid]
          (-> (.getv impl :services)
              (contains (keyword sid))))

        (core [_]
          (.getv impl :core))

        (envConfig [_]
          (.getv impl :envConf))

        (appConfig [_]
          (.getv impl :appConf))

        (start [this]
          (let [svcs (.getv impl :services)
                main (.getv impl :mainApp)]
            (log/info "container starting all services...")
            (doseq [[k v] svcs]
              (log/info "service: %s about to start..." k)
              (.start ^Service v))
            (log/info "container starting main app...")
            (when (some? main)
              (.start ^AppMain main))))

        (stop [this]
          (let [svcs (.getv impl :services)
                pugs (.getv impl :plugins)
                main (.getv impl :mainApp)]
            (log/info "container stopping all services...")
            (doseq [[k v] svcs]
              (.stop ^Service v))
            (log/info "container stopping all plugins...")
            (doseq [[k v] pugs]
              (.stop ^Plugin v))
            (log/info "container stopping...")
            (when (some? main)
              (.stop ^AppMain main))))

        (dispose [this]
          (let [svcs (.getv impl :services)
                pugs (.getv impl :plugins)
                main (.getv impl :mainApp)]
            (log/info "container dispose(): all services")
            (doseq [[k v] svcs]
              (.dispose ^Service v))
            (log/info "container dispose(): all plugins")
            (doseq [[k v] pugs]
              (.dispose ^Plugin v))
            (when (some? main)
              (.dispose ^AppMain main))
            (log/info "container dispose() - main app disposed")
            (releaseSysResources this) )))

    {:typeid ::Container})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- service<>

  ""
  ^Service
  [^Container co svcType nm cfg0]

  (let
    [^Execvisor exe (.parent co)
     bks (.getv (.ctx exe) :emitters)]
    (if-some
      [^EmitterGist
       bk (get bks (keyword svcType))]
      (let
        [cfg (merge (.impl (.getx bk)) cfg0)
         pkey (.getAppKey co)
         eid (.id bk)
         hid (:handler cfg)
         obj (emitter<> co eid nm)]
        (log/info "about to create emitter: %s" eid)
        (log/info "emitter meta: %s" (meta obj))
        (log/info "config params =\n%s" cfg)
        (comp->initialize obj co cfg)
        (log/info "emitter - ok. handler => %s" hid)
        obj)
      (trap! ServiceError (str "No such emitter: " svcType)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOneService

  [^Container co nm cfg]

  (let
    [ctx (.getx co)
     cc (.getv ctx :services)
     {:keys [service enabled]}
     cfg ]
    (if-not (or (false? enabled)
                (nichts? service))
      (let [v (service<> co service nm cfg)]
        (->> (assoc cc (.id v) v)
             (.setv ctx :services))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doServices

  ""
  [^Container co]

  (when-some+
    [s (:services (.getv (.getx co) :envConf))]
    (doseq [[k v] s]
      (doOneService co k v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn container<>

  "Create an application container"
  ^Component
  [^Execvisor exe ^AppGist gist]

  (doto (mkctr gist)
    (comp->initialize c )))

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
(defn- doCljApp

  ""
  [^Container ctr ^AppMain m options]

  (.contextualize m ctr)
  (.init m options)
  m)

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

  (let [b (.exists (fmtPluginFname v appDir))]
    (if b (log/info "plugin %s already initialized" v))
    b))

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
  [^Container co ^String v ^File appDir env app]

  (let
    [^Cljshim
     rts (.getv (.getx co) :shim)
     e? (pluginInited? v appDir)
    ^PluginFactory
    pf (try! (cast? PluginFactory
                    (.call rts v)))
    u (when (some? pf)
        (.createPlugin pf co))]
    (when (some? u)
      (log/info "plugin->factory: %s" v)
      (.init u {:new? (not e?)
                :env env :app app })
      (postInitPlugin v appDir)
      (log/info "plugin %s starting..." v)
      (.start u)
      (log/info "plugin %s started" v)
      u)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs

  ""
  [^Container co env app]

  (persistent!
    (reduce
      #(let [[k v] %2]
         (if-not (false? (:status v))
           (let [pwd (passwd<> (:passwd v)
                               (.getAppKey co))
                 cfg (merge v {:passwd (.text pwd)
                               :id k})]
             (->> (dbpool<> (dbspec<> cfg) cfg)
                  (assoc! %1 k)))
           %1))
      (transient {})
      (seq  (get-in env [:databases :jdbc])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dftAppMain<>

  ""
  ^AppMain
  []

  (reify AppMain
    (contextualize [_ c])
    (init [_ arg])
    (start [_] )
    (stop [_])
    (dispose [_] )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Container
  [^Container co & [^Component execv]]

  (log/info "comp->initialize: Container: %s" (.id co))
  (let
    [cfgDir (io/file (.appDir co) DN_CONF)
     envConf (parseConfile appDir CFG_ENV_CF)
     appConf (parseConfile appDir CFG_APP_CF)]
    (doto (.getx co)
      (.setv :envConf envConf)
      (.setv :appConf appConf)))
  (let
    [appDir (.appDir co)
     ctx (.getx co)
     pid (.id co)
     env (.getv ctx :envConf)
     app (.getv ctx :appConf)
     mcz (strim (get-in app [:info :main]))
     cfg (:container env)
     lg (lcase (or (get-in env [:locale :lang]) "en"))
     cn (lcase (get-in env [:locale :country]))
     loc (if-not (hgl? cn)
           (Locale. lg) (Locale. lg cn))
     rts (CLJShim/newrt (getCldr) (juid))
     cpu (scheduler<> pid)
     res (->>
           (format "%s_%s.%s"
                   "Resources"
                   (str loc)
                   "properties")
           (io/file appDir "i18n"))]
    (.setv ctx :core cpu)
    (.setv ctx :shim rts)
    (when-some
     [rb (if (fileRead? res)
           (loadResource res))]
     (I18N/setBundle pid  rb))
    (doto->>
      (maybeInitDBs co env app)
      (.setv ctx :dbps )
      (log/debug "db [dbpools]\n%s" ))
    ;; handle the plugins
    (->>
      (persistent!
        (reduce
          #(->>
             (doOnePlugin
               co (last %2) appDir env app)
             (assoc! %1 (first %2)))
          (transient {})
          (seq (:plugins app))))
      (.setv ctx :plugins))
    ;; build the user data-models or create a default one
    (when-some+ [dmCZ (:data-model app)]
      (log/info "data-model schema-func: %s" dmCZ)
      (when-some [sc (try! (.call rts dmCZ))]
        (when-not (inst? Schema sc)
          (trap! ConfigError (str "Invalid schema " sc)))
        (.setv ctx :schema sc)))
    (let
      [m
       (if-not (hgl? mCZ)
         (do
           (log/warn "no main defined, using default")
           (dftAppMain))
         (.call rts mCZ))]
      (if (inst? AppMain m)
        (do
          (doCljApp co m app)
          (.setv ctx :main m))
        (trap! ConfigError (str "Invalid main " mCZ))))
    (when-some+
      [svcs (:services env)]
      (services<> co svcs))
    ;; start the scheduler
    (.activate ^Activable cpu cfg)
    (log/info "app class-loader: %s" cl)
    (log/info "app: %s initialized - ok" pid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


