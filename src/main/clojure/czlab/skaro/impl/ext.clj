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

  czlab.skaro.impl.ext

  (:require
    [czlab.xlib.util.files :refer [ReadOneFile WriteOneFile FileRead?]]
    [czlab.xlib.util.str :refer [ToKW stror lcase strim]]
    [czlab.xlib.dbio.connect :refer [DbioConnectViaPool]]
    [czlab.xlib.i18n.resources :refer [LoadResource]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.xlib.util.wfs :refer [WrapPTask NewJob SimPTask]]
    [czlab.xlib.crypto.codec :refer [Pwdify CreateRandomString]]
    [czlab.xlib.util.core
    :refer [MakeMMap doto->> juid FPath Cast?
    trycr ConvToJava nbf ConvLong Bytesify]]
    [czlab.xlib.util.scheduler :refer [MakeScheduler]]
    [czlab.xlib.util.core
    :refer [NextLong LoadJavaProps SubsVar]]
    [czlab.xlib.dbio.core
    :refer [MakeJdbc MakeMetaCache MakeDbPool MakeSchema]])

  (:use
    [czlab.skaro.io.core :rename {enabled? io-enabled?} ]
    [czlab.skaro.impl.dfts
    :rename {enabled? blockmeta-enabled?} ]
    ;;[czlab.xlib.util.consts]
    [czlab.skaro.core.consts]
    [czlab.skaro.io.loops]
    [czlab.skaro.io.mails]
    [czlab.skaro.io.files]
    [czlab.skaro.io.jms]
    [czlab.skaro.io.http]
    [czlab.skaro.io.netty]
    [czlab.skaro.io.jetty]
    [czlab.skaro.io.socket]
    [czlab.skaro.mvc.filters]
    [czlab.skaro.mvc.ftlshim]
    ;;[czlab.skaro.impl.misc]
    [czlab.skaro.core.sys])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io])

  (:import
    [com.zotohlab.skaro.core Muble Context Container ConfigError]
    [com.zotohlab.frwk.dbio MetaCache Schema JDBCPool DBAPI]
    [com.zotohlab.skaro.etc CliMain PluginFactory Plugin]
    [org.apache.commons.io FilenameUtils FileUtils]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.codec.binary Hex]
    [freemarker.template Configuration
    Template DefaultObjectWrapper]
    [java.util Locale Map Properties]
    [com.zotohlab.frwk.i18n I18N]
    [java.net URL]
    [java.io File StringWriter]
    [com.zotohlab.skaro.runtime AppMain RegoAPI PODMeta]
    [com.zotohlab.frwk.core Versioned Hierarchial
    Morphable Activable
    Startable Disposable Identifiable]
    [com.zotohlab.frwk.server ComponentRegistry
    Service Emitter
    Component ServiceHandler ServiceError]
    [com.zotohlab.skaro.io IOEvent]
    [com.zotohlab.frwk.util Schedulable CU]
    [com.zotohlab.frwk.io XData]
    [com.zotohlab.wflow Activity Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetAppKeyFromEvent

  "Get the secret application key"

  ^String
  [^IOEvent evt]

  (let [^Container c (.. evt emitter container)]
    (.getAppKey c)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ContainerAPI
(defprotocol ^:private ContainerAPI

  ""

  (reifyOneService [_ sid cfg] )
  (reifyService [_ svc sid cfg] )
  (reifyServices [_] )
  (generateNonce [_] )
  (generateCsrf [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A Service is an instance of a Block, that is, an instance of an event
;; emitter
(defn- makeServiceBlock ""

  ^Emitter
  [^Identifiable bk ctr nm cfg]

  (let [pkey (-> ^Container ctr (.getAppKey))
        hid (:handler cfg)
        eid (.id bk)
        obj (MakeEmitter ctr eid nm)
        mm (meta obj) ]
    (log/info "about to synthesize an emitter: %s" eid)
    (log/info "emitter meta: %s" mm)
    (log/info "is emitter = %s" (isa? (:typeid mm)
                                      :czc.skaro.io/Emitter))
    (log/info "config params =\n%s" cfg)
    (SynthesizeComponent obj
                         {:ctx ctr
                          :props (assoc cfg :app.pkey pkey) })
    ;;(.setf! obj :app.pkey pkey)
    (log/info "emitter synthesized - ok. handler => %s" hid)
    obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool ""

  ^JDBCPool
  [^Muble co ^String gid]

  (let [dbs (.getv co K_DBPS)
        dk (stror gid DEF_DBID) ]
    (get dbs (keyword dk))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI ""

  ^DBAPI
  [^Muble co ^String gid]

  (let [mcache (.getv co K_MCACHE)
        p (maybeGetDBPool co gid) ]
    (log/debug "acquiring from dbpool: %s" p)
    (if (nil? p)
      nil
      (DbioConnectViaPool p mcache {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources ""

  [^Muble co]

  (let [^Schedulable sc (.getv co K_SCHEDULER)
        dbs (.getv co K_DBPS) ]
    (log/info "container releasing all system resources")
    (when (some? sc) (.dispose sc))
    (doseq [[k v] dbs]
      (log/debug "shutting down dbpool %s" (name k))
      (.shutdown ^JDBCPool v))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAppContainer ""

  ^Container
  [^PODMeta pod options]

  (log/info "creating an app-container: %s" (.id ^Identifiable pod))
  (let [pub (io/file (K_APPDIR options) DN_PUBLIC DN_PAGES)
        ftlCfg (GenFtlConfig :root pub)
        impl (MakeMMap)
        ctxt (atom (MakeMMap)) ]
    (with-meta
      (reify

        Context

        (setx [_ x] (reset! ctxt x))
        (getx [_] @ctxt)

        Muble

        (setv [_ a v] (.setv impl a v) )
        (unsetv [_ a] (.unsetv impl a) )
        (getv [_ a] (.getv impl a) )
        (seq [_])
        (clear [_] (.clear impl))
        (toEDN [_] (.toEDN impl))

        Container

        (getAppKeyBits [this] (Bytesify (.getAppKey this)))
        (getAppDir [this] (.getv this K_APPDIR))
        (getAppKey [_] (.appKey pod))
        (getName [_] (.moniker pod))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))

        (loadTemplate [_ tpath ctx]
          (let [tpl (str tpath)
                ts (str (if (.startsWith tpl "/") "" "/") tpl)
                out (RenderFtl ftlCfg ts ctx)]
            {:data (XData. out)
             :ctype (cond
                      (.endsWith tpl ".json") "application/json"
                      (.endsWith tpl ".xml") "application/xml"
                      (.endsWith tpl ".html") "text/html"
                      :else "text/plain")} ))

        (isEnabled [_]
          (let [env (.getv impl K_ENVCONF)
                c (:container env) ]
            (if (false? (:enabled c))
              false
              true)))

        (getService [_ serviceId]
          (let [^ComponentRegistry
                srg (.getv impl K_SVCS) ]
            (.lookup srg (keyword serviceId))))

        (hasService [_ serviceId]
          (let [^ComponentRegistry
                srg (.getv impl K_SVCS) ]
            (.has srg (keyword serviceId))))

        (core [this]
          (.getv this K_SCHEDULER))

        (getEnvConfig [_]
          (.getv impl K_ENVCONF))

        (getAppConfig [_]
          (.getv impl K_APPCONF))

        Component

        (id [_] (.id ^Identifiable pod) )
        (version [_] "1.0")

        Hierarchial

        (parent [_] nil)

        Startable

        (start [this]
          (let [^RegoAPI
                srg (.getv impl K_SVCS)
                main (.getv impl :main-app) ]
            (log/info "container starting all services...")
            (doseq [[k v] (.iter srg) ]
              (log/info "service: %s about to start..." k)
              (.start ^Startable v))
            (log/info "container starting main app...")
            (when (some? main)
              (.start ^Startable main))))

        (stop [this]
          (let [^RegoAPI
                srg (.getv impl K_SVCS)
                pls (.getv this K_PLUGINS)
                main (.getv impl :main-app) ]
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
          (let [^RegoAPI
                srg (.getv impl K_SVCS)
                pls (.getv this K_PLUGINS)
                main (.getv impl :main-app) ]
            (log/info "container dispose(): all services")
            (doseq [[k v] (.iter srg) ]
              (.dispose ^Disposable v))
            (log/info "container dispose(): all plugins")
            (doseq [[k v] pls]
              (.dispose ^Disposable v))
            (when (some? main)
              (.dispose ^Disposable main))
            (log/info "container dispose() - main app disposed")
            (releaseSysResources this) ))

        ContainerAPI

        (generateNonce [_] (-> (CreateRandomString 18)
                               (Bytesify )
                               (Hex/encodeHexString )))

        (generateCsrf [_] (-> (CreateRandomString 18)
                              (Bytesify )
                              (Hex/encodeHexString )))

        (reifyServices [this]
          (let [env (.getv impl K_ENVCONF)
                s (:services env) ]
            (if-not (empty? s)
              (doseq [[k v] s]
                (reifyOneService this k v)))))

        (reifyOneService [this nm cfg]
          (let [^ComponentRegistry srg (.getv impl K_SVCS)
                svc (str (:service cfg))
                b (:enabled cfg) ]
            (if-not (or (false? b)
                        (empty? svc))
              (->> (reifyService this svc nm cfg)
                   (.reg srg )))))

        (reifyService [this svc nm cfg]
          (let [^Muble ctx (.getx this)
                ^ComponentRegistry
                root (.getv ctx K_COMPS)
                ^ComponentRegistry
                bks (.lookup root K_BLOCKS)
                ^ComponentRegistry
                bk (.lookup bks (keyword svc)) ]
            (when (nil? bk)
              (throw (ServiceError. (str "No such Service: " svc))))
            (makeServiceBlock bk this nm cfg))) )

    {:typeid (ToKW "czc.skaro.ext" "Container") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn MakeContainer

  "Create an application container"

  ^Container
  [^Context pod]

  (let [url (-> ^PODMeta pod (.srcUrl))
        ps {K_APPDIR (io/file url)}
        ^Muble ctx (.getx pod)
        ^ComponentRegistry
        root (.getv ctx K_COMPS)
        apps (.lookup root K_APPS)
        ^Startable
        c (makeAppContainer pod ps)]
    (CompCompose c apps)
    (CompContextualize c ctx)
    (CompConfigure c ps)
    (CompInitialize c)
    (.start c)
    c))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseConfile ""

  [^File appDir ^String conf]

  (-> (ReadOneFile (io/file appDir conf))
      (SubsVar)
      (cs/replace "${appdir}" (FPath appDir))
      (ReadEdn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.ext/Container

  [^Muble co props]

  (let [srg (MakeRegistry :EventSources K_SVCS "1.0" co)
        appDir (K_APPDIR props)
        cfgDir (io/file appDir DN_CONF)
        envConf (parseConfile appDir CFG_ENV_CF)
        appConf (parseConfile appDir CFG_APP_CF) ]
    ;; make registry to store services
    (SynthesizeComponent srg {})
    ;; store references to key attributes
    (doto co
      (.setv K_APPDIR appDir)
      (.setv K_SVCS srg)
      (.setv K_ENVCONF envConf)
      (.setv K_APPCONF appConf))
    (log/info "container: configured app: %s" (.id ^Identifiable co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doCljApp ""

  [ctr opts ^AppMain obj]

  (.contextualize obj ctr)
  (.configure obj opts)
  (.initialize obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname ""

  ^File
  [^String v ^File appDir]

  (->> (cs/replace v #"[\./]+" "")
       (io/file appDir "modules" )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pluginInited? ""

  [v appDir]

  (.exists (fmtPluginFname v appDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postInitPlugin ""

  [^String v ^File appDir]

  (let [pfile (fmtPluginFname v appDir) ]
    (WriteOneFile pfile "ok")
    (log/info "initialized plugin: %s" v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin ""

  ^Plugin
  [^Muble co ^String v ^File appDir env app]

  (let [^CliMain rts (.getv co :cljshim)
        pf (trycr nil (.call rts v))
        u (when (instance? PluginFactory pf)
            (.createPlugin ^PluginFactory pf
                           ^Container co)) ]
    (when-some [^Plugin p (Cast? Plugin u)]
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
(defn- splitPoolSize ""

  [^String s]

  (let [pos (.indexOf s (int \:)) ]
    (if (< pos 0)
      [ 1 (ConvLong (strim s) 4) ]
      [ (ConvLong (strim (.substring s 0 pos)) 4)
        (ConvLong (strim (.substring s (+ pos 1))) 1) ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs ""

  [^Container co
   env app]

  (with-local-vars [p (transient {}) ]
    (let [cfg (get-in env [:databases :jdbc])
          pkey (.getAppKey co) ]
      (doseq [[k v] cfg]
        (when-not (false? (:status v))
          (let [[t c]
                (splitPoolSize (str (:poolsize v))) ]
            (var-set p
                     (->> (MakeDbPool
                            (MakeJdbc k v
                                      (Pwdify (:passwd v) pkey))
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
(defmethod CompInitialize :czc.skaro.ext/Container

  [^Muble co]

  (let [cl (-> (Thread/currentThread)
               (.getContextClassLoader))
        rts (CliMain/newrt cl (juid))
        appDir (.getv co K_APPDIR)
        pid (.id ^Component co)]
    (log/info "initializing container: %s" pid)
    (.setv co :cljshim rts)
    (let [cpu (MakeScheduler (str pid))
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
      (when (FileRead? res)
        (when-some [rb (LoadResource res)]
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
          (throw (ConfigError. (str "Invalid Schema Class " dmCZ))))
        (.setv co
               K_MCACHE
               (MakeMetaCache (or sc (MakeSchema [])))))

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
          (throw (ConfigError. (str "Invalid Main Class " mCZ))))

        (.setv co :main-app @obj)
        (log/info "application main-class %s%s"
                  (stror mCZ "???")
                  " created and invoked"))

      (let [svcs (:services env) ]
        (if (empty? svcs)
          (log/warn "no system service defined in env.conf")
          (-> ^czlab.skaro.impl.ext.ContainerAPI co
              (.reifyServices ))))

      ;; start the scheduler
      (.activate ^Activable cpu cfg)

      (log/info "container app class-loader: %s"
                (-> cl
                    (.getClass)
                    (.getName)))
      (log/info "initialized app: %s" (.id ^Identifiable co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

