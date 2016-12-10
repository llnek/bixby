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
      :author "Kenneth Leung"}

  czlabtest.wabbit.mock

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

  (:use [czlab.wabbit.etc.core]
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

  (:import [czlab.wabbit.etc Gist ServiceError ConfigError]
           [czlab.wabbit.pugs PluginFactory Plugin]
           [czlab.horde Schema JDBCPool DBAPI]
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
           [czlab.wabbit.io IoGist IoEvent]))

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
  ^JDBCPool
  [^Container co ^String gid]
  (let
    [dk (stror gid DEF_DBID)]
    (get
      (.getv (.getx co) :dbps)
      (keyword dk))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI
  ""
  ^DBAPI
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
  (log/info "container releasing system resources")
  (if-some
    [sc (.getv (.getx co) :core)]
    (.dispose ^Disposable sc))
  (doseq [[k v]
          (.getv (.getx co) :dbps)]
    (log/debug "shutting down dbpool %s" (name k))
    (.shutdown ^JDBCPool v))
  (some-> (.cljrt co)
          (.close)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkctr
  ""
  ^Container
  [^Execvisor parObj ^Gist gist]
  (let
    [rts (Cljshim/newrt (getCldr) "mock")
     ctx (.getx gist)
     impl (muble<> {:services {}})]
    (with-meta
      (reify
        Container
        (podKeyBits [this] (bytesify (.podKey this)))
        (podKey [_] "hello world")
        (podDir [this] (getCwd))
        (cljrt [_] rts)
        (getx [_] impl)
        (version [_] "1.0")
        (id [_] "007")
        (name [_] "mock")

        (acquireDbPool [this gid] nil)
        (acquireDbAPI [this gid] nil)
        (acquireDbPool [this] nil)
        (acquireDbAPI [this] nil)

        (parent [_] parObj)
        (setParent [_ x])

        (loadTemplate [_ tpath ctx])
        (isEnabled [_] true)

        (service [_ sid])
        (hasService [_ sid])

        (core [_]
          (.getv impl :core))

        (podConfig [_] {})

        (start [this] )
        (stop [this] )
        (dispose [this] ))

      {:typeid ::Container})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init<c>
  [^Container co ^Execvisor execv]
  (let
    [cpu (scheduler<> (.id co))
     rts (.cljrt co)
     pid (.id co)]
    (.setv (.getx co) :core cpu)
    (.activate ^Activable cpu {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkexe
  ^Execvisor
  []
  (let
    [impl (muble<> {:container nil
                    :pod nil
                    :services {}})]
    (with-meta
      (reify
        Execvisor
        (uptimeInMillis [_] 0)
        (id [_] "001")
        (homeDir [_] (getCwd))
        (locale [_] nil)
        (version [_] "1.0")
        (getx [_] impl)
        (startTime [_] 0)
        (kill9 [_] )
        (start [this] )
        (stop [this] ))
      {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkgist
  ""
  ^Gist
  [^Execvisor ec]
  (let [impl (muble<>)]
    (with-meta
      (reify
        Gist
        (setParent [_ p] )
        (parent [_] ec)
        (version [_] "1.0")
        (id [_] "005")
        (getx [_] impl))
      {:typeid  ::PodGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- service<+>
  ""
  ^Service
  [^Container co svcType nm cfg0]
  (let
    [^Execvisor exe (.parent co)
     bks (.getv (.getx exe) :services)]
    (if-some
      [^IoGist bk (bks svcType)]
      (let
        [obj (service<> co svcType nm (.intern (.getx bk)))
         pkey (.podKey co)
         hid (:handler cfg0)]
        (log/info "preparing service %s..." svcType)
        (log/info "svc meta: %s" (meta obj))
        (log/info "config params =\n%s" cfg0)
        (.init obj cfg0)
        (log/info "service - ok. handler => %s" hid)
        obj)
      (trap! ServiceError
             (str "No such service: " svcType)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ioServices<>
  ""
  ^Container
  [^Container co svcs]
  (->>
    (preduce<map>
      #(let
         [[k cfg] %2
          {:keys [service
                  enabled?]} cfg]
         (if-not (or (false? enabled?)
                     (nil? service))
           (let [v (service<+>
                     co service k cfg)]
             (assoc! %1 (.id v) v))
           %1))
      svcs)
    (.setv (.getx co) :services ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn container<>
  "Create an application container"
  ^Container
  [^Execvisor exe ^Gist gist]
  (doto
    (mkctr exe gist)
    (comp->init  exe)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname
  ""
  ^File
  [^Container co ^String fc]
  (->> (cs/replace fc #"[^a-zA-Z_\-]" "")
       (io/file (.podDir co) "modules" )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pluginInited?
  ""
  [^Container co ^String fc]
  (let [b (.exists (fmtPluginFname co fc))]
    (if b
      (log/info "plugin %s %s"
                "already initialized" fc))
    b))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postInitPlugin
  ""
  [^Container co ^String fc]
  (let [pfile (fmtPluginFname co fc)]
    (writeFile pfile "ok")
    (log/info "initialized plugin: %s" fc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin
  ""
  ^Plugin
  [^Container co ^Cljshim rts kee pc env]
  (log/info "plugin: %s ->factory: %s" kee pc)
  (let
    [[pn opts]
     (if (string? pc)
       [pc {}] [(:name pc) pc])
     _ (log/info "calling plugin fac: %s" pn)
     pf (cast? PluginFactory
               (.call rts pn))
     _ (log/info "plugin fac-obj: %s" pf)
     u (some-> pf
               (.createPlugin co))]
    (log/info "plugin-obj : %s" u)
    (if (and (some? u)
             (.init u {:pod env
                       :pug opts}))
      (do
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
(defmethod comp->init
  ::Container
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
           (format "Resources_%s.properties"
                   (.getLanguage loc))
           (io/file (:podDir conf) DN_ETC))]
    (.setv (.getx co) :core cpu)
    (if (fileRead? res)
      (->> (loadResource res)
           (I18N/setBundle pid)))
    (log/info "about to process db-defs")
    (doto->>
      (maybeInitDBs co env)
      (.setv (.getx co) :dbps)
      (log/debug "db [dbpools]\n%s" ))
    (log/info "about to process plugins")
    (->>
      (preduce<map>
        #(let
           [[k v] %2
            p (doOnePlugin co rts k v env)]
           (if (some? p)
             (assoc! %1 k p)
             %1))
        (:plugins env))
      (.setv (.getx co) :plugins))
    ;; build the user data-models?
    (when-some+
      [dmCZ (:data-model env)]
      (log/info "schema-func: %s" dmCZ)
      (if-some
        [sc (cast? Schema
                   (try! (.call rts dmCZ)))]
        (.setv (.getx co) :schema sc)
        (trap! ConfigError
               "Invalid data-model schema ")))
    (.activate ^Activable cpu {})
    (->> (or (:services env) {})
         (ioServices<> co ))
    (if (hgl? mcz)
      (.call rts mcz))
    (log/info "pod: %s initialized - ok" pid)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mock
  ""
  [kind]
  (case x
    :execvisor (mkexe)
    :pod (mkgist)
    :container
    (let [e (mkexe)
          p (mkgist e)
          c (mkctr e p)]
      (init<c> c e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


