;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.impl.ext

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.data.json :as json])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.io.core :rename {enabled? io-enabled?} ])
  (:use [comzotohlabscljc.tardis.io.loops])
  (:use [comzotohlabscljc.tardis.io.mails])
  (:use [comzotohlabscljc.tardis.io.files])
  (:use [comzotohlabscljc.tardis.io.jms])
  (:use [comzotohlabscljc.tardis.io.http])
  (:use [comzotohlabscljc.tardis.io.netty])
  (:use [comzotohlabscljc.tardis.io.socket])
  (:use [comzotohlabscljc.tardis.mvc.handler])
  ;;(:use [comzotohlabscljc.tardis.io.events])
  (:use [comzotohlabscljc.tardis.impl.defaults
         :rename {enabled? blockmeta-enabled?
                  start kernel-start
                  stop kernel-stop } ])
  (:use [comzotohlabscljc.tardis.etc.misc])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.util.core :only [MubleAPI MakeMMap] ])
  (:use [ comzotohlabscljc.util.scheduler :only [MakeScheduler] ])
  (:use [ comzotohlabscljc.util.process :only [Coroutine] ])
  (:use [ comzotohlabscljc.util.core :only [LoadJavaProps] ])
  (:use [ comzotohlabscljc.util.seqnum :only [NextLong] ])
  (:use [ comzotohlabscljc.util.str :only [hgl? nsb strim nichts?] ])
  (:use [ comzotohlabscljc.util.meta :only [MakeObj] ])
  (:use [ comzotohlabscljc.crypto.codec :only [Pwdify] ])
  (:use [ comzotohlabscljc.dbio.connect :only [DbioConnect] ])
  (:use [ comzotohlabscljc.dbio.core
         :only [MakeJdbc MakeMetaCache MakeDbPool MakeSchema] ])
  (:use [ comzotohlabscljc.net.routes :only [LoadRoutes] ])
  (:import (org.apache.commons.io FilenameUtils FileUtils))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (freemarker.template Configuration Template DefaultObjectWrapper))
  (:import (java.util Map Properties))
  (:import (java.net URL))
  (:import (java.io File StringWriter))
  (:import (com.zotohlabs.gallifrey.runtime AppMain))
  (:import (com.zotohlabs.gallifrey.etc PluginFactory Plugin))
  (:import (com.zotohlabs.frwk.dbio MetaCache Schema DBIOLocal DBAPI))
  (:import (com.zotohlabs.frwk.core Versioned Hierarchial
                                    Startable Disposable Identifiable ))
  (:import (com.zotohlabs.frwk.server ComponentRegistry Component ServiceError ))
  (:import (com.zotohlabs.gallifrey.core Container ConfigError ))
  (:import (com.zotohlabs.gallifrey.io IOEvent))
  (:import (com.zotohlabs.frwk.util Schedulable CoreUtils))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.wflow.core Job))
  (:import (com.zotohlabs.wflow Pipeline)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; An application must implement this protocol.
;;
(defprotocol CljAppMain

  ""

  (contextualize [_ ctr] )
  (configure [_ options] )
  (initialize [_] )
  (start [_] )
  (stop [_])
  (dispose [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-job ""

  ^Job
  [_container evt]

  (let [ impl (MakeMMap)
         jid (NextLong) ]
    (with-meta
      (reify

        MubleAPI

        (setf! [_ k v] (.setf! impl k v))
        (clear! [_] (.clear! impl))
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k))
        (clrf! [_ k] (.clrf! impl k))

        Job

        (container [_] _container)
        (setv [this k v] (.setf! this k v))
        (unsetv [this k] (.clrf! this k))
        (getv [this k] (.getf this k))
        (setLastResult [this v] (.setf! this JS_LAST v))
        (getLastResult [this] (.getf this JS_LAST))
        (clrLastResult [this] (.clrf! this JS_LAST))
        (event [_] evt)
        (id [_] jid))

      { :typeid (keyword "czc.tardis.impl/Job") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private JobCreator

  ""

  (update [_ event options] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A Job creator has the task of creating a job from an event, and delegates
;; a new Pipline which will handle the job.  The Pipeline internally will
;; call out to your application workflow  for the actual handling of the job.
;;
(defn- make-jobcreator ""

  ^comzotohlabscljc.tardis.impl.ext.JobCreator
  [parObj]

  (let [ impl (MakeMMap) ]
    (log/info "about to synthesize a job-creator...")
    (with-meta
      (reify

        JobCreator

        (update [_  evt options]
          (let [ ^comzotohlabscljc.tardis.core.sys.Element
                 src (.emitter ^IOEvent evt)
                 c0 (.getAttr src :router)
                 c1 (:router options)
                 job (make-job parObj evt) ]
            (log/debug "event type = " (type evt))
            (log/debug "event options = " options)
            (log/debug "event router = " c1)
            (log/debug "io router = " c0)
            (try
              (let [ p (Pipeline. job (if (hgl? c1) c1 c0)) ]
                (.setv job EV_OPTS options)
                (.start p))
              (catch Throwable e#
                (-> (MakeFatalErrorFlow job) (.start)))))))

      { :typeid (keyword "czc.tardis.impl/JobCreator") }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ContainerAPI
(defprotocol ^:private ContainerAPI

  ""

  (reifyOneService [_ sid cfg] )
  (reifyService [_ svc sid cfg] )
  (reifyServices [_] )
  (loadTemplate [_ tpl ctx] )
  (enabled? [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A Service is an instance of a Block, that is, an instance of an event
;; emitter.
;;
(defn- make-service-block

  [^Identifiable bk container nm cfg]

  (let [ pkey (.getAppKey ^Container container)
         hid (:handler cfg)
         eid (.id bk)
         ^comzotohlabscljc.tardis.core.sys.Element
         obj (MakeEmitter container eid nm)
         mm (meta obj) ]
    (log/info "about to synthesize an emitter: " eid)
    (log/info "emitter meta: " mm)
    (log/info "is emitter = " (isa?  (:typeid mm) :czc.tardis.io/Emitter))
    (log/info "config params = " cfg)
    (SynthesizeComponent
      obj
      { :ctx container :props (assoc cfg :hhh.pkey pkey) })
    (.setAttr! obj :router hid)
    (log/info "emitter synthesized - OK. handler => " hid)
    obj
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getDBAPI?

  ^DBAPI
  [^String mkey cfg ^String pkey mcache]

  (let [ ^Map c (.get (DBIOLocal/getCache))
         jdbc (MakeJdbc mkey
                        cfg
                        (Pwdify (:passwd cfg) pkey)) ]
    (when-not (.containsKey c mkey)
      (let [ p (MakeDbPool jdbc {} ) ]
        (.put c mkey p)))
    (DbioConnect jdbc mcache {})
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI ""

  [^comzotohlabscljc.tardis.core.sys.Element co ^String gid]

  (let [ pkey (.getAppKey ^Container co)
         mcache (.getAttr co K_MCACHE)
         env (.getAttr co K_ENVCONF)
         cfg (:jdbc (:databases env))
         dk (if (hgl? gid) gid "_")
         jj (get cfg (keyword dk)) ]
    (if (nil? jj)
      nil
      (getDBAPI? dk jj pkey mcache))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources ""

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^Schedulable sc (.getAttr co K_SCHEDULER)
         jc (.getAttr co K_JCTOR) ]
    (log/info "container releasing all system resources.")
    (when-not (nil? sc)
      (.dispose sc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-app-container ""

  [pod]

  (let [ ftlCfg (Configuration.)
         impl (MakeMMap) ]
    (log/info "about to create an app-container...")
    (with-meta
      (reify

        Element

        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (setCtx! [_ x] (.setf! impl :ctx x) )
        (getCtx [_] (.getf impl :ctx) )

        Container

        (notifyObservers [this evt options]
          (let [ ^comzotohlabscljc.tardis.impl.ext.JobCreator
                 jc (.getAttr this K_JCTOR) ]
            (.update jc evt options)))

        (getAppKey [_] (.appKey ^comzotohlabscljc.tardis.impl.defaults.PODMeta pod))
        (getAppDir [this] (.getAttr this K_APPDIR))
        (acquireJdbc [this gid] (maybeGetDBAPI this gid))

        (hasService [_ serviceId]
          (let [ ^ComponentRegistry srg (.getf impl K_SVCS) ]
            (.has srg (keyword serviceId))))

        (core [this]
          (.getAttr this K_SCHEDULER))

        (getService [_ serviceId]
          (let [ ^ComponentRegistry srg (.getf impl K_SVCS) ]
            (.lookup srg (keyword serviceId))))

        Component

        (id [_] (.id ^Identifiable pod) )
        (version [_] "1.0")

        Hierarchial

        (parent [_] nil)

        Startable

        (start [this]
          (let [ pub (File. (.getAppDir this) (str DN_PUBLIC "/" DN_PAGES))
                 ^comzotohlabscljc.tardis.core.sys.Registry
                 srg (.getf impl K_SVCS)
                 main (.getf impl :main-app) ]
            (log/info "container starting all services...")
            (when (.exists pub)
              (doto ftlCfg
                    (.setDirectoryForTemplateLoading pub)
                    (.setObjectWrapper (DefaultObjectWrapper.))))
            (doseq [ [k v] (seq* srg) ]
              (log/info "service: " k " about to start...")
              (.start ^Startable v))
            (log/info "container starting main app...")
            (cond
              (satisfies? CljAppMain main)    ;; clojure app
              (.start ^comzotohlabscljc.tardis.impl.ext.CljAppMain main)
              (instance? AppMain main) ;; java app
              (.start ^AppMain main)
              :else nil)))

        (stop [this]
          (let [ ^comzotohlabscljc.tardis.core.sys.Registry
                 srg (.getf impl K_SVCS)
                 pls (.getAttr this K_PLUGINS)
                 main (.getf impl :main-app) ]
            (log/info "container stopping all services...")
            (doseq [ [k v] (seq* srg) ]
              (.stop ^Startable v))
            (log/info "container stopping all plugins...")
            (doseq [ p (seq pls) ]
              (.stop ^Startable p))
            (log/info "container stopping...")
            (cond
              (satisfies? CljAppMain main)
              (.stop ^comzotohlabscljc.tardis.impl.ext.CljAppMain main)
              (instance? AppMain main)
              (.stop ^AppMain main)
              :else nil) ))

        Disposable

        (dispose [this]
          (let [ ^comzotohlabscljc.tardis.core.sys.Registry
                 srg (.getf impl K_SVCS)
                 pls (.getAttr this K_PLUGINS)
                 main (.getf impl :main-app) ]
            (doseq [ [k v] (seq* srg) ]
              (.dispose ^Disposable v))
            (doseq [ p (seq pls) ]
              (.dispose ^Disposable p))
            (log/info "container dispose() - main app getting disposed.")
            (cond
              (satisfies? CljAppMain main)
              (.dispose ^comzotohlabscljc.tardis.impl.ext.CljAppMain main)
              (instance? AppMain main)
              (.dispose ^AppMain main)
              :else nil)
            (releaseSysResources this) ))

        ContainerAPI

        (loadTemplate [_ tpath ctx]
          (let [ tpl (nsb tpath)
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

        (enabled? [_]
          (let [ env (.getf impl K_ENVCONF)
                 c (:container env) ]
            (if (false? (:enabled c))
              false
              true)))

        (reifyServices [this]
          (let [ env (.getf impl K_ENVCONF)
                 s (:services env) ]
            (if-not (empty? s)
                (doseq [ [k v] (seq s) ]
                  (reifyOneService this k v)))))

        (reifyOneService [this nm cfg]
          (let [ ^ComponentRegistry srg (.getf impl K_SVCS)
                 svc (nsb (:service cfg))
                 b (:enabled cfg) ]
            (if-not (or (false? b) (nichts? svc))
              (let [ s (reifyService this svc nm cfg) ]
                (.reg srg s)))))

        (reifyService [this svc nm cfg]
          (let [^comzotohlabscljc.util.core.MubleAPI ctx (.getCtx this)
                 ^ComponentRegistry root (.getf ctx K_COMPS)
                 ^ComponentRegistry bks (.lookup root K_BLOCKS)
                 ^ComponentRegistry bk (.lookup bks (keyword svc)) ]
            (when (nil? bk)
              (throw (ServiceError. (str "No such Service: " svc "."))))
            (make-service-block bk this nm cfg))) )

    { :typeid (keyword "czc.tardis.ext/Container") }

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  The runtime container for your application.
;;
(defn MakeContainer ""

  [^comzotohlabscljc.tardis.core.sys.Element pod]

  (let [ c (make-app-container pod)
         ^comzotohlabscljc.util.core.MubleAPI
         ctx (.getCtx pod)
         cl (.getf ctx K_APP_CZLR)
         ^ComponentRegistry root (.getf ctx K_COMPS)
         apps (.lookup root K_APPS)
         ^URL url (.srcUrl ^comzotohlabscljc.tardis.impl.defaults.PODMeta pod)
         ps { K_APPDIR (File. (.toURI  url)) K_APP_CZLR cl } ]
    (CompCompose c apps)
    (CompContextualize c ctx)
    (CompConfigure c ps)
    (if (.enabled? ^comzotohlabscljc.tardis.impl.ext.ContainerAPI c)
      (do (Coroutine (fn []
                         (CompInitialize c)
                         (.start ^Startable c))
                     cl)
          c)
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.ext/Container

  [^comzotohlabscljc.tardis.core.sys.Element co props]

  (let [ srg (MakeComponentRegistry :EventSources K_SVCS "1.0" co)
         ^File appDir (K_APPDIR props)
         cfgDir (File. appDir ^String DN_CONF)
         mf (LoadJavaProps (File. appDir ^String MN_FILE))
         envConf (json/read-str (FileUtils/readFileToString
                                (File. cfgDir "env.conf"))
                              :key-fn keyword)
         appConf (json/read-str (FileUtils/readFileToString
                                (File. cfgDir "app.conf"))
                              :key-fn keyword) ]
    ;;WebPage.setup(new File(appDir))
    ;;maybeLoadRoutes(cfgDir)
    ;;_ftlCfg = new FTLCfg()
    ;;_ftlCfg.setDirectoryForTemplateLoading( new File(_appDir, DN_PAGES+"/"+DN_TEMPLATES))
    ;;_ftlCfg.setObjectWrapper(new DefaultObjectWrapper())
    (SynthesizeComponent srg {} )
    (doto co
          (.setAttr! K_ENVCONF_FP (File. cfgDir "env.conf"))
          (.setAttr! K_APPCONF_FP (File. cfgDir "app.conf"))
          (.setAttr! K_APPDIR appDir)
          (.setAttr! K_SVCS srg)
          (.setAttr! K_ENVCONF envConf)
          (.setAttr! K_APPCONF appConf)
          (.setAttr! K_MFPROPS mf))
    (log/info "container: configured app: " (.id ^Identifiable co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doCljApp ""

  [ctr opts ^comzotohlabscljc.tardis.impl.ext.CljAppMain obj]

  (.contextualize obj ctr)
  (.configure obj opts)
  (.initialize obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doJavaApp ""

  [^comzotohlabscljc.tardis.core.sys.Element ctr ^AppMain obj]

  (let [ ^File cfg (.getAttr ctr K_APPCONF_FP)
         json (CoreUtils/readJson cfg) ]
  (.contextualize obj ctr)
  (.configure obj json)
  (.initialize obj)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname ""

  ^File
  [^String v ^File appDir]

  (let [ fnn (StringUtils/replace v "." "")
         m (File. appDir (str "modules/" fnn)) ]
    m
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- plugin-inited? ""

  [^String v ^File appDir]

  (let [ pfile (fmtPluginFname v appDir) ]
    (.exists pfile)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- post-init-plugin ""

  [^String v ^File appDir]

  (let [ pfile (fmtPluginFname v appDir) ]
    (FileUtils/writeStringToFile pfile "ok" "utf-8")
    (log/info "initialized plugin: " v)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin ""

  ^Plugin
  [co ^String v ^File appDir env app]

  (let [ pf (MakeObj v)
         ^Plugin p (if (instance? PluginFactory pf)
                       (.createPlugin ^PluginFactory pf)
                       nil) ]
    (when (instance? Plugin p)
      (log/info "calling plugin-factory: " v)
      (.contextualize p co)
      (.configure p { :env env :app app })
      (if (plugin-inited? v appDir)
        (log/info "plugin " v " already initialized.")
        (do
          (.initialize p)
          (post-init-plugin v appDir)))
      (.start p))
    p
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.ext/Container

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^File appDir (.getAttr co K_APPDIR)
         env (.getAttr co K_ENVCONF)
         app (.getAttr co K_APPCONF)
         ^String dmCZ (nsb (:data-model app))
         ^Properties mf (.getAttr co K_MFPROPS)
         mCZ (strim (.get mf "Main-Class"))
         reg (.getAttr co K_SVCS)
         jc (make-jobcreator co)
         ^comzotohlabscljc.util.scheduler.SchedulerAPI
         sc (MakeScheduler co)
         cfg (:container env) ]

    ;; handle the plugins
    (.setAttr! co K_PLUGINS
      (persistent! (reduce (fn [sum en]
                               (assoc! sum (keyword (first en))
                                           (doOnePlugin co (last en) appDir env app)) )
                           (transient {})
                           (seq (:plugins app))) ))

    (.setAttr! co K_SCHEDULER sc)
    (.setAttr! co K_JCTOR jc)

    ;; build the user data-models or create a default one.
    (log/info "application data-model schema-class: " dmCZ )
    (.setAttr! co
               K_MCACHE
               (MakeMetaCache (if (hgl? dmCZ)
                                  (let [ sc (MakeObj dmCZ) ]
                                       (when-not (instance? Schema sc)
                                                 (throw (ConfigError. (str "Invalid Schema Class " dmCZ))))
                                       sc)
                                   (MakeSchema [])) ))

    (when (nichts? mCZ) (log/warn "============> NO MAIN-CLASS DEFINED."))
    ;;(test-nestr "Main-Class" mCZ)

    (when (hgl? mCZ)
      (let [ obj (MakeObj mCZ) ]
        (cond
          (satisfies? CljAppMain obj)
          (doCljApp co app obj)
          (instance? AppMain obj)
          (doJavaApp co obj)
          :else (throw (ConfigError. (str "Invalid Main Class " mCZ))))
        (.setAttr! co :main-app obj)
        (log/info "application main-class " mCZ " created and invoked")))

    (let [ sf (File. appDir (str DN_CONF "/static-routes.conf"))
           rf (File. appDir (str DN_CONF "/routes.conf")) ]
      (.setAttr! co :routes
        (vec (concat (if (.exists sf) (LoadRoutes sf) [] )
                     (if (.exists rf) (LoadRoutes rf) [] ))) ))

    (let [ svcs (:services env) ]
      (if (empty? svcs)
          (log/warn "No system service defined in env.conf.")
          (.reifyServices ^comzotohlabscljc.tardis.impl.ext.ContainerAPI co)))

    ;; start the scheduler
    (.activate sc cfg)

    (log/info "Initialized app: " (.id ^Identifiable co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ext-eof nil)

