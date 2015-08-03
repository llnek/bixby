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
    [czlab.xlib.util.str :refer [ToKW hgl? lcase nsb strim nichts?]]
    [czlab.xlib.dbio.connect :refer [DbioConnectViaPool]]
    [czlab.xlib.i18n.resources :refer [LoadResource]]
    [czlab.xlib.util.format :refer [ReadEdn]]
    [czlab.xlib.util.wfs :refer [WrapPTask NewJob SimPTask]]
    [czlab.xlib.util.files :refer [ReadOneFile WriteOneFile FileRead?]]
    [czlab.xlib.crypto.codec :refer [Pwdify CreateRandomString]]
    [czlab.xlib.util.core
    :refer [ Muble MakeMMap FPath Cast?
    trycr ConvToJava nbf ConvLong Bytesify]]
    [czlab.xlib.util.scheduler :refer [MakeScheduler]]
    [czlab.xlib.util.process :refer [Coroutine]]
    [czlab.xlib.util.core
    :refer [ NextLong LoadJavaProps SubsVar]]
    [czlab.xlib.util.meta :refer [MakeObj]]
    [czlab.xlib.dbio.core
    :refer [MakeJdbc MakeMetaCache MakeDbPool MakeSchema]]
    [czlab.xlib.net.routes :refer [LoadRoutes]])

  (:use
    [czlab.skaro.io.core :rename {enabled? io-enabled?} ]
    [czlab.xlib.util.consts]
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
    [czlab.skaro.impl.dfts
    :rename {enabled? blockmeta-enabled?} ]
    [czlab.skaro.impl.misc]
    [czlab.skaro.core.sys])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io])

  (:import
    [com.zotohlab.frwk.dbio MetaCache Schema JDBCPool DBAPI]
    [com.zotohlab.skaro.core Context Container ConfigError]
    [org.apache.commons.io FilenameUtils FileUtils]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.codec.binary Hex]
    [freemarker.template Configuration
    Template DefaultObjectWrapper]
    [java.util Locale Map Properties]
    [com.zotohlab.frwk.i18n I18N]
    [java.net URL]
    [java.io File StringWriter]
    [com.zotohlab.skaro.runtime AppMain]
    [com.zotohlab.skaro.etc PluginFactory Plugin]
    [com.zotohlab.frwk.core Versioned Hierarchial
    Morphable Activable
    Startable Disposable Identifiable]
    [com.zotohlab.frwk.server ComponentRegistry
    EventBus Service Emitter
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

  (-> ^Container (.container (.emitter evt))
      (.getAppKey)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJob ""

  ^Job
  [container wf evt]

  (with-meta
    (NewJob container wf evt)
    { :typeid (ToKW "czc.skaro.impl" "Job") }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A EventBus has the task of creating a job from an event, and delegates
;; a new Pipline which will handle the job.  The Pipeline internally will
;; call out to your application workflow  for the actual handling of the job
(defn- makeEventBus ""

  ^EventBus
  [parObj]

  (log/info "about to synthesize an event-bus...")
  (let [impl (MakeMMap) ]
    (with-meta
      (reify

        EventBus

        (onEvent [_  evt options]
          (let [^czlab.xlib.util.core.Muble
                src (.emitter ^IOEvent evt)
                ^ServiceHandler
                hr (.handler ^Service src)
                cfg (.getf src :emcfg)
                c0 (str (:handler cfg))
                c1 (str (:router options))
                wf (MakeObj (if (hgl? c1) c1 c0))
                job (mkJob parObj wf evt) ]
            (log/debug "Event type = %s" (type evt))
            (log/debug "Event options = %s" options)
            (log/debug "Event router = %s" c1)
            (log/debug "IO handler = %s" c0)
            (try
              (.setv job EV_OPTS options)
              (.handle hr wf job)
              (catch Throwable _
                (.handle hr (MakeFatalErrorFlow job) job)))))

        (parent [_] parObj))

      { :typeid (ToKW "czc.skaro.impl" "EventBus") })
  ))

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

  [^Identifiable bk container nm cfg]

  (let [pkey (.getAppKey ^Container container)
        hid (:handler cfg)
        eid (.id bk)
        ^czlab.xlib.util.core.Muble
        obj (MakeEmitter container eid nm)
        mm (meta obj) ]
    (log/info "about to synthesize an emitter: %s" eid)
    (log/info "emitter meta: %s" mm)
    (log/info "is emitter = %s" (isa? (:typeid mm)
                                      :czc.skaro.io/Emitter))
    (log/info "config params =\n%s" cfg)
    (SynthesizeComponent obj
                         {:ctx container
                          :props (assoc cfg :app.pkey pkey) })
    ;;(.setf! obj :app.pkey pkey)
    (log/info "emitter synthesized - ok. handler => %s" hid)
    obj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool ""

  ^JDBCPool
  [^czlab.xlib.util.core.Muble co ^String gid]

  (let [dbs (.getf co K_DBPS)
        dk (if (hgl? gid) gid DEF_DBID) ]
    (get dbs (keyword dk))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI ""

  [^czlab.xlib.util.core.Muble co ^String gid]

  (let [mcache (.getf co K_MCACHE)
        p (maybeGetDBPool co gid) ]
    (log/debug "acquiring from dbpool: %s" p)
    (if (nil? p)
      nil
      (DbioConnectViaPool p mcache {}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources ""

  [^czlab.xlib.util.core.Muble co]

  (let [^Schedulable sc (.getf co K_SCHEDULER)
        dbs (.getf co K_DBPS) ]
    (log/info "container releasing all system resources")
    (when (some? sc) (.dispose sc))
    (doseq [[k v] (seq dbs) ]
      (log/debug "shutting down dbpool %s" (name k))
      (.shutdown ^JDBCPool v))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAppContainer ""

  ^Container
  [^czlab.skaro.impl.dfts.PODMeta pod options]

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

        (setf! [_ a v] (.setf! impl a v) )
        (clrf! [_ a] (.clrf! impl a) )
        (getf [_ a] (.getf impl a) )
        (seq* [_])
        (clear! [_] (.clear! impl))
        (toEDN [_] (.toEDN impl))

        Container

        (getAppKeyBits [this] (Bytesify (.getAppKey this)))
        (getAppDir [this] (.getf this K_APPDIR))
        (getAppKey [_] (.appKey pod))
        (getName [_] (.moniker pod))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))

        (eventBus [this] (.getf this K_EBUS))

        (loadTemplate [_ tpath ctx]
          (let [tpl (nsb tpath)
                ts (str (if (.startsWith tpl "/") "" "/") tpl)
                out (RenderFtl ftlCfg  ts ctx)]
            [ (XData. out)
              (cond
                (.endsWith tpl ".html")
                "text/html"
                (.endsWith tpl ".json")
                "application/json"
                (.endsWith tpl ".xml")
                "application/xml"
                :else
                "text/plain") ] ))

        (isEnabled [_]
          (let [env (.getf impl K_ENVCONF)
                c (:container env) ]
            (if (false? (:enabled c))
              false
              true)))

        (getService [_ serviceId]
          (let [^ComponentRegistry
                srg (.getf impl K_SVCS) ]
            (.lookup srg (keyword serviceId))))

        (hasService [_ serviceId]
          (let [^ComponentRegistry
                srg (.getf impl K_SVCS) ]
            (.has srg (keyword serviceId))))

        (core [this]
          (.getf this K_SCHEDULER))

        (getEnvConfig [_]
          (let [env (.getf impl K_ENVCONF)]
            (ConvToJava env)))

        (getAppConfig [_]
          (let [app (.getf impl K_APPCONF)]
            (ConvToJava app)))

        Component

        (id [_] (.id ^Identifiable pod) )
        (version [_] "1.0")

        Hierarchial

        (parent [_] nil)

        Startable

        (start [this]
          (let [^czlab.skaro.core.sys.Rego
                srg (.getf impl K_SVCS)
                main (.getf impl :main-app) ]
            (log/info "container starting all services...")
            (doseq [[k v] (iter* srg) ]
              (log/info "service: %s about to start..." k)
              (.start ^Startable v))
            (log/info "container starting main app...")
            (cond
              (satisfies? CljAppMain main)    ;; clojure app
              (-> ^czlab.skaro.core.sys.CljAppMain main
                  (.start ))
              (instance? AppMain main) ;; java app
              (.start ^AppMain main)
              :else nil)))

        (stop [this]
          (let [^czlab.skaro.core.sys.Rego
                srg (.getf impl K_SVCS)
                pls (.getf this K_PLUGINS)
                main (.getf impl :main-app) ]
            (log/info "container stopping all services...")
            (doseq [[k v] (iter* srg) ]
              (.stop ^Startable v))
            (log/info "container stopping all plugins...")
            (doseq [[k v] (seq pls) ]
              (.stop ^Plugin v))
            (log/info "container stopping...")
            (cond
              (satisfies? CljAppMain main)
              (-> ^czlab.skaro.core.sys.CljAppMain main
                  (.stop ))
              (instance? AppMain main)
              (.stop ^AppMain main)
              :else nil) ))

        Disposable

        (dispose [this]
          (let [^czlab.skaro.core.sys.Rego
                srg (.getf impl K_SVCS)
                pls (.getf this K_PLUGINS)
                main (.getf impl :main-app) ]
            (log/info "container dispose(): all services")
            (doseq [[k v] (iter* srg) ]
              (.dispose ^Disposable v))
            (log/info "container dispose(): all plugins")
            (doseq [[k v] (seq pls) ]
              (.dispose ^Disposable v))
            (cond
              (satisfies? CljAppMain main)
              (.dispose ^czlab.skaro.core.sys.CljAppMain main)
              (instance? AppMain main)
              (.dispose ^AppMain main)
              :else nil)
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
          (let [env (.getf impl K_ENVCONF)
                s (:services env) ]
            (if-not (empty? s)
              (doseq [[k v] (seq s) ]
                (reifyOneService this k v)))))

        (reifyOneService [this nm cfg]
          (let [^ComponentRegistry srg (.getf impl K_SVCS)
                svc (nsb (:service cfg))
                b (:enabled cfg) ]
            (if-not (or (false? b)
                        (nichts? svc))
              (->> (reifyService this svc nm cfg)
                   (.reg srg )))))

        (reifyService [this svc nm cfg]
          (let [^czlab.xlib.util.core.Muble ctx (.getx this)
                ^ComponentRegistry
                root (.getf ctx K_COMPS)
                ^ComponentRegistry
                bks (.lookup root K_BLOCKS)
                ^ComponentRegistry
                bk (.lookup bks (keyword svc)) ]
            (when (nil? bk)
              (throw (ServiceError. (str "No such Service: " svc))))
            (makeServiceBlock bk this nm cfg))) )

    { :typeid (ToKW "czc.skaro.ext" "Container") })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn MakeContainer

  "Create an application container"

  ^Container
  [^czlab.xlib.util.core.Muble pod]

  (let [^czlab.skaro.impl.dfts.PODMeta pm pod
        ^czlab.xlib.util.core.Muble
        ctx (-> ^Context pod (.getx ))
        ^ComponentRegistry
        root (.getf ctx K_COMPS)
        apps (.lookup root K_APPS)
        ^URL url (.srcUrl pm)
        ps {K_APPDIR (File. (.toURI url))}
        c (makeAppContainer pod ps)]
    (CompCompose c apps)
    (CompContextualize c ctx)
    (CompConfigure c ps)
    (CompInitialize c)
    (.start ^Startable c)
    c
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseConfile ""

  [^File appDir ^String conf]

  (-> (ReadOneFile (io/file appDir conf))
      (SubsVar)
      (cs/replace "${appdir}" (FPath appDir))
      (ReadEdn)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.ext/Container

  [^czlab.xlib.util.core.Muble co props]

  (let [srg (MakeRegistry :EventSources K_SVCS "1.0" co)
        appDir (K_APPDIR props)
        cfgDir (io/file appDir DN_CONF)
        mf (LoadJavaProps (io/file appDir MN_FILE))
        envConf (parseConfile appDir CFG_ENV_CF)
        appConf (parseConfile appDir CFG_APP_CF) ]
    ;; make registry to store services
    (SynthesizeComponent srg {} )
    ;; store references to key attributes
    (doto co
      (.setf! K_APPDIR appDir)
      (.setf! K_SVCS srg)
      (.setf! K_ENVCONF envConf)
      (.setf! K_APPCONF appConf)
      (.setf! K_MFPROPS mf))
    (log/info "container: configured app: %s" (.id ^Identifiable co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doCljApp ""

  [ctr opts ^czlab.skaro.core.sys.CljAppMain obj]

  (.contextualize obj ctr)
  (.configure obj opts)
  (.initialize obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doJavaApp ""

  [^czlab.xlib.util.core.Muble ctr
   ^AppMain obj]

  ;; if java, pass in the conf properties as json,
  ;; not edn
  (let [cfg (.getf ctr K_APPCONF)
        m (ConvToJava cfg) ]
  (.contextualize obj ctr)
  (.configure obj m)
  (.initialize obj)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname ""

  ^File
  [^String v ^File appDir]

  (io/file appDir "modules" (.replace v "." "")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pluginInited? ""

  [^String v ^File appDir]

  (.exists (fmtPluginFname v appDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postInitPlugin ""

  [^String v ^File appDir]

  (let [pfile (fmtPluginFname v appDir) ]
    (WriteOneFile pfile "ok")
    (log/info "initialized plugin: %s" v)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin ""

  ^Plugin
  [co ^String v ^File appDir env app]

  (let [pf (MakeObj v)
        u (when (instance? PluginFactory pf)
            (.createPlugin ^PluginFactory pf
                           ^Container co)) ]
    (when-let [^Plugin p (Cast? Plugin u)]
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
      p)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitPoolSize ""

  [^String s]

  (let [pos (.indexOf s (int \:)) ]
    (if (< pos 0)
      [ 1 (ConvLong (strim s) 4) ]
      [ (ConvLong (strim (.substring s 0 pos)) 4)
        (ConvLong (strim (.substring s (+ pos 1))) 1) ])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs ""

  [^Container co
   env app]

  (with-local-vars [p (transient {}) ]
    (let [cfg (get-in env [:databases :jdbc])
          pkey (.getAppKey co) ]
      (doseq [[k v] (seq cfg) ]
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
    (persistent! @p)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkDftAppMain ""

  []

  (reify czlab.skaro.core.sys.CljAppMain
    (contextualize [_ ctr] )
    (initialize [_])
    (configure [_ cfg] )
    (start [_] )
    (stop [_])
    (dispose [_] )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.skaro.ext/Container

  [^czlab.xlib.util.core.Muble co]

  (let [pid (.id ^Component co)]
    (log/info "initializing container: %s" pid)
    (let [^Properties mf (.getf co K_MFPROPS)
          cpu (MakeScheduler (str pid))
          mCZ (strim (.get mf "Main-Class"))
          appDir (.getf co K_APPDIR)
          env (.getf co K_ENVCONF)
          app (.getf co K_APPCONF)
          dmCZ (str (:data-model app))
          reg (.getf co K_SVCS)
          bus (makeEventBus co)
          cfg (:container env) ]
      (let [cn (lcase (str (K_COUNTRY (K_LOCALE env)) ))
            lg (lcase (or (K_LANG (K_LOCALE env)) "en"))
            loc (if (hgl? cn)
                    (Locale. lg cn)
                    (Locale. lg))
            res (io/file appDir "i18n"
                         (str "Resources_"
                              (.toString loc) ".properties"))]
        (when (FileRead? res)
          (when-let [rb (LoadResource res)]
            (I18N/setBundle (.id ^Identifiable co) rb))))

      (.setf! co K_DBPS (maybeInitDBs co env app))
      (log/debug "db [dbpools]\n%s" (.getf co K_DBPS))

      ;; handle the plugins
      (.setf! co K_PLUGINS
                 (persistent! (reduce #(assoc! %1
                                               (keyword (first %2))
                                               (doOnePlugin co
                                                            (last %2)
                                                            appDir env app))
                                      (transient {})
                                      (seq (:plugins app))) ))

      (.setf! co K_SCHEDULER cpu)
      (.setf! co K_EBUS bus)

      ;; build the user data-models or create a default one.
      (log/info "application data-model schema-class: %s" dmCZ)
      (.setf! co
              K_MCACHE
              (MakeMetaCache
                (if (hgl? dmCZ)
                  (let [sc (MakeObj dmCZ) ]
                    (when-not (instance? Schema sc)
                      (throw (ConfigError. (str "Invalid Schema Class " dmCZ))))
                    sc)
                  (MakeSchema [])) ))

      (when (nichts? mCZ) (log/warn "============> NO MAIN-CLASS DEFINED"))
      ;;(test-nestr "Main-Class" mCZ)

      (with-local-vars
        [obj (trycr nil (when (hgl? mCZ) (MakeObj mCZ)))]
        (when (nil? @obj)
          (log/warn "failed to create main class: %s" mCZ)
          (var-set obj (mkDftAppMain)))
        (cond
          (satisfies? CljAppMain @obj)
          (doCljApp co app @obj)
          (instance? AppMain @obj)
          (doJavaApp co @obj)
          :else (throw (ConfigError. (str "Invalid Main Class " mCZ))))

        (.setf! co :main-app @obj)
        (log/info "application main-class %s%s"
                  (if (hgl? mCZ) mCZ "???")
                  " created and invoked"))

      (let [sf (io/file appDir DN_CONF "static-routes.conf")
            rf (io/file appDir DN_CONF "routes.conf") ]
        (.setf! co :routes
                   (vec (concat (if (.exists sf) (LoadRoutes sf) [] )
                                (if (.exists rf) (LoadRoutes rf) [] ))) ))

      (let [svcs (:services env) ]
        (if (empty? svcs)
          (log/warn "no system service defined in env.conf")
          (.reifyServices ^czlab.skaro.impl.ext.ContainerAPI co)))

      ;; start the scheduler
      (.activate ^Activable cpu cfg)

      (log/info "initialized app: %s" (.id ^Identifiable co))
      (log/info "container app class-loader: %s"
                (-> (Thread/currentThread)
                    (.getContextClassLoader)
                    (.getClass)
                    (.getName))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

