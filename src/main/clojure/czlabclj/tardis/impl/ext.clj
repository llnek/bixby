;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.impl.ext

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.str
         :only [ToKW hgl? lcase nsb strim nichts?]]
        [czlabclj.xlib.util.consts]
        [czlabclj.tardis.io.core :rename {enabled? io-enabled?} ]
        [czlabclj.xlib.dbio.connect :only [DbioConnectViaPool]]
        [czlabclj.xlib.i18n.resources :only [LoadResource]]
        [czlabclj.xlib.util.format :only [ReadEdn]]
        [czlabclj.xlib.util.wfs :only [WrapPTask MakeJob SimPTask]]
        [czlabclj.xlib.util.files
         :only
         [ReadOneFile WriteOneFile FileRead?]]
        [czlabclj.xlib.crypto.codec
         :only
         [Pwdify CreateRandomString]]
        [czlabclj.tardis.core.consts]
        [czlabclj.tardis.io.loops]
        [czlabclj.tardis.io.mails]
        [czlabclj.tardis.io.files]
        [czlabclj.tardis.io.jms]
        [czlabclj.tardis.io.http]
        [czlabclj.tardis.io.netty]
        [czlabclj.tardis.io.jetty]
        [czlabclj.tardis.io.socket]
        [czlabclj.tardis.mvc.filters]

        [czlabclj.tardis.impl.dfts
         :rename
         {enabled? blockmeta-enabled?} ]

        [czlabclj.tardis.impl.misc]
        [czlabclj.tardis.core.sys]

        [czlabclj.xlib.util.core
         :only
         [Muble MakeMMap NiceFPath TryCR
          ConvToJava nbf ConvLong Bytesify]]

        [czlabclj.xlib.util.scheduler :only [MakeScheduler]]
        [czlabclj.xlib.util.process :only [Coroutine]]
        [czlabclj.xlib.util.core
         :only
         [NextLong LoadJavaProps SubsVar]]
        [czlabclj.xlib.util.meta :only [MakeObj]]

        [czlabclj.xlib.dbio.core
         :only
         [MakeJdbc MakeMetaCache
          MakeDbPool MakeSchema]]

        [czlabclj.xlib.net.routes :only [LoadRoutes]])

  (:import  [com.zotohlab.frwk.dbio MetaCache Schema JDBCPool DBAPI]
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
            [com.zotohlab.skaro.core Container ConfigError]
            [com.zotohlab.skaro.io IOEvent]
            [com.zotohlab.frwk.util Schedulable CoreUtils]
            [com.zotohlab.frwk.io XData]
            [com.zotohlab.wflow Activity Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetAppKeyFromEvent ""

  ^String
  [^IOEvent evt]

  (-> ^Container (.container (.emitter evt))
      (.getAppKey)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJob ""

  ^Job
  [container evt]

  (with-meta
    (MakeJob container evt)
    { :typeid (ToKW "czc.tardis.impl" "Job") }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A EventBus has the task of creating a job from an event, and delegates
;; a new Pipline which will handle the job.  The Pipeline internally will
;; call out to your application workflow  for the actual handling of the job.
;;
(defn- makeEventBus ""

  ^EventBus
  [parObj]

  (let [^String id (if (instance? Identifiable parObj)
             (.id ^Identifiable parObj)
             "Container")
        impl (MakeMMap) ]
    (log/info "About to synthesize an event-bus...")
    (with-meta
      (reify

        EventBus

        (onEvent [_  evt options]
          (let [^czlabclj.tardis.core.sys.Elmt
                src (.emitter ^IOEvent evt)
                ^ServiceHandler
                hr (.handler ^Service src)
                cfg (.getAttr src :emcfg)
                c0 (nsb (:handler cfg))
                c1 (nsb (:router options))
                job (mkJob parObj evt) ]
            (log/debug "Event type = " (type evt))
            (log/debug "Event options = " options)
            (log/debug "Event router = " c1)
            (log/debug "IO handler = " c0)
            (try
              (let [z (MakeObj (if (hgl? c1) c1 c0))]
                (.setv job EV_OPTS options)
                (.handle hr z job))
              (catch Throwable _
                (.handle hr (MakeFatalErrorFlow job) job)))))

        (parent [_] parObj))

      { :typeid (ToKW "czc.tardis.impl" "EventBus") }
  )))

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
;; emitter.
;;
(defn- makeServiceBlock

  [^Identifiable bk container nm cfg]

  (let [pkey (.getAppKey ^Container container)
        hid (:handler cfg)
        eid (.id bk)
        ^czlabclj.tardis.core.sys.Elmt
        obj (MakeEmitter container eid nm)
        mm (meta obj) ]
    (log/info "About to synthesize an emitter: " eid)
    (log/info "Emitter meta: " mm)
    (log/info "Is emitter = " (isa? (:typeid mm)
                                    :czc.tardis.io/Emitter))
    (log/info "Config params =\n" cfg)
    (SynthesizeComponent obj
                         {:ctx container
                          :props (assoc cfg :app.pkey pkey) })
    ;;(.setAttr! obj :app.pkey pkey)
    (log/info "Emitter synthesized - OK. handler => " hid)
    obj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool ""

  ^JDBCPool
  [^czlabclj.tardis.core.sys.Elmt co ^String gid]

  (let [dbs (.getAttr co K_DBPS)
        dk (if (hgl? gid) gid DEF_DBID) ]
    (get dbs (keyword dk))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI ""

  [^czlabclj.tardis.core.sys.Elmt co ^String gid]

  (let [mcache (.getAttr co K_MCACHE)
        p (maybeGetDBPool co gid) ]
    (log/debug "Acquiring from dbpool " p)
    (if (nil? p)
      nil
      (DbioConnectViaPool p mcache {}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources ""

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^Schedulable sc (.getAttr co K_SCHEDULER)
        dbs (.getAttr co K_DBPS) ]
    (log/info "Container releasing all system resources.")
    (when-not (nil? sc) (.dispose sc))
    (doseq [[k v] (seq dbs) ]
      (log/debug "Shutting down dbpool " (name k))
      (.shutdown ^JDBCPool v))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeAppContainer ""

  ^Container
  [^czlabclj.tardis.impl.dfts.PODMeta pod]

  (log/info "Creating an app-container: " (.id ^Identifiable pod))
  (let [ftlCfg (Configuration.)
        impl (MakeMMap) ]
    (with-meta
      (reify

        Elmt

        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (setCtx! [_ x] (.setf! impl :ctx x) )
        (getCtx [_] (.getf impl :ctx) )
        (toEDN [_] (.toEDN impl))

        Container

        (getAppKeyBits [this] (Bytesify (.getAppKey this)))
        (getAppDir [this] (.getAttr this K_APPDIR))
        (getAppKey [_] (.appKey pod))
        (getName [_] (.moniker pod))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))

        (eventBus [this] (.getAttr this K_EBUS))

        (loadTemplate [_ tpath ctx]
          (let [tpl (nsb tpath)
                ts (str (if (.startsWith tpl "/") "" "/") tpl)
                ^Template tp (.getTemplate ftlCfg ts)
                out (StringWriter.) ]
            (when-not (nil? tp) (.process tp ctx out))
            (.flush out)
            [ (XData. (.toString out))
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
          (.getAttr this K_SCHEDULER))

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
          (let [pub (File. (.getAppDir this)
                           (str DN_PUBLIC "/" DN_PAGES))
                ^czlabclj.tardis.core.sys.Rego
                srg (.getf impl K_SVCS)
                main (.getf impl :main-app) ]
            (log/info "Container starting all services...")
            (when (.exists pub)
              (doto ftlCfg
                (.setDirectoryForTemplateLoading pub)
                (.setObjectWrapper (DefaultObjectWrapper.))))
            (doseq [[k v] (seq* srg) ]
              (log/info "Service: " k " about to start...")
              (.start ^Startable v))
            (log/info "Container starting main app...")
            (cond
              (satisfies? CljAppMain main)    ;; clojure app
              (.start ^czlabclj.tardis.core.sys.CljAppMain main)
              (instance? AppMain main) ;; java app
              (.start ^AppMain main)
              :else nil)))

        (stop [this]
          (let [^czlabclj.tardis.core.sys.Rego
                srg (.getf impl K_SVCS)
                pls (.getAttr this K_PLUGINS)
                main (.getf impl :main-app) ]
            (log/info "Container stopping all services...")
            (doseq [[k v] (seq* srg) ]
              (.stop ^Startable v))
            (log/info "Container stopping all plugins...")
            (doseq [[k v] (seq pls) ]
              (.stop ^Plugin v))
            (log/info "Container stopping...")
            (cond
              (satisfies? CljAppMain main)
              (.stop ^czlabclj.tardis.core.sys.CljAppMain main)
              (instance? AppMain main)
              (.stop ^AppMain main)
              :else nil) ))

        Disposable

        (dispose [this]
          (let [^czlabclj.tardis.core.sys.Rego
                srg (.getf impl K_SVCS)
                pls (.getAttr this K_PLUGINS)
                main (.getf impl :main-app) ]
            (log/info "Container dispose(): all services.")
            (doseq [[k v] (seq* srg) ]
              (.dispose ^Disposable v))
            (log/info "Container dispose(): all plugins.")
            (doseq [[k v] (seq pls) ]
              (.dispose ^Disposable v))
            (cond
              (satisfies? CljAppMain main)
              (.dispose ^czlabclj.tardis.core.sys.CljAppMain main)
              (instance? AppMain main)
              (.dispose ^AppMain main)
              :else nil)
            (log/info "Container dispose() - main app disposed.")
            (releaseSysResources this) ))

        ContainerAPI

        (generateNonce [_] (Hex/encodeHexString (Bytesify (CreateRandomString 18))))
        (generateCsrf [_] (Hex/encodeHexString (Bytesify (CreateRandomString 18))))

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
            (if-not (or (false? b) (nichts? svc))
              (let [s (reifyService this svc nm cfg) ]
                (.reg srg s)))))

        (reifyService [this svc nm cfg]
          (let [^czlabclj.xlib.util.core.Muble ctx (.getCtx this)
                ^ComponentRegistry root (.getf ctx K_COMPS)
                ^ComponentRegistry bks (.lookup root K_BLOCKS)
                ^ComponentRegistry bk (.lookup bks (keyword svc)) ]
            (when (nil? bk)
              (throw (ServiceError. (str "No such Service: " svc "."))))
            (makeServiceBlock bk this nm cfg))) )

    { :typeid (ToKW "czc.tardis.ext" "Container") }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  The runtime container for your application.
;;
(defn MakeContainer ""

  [^czlabclj.tardis.core.sys.Elmt pod]

  (let [^czlabclj.tardis.impl.dfts.PODMeta pm pod
        ^URL url (.srcUrl pm)
        ^czlabclj.xlib.util.core.Muble
        ctx (.getCtx pod)
        cl (.getf ctx K_APP_CZLR)
        ^ComponentRegistry root (.getf ctx K_COMPS)
        apps (.lookup root K_APPS)
        ps {K_APPDIR (File. (.toURI url))
            K_APP_CZLR cl }
        c (makeAppContainer pod)]
    (CompCompose c apps)
    (CompContextualize c ctx)
    (CompConfigure c ps)
    (if (.isEnabled c)
      (do
        (Coroutine #(do
                      (CompInitialize c)
                      (.start ^Startable c))
                   {:classLoader cl
                    :name (.getName c)})
        c)
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseConfile ""

  [^File appDir ^String conf]

  (-> (ReadOneFile (File. appDir conf))
      (SubsVar)
      (.replace "${appdir}" (NiceFPath appDir))
      (ReadEdn)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.ext/Container

  [^czlabclj.tardis.core.sys.Elmt co props]

  (let [srg (MakeRegistry :EventSources K_SVCS "1.0" co)
        ^File appDir (K_APPDIR props)
        cfgDir (File. appDir DN_CONF)
        mf (LoadJavaProps (File. appDir MN_FILE))
        envConf (parseConfile appDir CFG_ENV_CF)
        appConf (parseConfile appDir CFG_APP_CF) ]
    ;; make registry to store services
    (SynthesizeComponent srg {} )
    ;; store references to key attributes
    (doto co
      (.setAttr! K_APPDIR appDir)
      (.setAttr! K_SVCS srg)
      (.setAttr! K_ENVCONF envConf)
      (.setAttr! K_APPCONF appConf)
      (.setAttr! K_MFPROPS mf))
    (log/info "Container: configured app: " (.id ^Identifiable co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doCljApp ""

  [ctr opts ^czlabclj.tardis.core.sys.CljAppMain obj]

  (.contextualize obj ctr)
  (.configure obj opts)
  (.initialize obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doJavaApp ""

  [^czlabclj.tardis.core.sys.Elmt ctr
   ^AppMain obj]

  ;; if java, pass in the conf properties as json,
  ;; not edn.
  (let [cfg (.getAttr ctr K_APPCONF)
        m (ConvToJava cfg) ]
  (.contextualize obj ctr)
  (.configure obj m)
  (.initialize obj)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname ""

  ^File
  [^String v ^File appDir]

  (File. appDir (str "modules/"
                     (.replace v "." ""))))


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
    (log/info "Initialized plugin: " v)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin ""

  ^Plugin
  [co ^String v ^File appDir env app]

  (let [pf (MakeObj v)
        ^Plugin p (if (instance? PluginFactory pf)
                    (.createPlugin ^PluginFactory pf
                                   ^Container co)
                    nil) ]
    (when (instance? Plugin p)
      (log/info "Calling plugin-factory: " v)
      (.configure p { :env env :app app })
      (if (pluginInited? v appDir)
        (log/info "Plugin " v " already initialized.")
        (do
          (.initialize p)
          (postInitPlugin v appDir)))
      (log/info "Plugin " v " starting...")
      (.start p)
      (log/info "Plugin " v " started.")
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

  ;;[^czlabclj.tardis.core.sys.Elmt co
  [^Container co
   env app]

  (with-local-vars [p (transient {}) ]
    (let [cfg (->> env (:databases)(:jdbc))
          pkey (.getAppKey co) ]
      (when-not (nil? cfg)
        (doseq [[k v] (seq cfg) ]
          (when-not (false? (:status v))
            (let [[t c] (splitPoolSize (nsb (:poolsize v))) ]
              (var-set p
                       (assoc! @p k
                               (MakeDbPool (MakeJdbc k v (Pwdify (:passwd v) pkey))
                                           {:max-conns c
                                            :min-conns 1
                                            :partitions t
                                            :debug (nbf (:debug v)) }))))))
    ))
    (persistent! @p)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkDftAppMain ""

  []

  (reify czlabclj.tardis.core.sys.CljAppMain
    (contextualize [_ ctr] )
    (initialize [_])
    (configure [_ cfg] )
    (start [_] )
    (stop [_])
    (dispose [_] )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.ext/Container

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [pid (.id ^Component co)]
    (log/info "Initializing container: " pid)
    (let [^Properties mf (.getAttr co K_MFPROPS)
          cpu (MakeScheduler (nsb pid))
          mCZ (strim (.get mf "Main-Class"))
          ^File appDir (.getAttr co K_APPDIR)
          env (.getAttr co K_ENVCONF)
          app (.getAttr co K_APPCONF)
          dmCZ (nsb (:data-model app))
          reg (.getAttr co K_SVCS)
          bus (makeEventBus co)
          cfg (:container env) ]
      (let [cn (lcase (or (K_COUNTRY (K_LOCALE env)) ""))
            lg (lcase (or (K_LANG (K_LOCALE env)) "en"))
            loc (if (hgl? cn)
                    (Locale. lg cn)
                    (Locale. lg))
            res (File. appDir (str "i18n/Resources_"
                                   (.toString loc) ".properties"))]
        (when (FileRead? res)
          (when-let [rb (LoadResource res)]
            (I18N/setBundle (.id ^Identifiable co) rb))))

      (.setAttr! co K_DBPS (maybeInitDBs co env app))
      (log/debug "DB [dbpools]\n" (.getAttr co K_DBPS))

      ;; handle the plugins
      (.setAttr! co K_PLUGINS
                 (persistent! (reduce #(assoc! %1
                                               (keyword (first %2))
                                               (doOnePlugin co
                                                            (last %2)
                                                            appDir env app))
                                      (transient {})
                                      (seq (:plugins app))) ))

      (.setAttr! co K_SCHEDULER cpu)
      (.setAttr! co K_EBUS bus)

      ;; build the user data-models or create a default one.
      (log/info "Application data-model schema-class: " dmCZ)
      (.setAttr! co
                 K_MCACHE
                 (MakeMetaCache (if (hgl? dmCZ)
                                  (let [sc (MakeObj dmCZ) ]
                                    (when-not (instance? Schema sc)
                                      (throw (ConfigError. (str "Invalid Schema Class "
                                                                dmCZ))))
                                    sc)
                                  (MakeSchema [])) ))

      (when (nichts? mCZ) (log/warn "============> NO MAIN-CLASS DEFINED."))
      ;;(test-nestr "Main-Class" mCZ)

      (with-local-vars [obj (TryCR nil (when (hgl? mCZ) (MakeObj mCZ)))]
        (when (nil? @obj)
          (log/warn "Failed to create main class: " mCZ)
          (var-set obj (mkDftAppMain)))
        (cond
          (satisfies? CljAppMain @obj)
          (doCljApp co app @obj)
          (instance? AppMain @obj)
          (doJavaApp co @obj)
          :else (throw (ConfigError. (str "Invalid Main Class " mCZ))))

        (.setAttr! co :main-app @obj)
        (log/info "Application main-class "
                  (if (hgl? mCZ) mCZ "???") " created and invoked"))

      (let [sf (File. appDir (str DN_CONF "/static-routes.conf"))
            rf (File. appDir (str DN_CONF "/routes.conf")) ]
        (.setAttr! co :routes
                   (vec (concat (if (.exists sf) (LoadRoutes sf) [] )
                                (if (.exists rf) (LoadRoutes rf) [] ))) ))

      (let [svcs (:services env) ]
        (if (empty? svcs)
          (log/warn "No system service defined in env.conf.")
          (.reifyServices ^czlabclj.tardis.impl.ext.ContainerAPI co)))

      ;; start the scheduler
      (.activate ^Activable cpu cfg)

      (log/info "Initialized app: " (.id ^Identifiable co))
      (log/info "Container app class-loader: "
                (-> (Thread/currentThread)
                    (.getContextClassLoader)
                    (.getClass)
                    (.getName)))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ext-eof nil)

