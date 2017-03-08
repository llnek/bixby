;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.sys.exec

  (:require [czlab.horde.dbio.connect :refer [dbopen<+>]]
            [czlab.basal.resources :refer [loadResource]]
            [czlab.basal.scheduler :refer [scheduler<>]]
            [czlab.convoy.net.mime :refer [setupCache]]
            [czlab.basal.meta :refer [getCldr]]
            [czlab.basal.format :refer [readEdn]]
            [czlab.twisty.codec :refer [passwd<>]]
            [czlab.basal.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.horde.dbio.core
             :refer [dbspec<>
                     dbpool<>
                     dbschema<>]])

  (:use [czlab.wabbit.base.core]
        [czlab.basal.core]
        [clojure.walk]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.wabbit.ctl.core])

  (:import [czlab.wabbit.ctl Pluggable Pluglet PlugMsg PlugError]
           [czlab.jasal Context I18N Activable Disposable]
           [czlab.wabbit.jmx JmxPluglet]
           [czlab.wabbit.sys Execvisor]
           [czlab.wabbit.base Cljshim]
           [czlab.wabbit.base Gist ConfigError]
           [czlab.horde Schema JdbcPool DbApi]
           [java.security SecureRandom]
           [java.io File StringWriter]
           [java.util Date Locale]
           [java.net URL]
           [clojure.lang Atom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private start-time (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getPodKeyFromEvent
  "Get the secret application key"
  ^String [evt] (some.. ^PlugMsg evt source server pkey))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool
  "" ^JdbcPool [co gid]
  ((keyword (stror gid dft-dbid))
   (:dbps (some.. ^Context co getx intern))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI
  "" ^DbApi [co gid]
  (when-some
    [p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (dbopen<+> p
               (:schema (some.. ^Context co getx intern)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- relSysRes
  "" [^Execvisor co]
  (log/info "execvisor releasing system resources")
  (some-> ^Disposable (.core co) .dispose)
  (doseq [[k v]
          (.getv (.getx co) :dbps)]
    (log/debug "closing dbpool %s" (name k))
    (.shutdown ^JdbcPool v))
  (some-> (.cljrt co) .close))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- plug! "" ^Pluglet [co p cfg0]
  (log/info "preparing puglet %s..." p)
  (log/info "config params=\n%s" cfg0)
  (. ^Pluglet p init cfg0)
  (log/info "puglet - ok")
  p)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleDep "" [co out [dn dv]]
  (log/debug "handleDep: %s %s" dn dv)
  (let [[dep cfg] dv
        v (plugletViaType<> co dep (keyword dn))
        v (plug! co v cfg)]
    (assoc! out (.id v) v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xrefPlugs<>
  "" [^Execvisor co plugs]
  (let
    [ps
     (preduce<map>
       #(let
          [[k cfg] %2
           {:keys [$pluggable
                   enabled?]} cfg]
          (if-not (or (false? enabled?)
                      (nil? $pluggable))
            (let [v (plugletViaType<> co $pluggable k)
                  v (plug! co v cfg)
                  deps (:deps (some-> v .spec))]
              (log/debug "pluglet %s: deps = %s" k deps)
              (-> (reduce
                    (fn [m d]
                      (handleDep co m d)) %1 deps)
                  (assoc! (.id v) v)))
            %1))
       plugs)]
    (->>
      (let [api :czlab.wabbit.plugs.jmx.core/JmxMonitor
            {:keys [jmx] :as conf} (.config co)]
        (if (and (!false? (:enabled? jmx))
                 (not-empty ps))
          (let [x (plugletViaType<> co api :$jmx)
                x (plug! co x jmx)]
            (assoc ps (.id x) x))
          ps))
      (.setv (.getx co) :plugs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs
  "" [^Execvisor co conf]
  (preduce<map>
    #(let
       [[k v] %2]
       (if (!false? (:enabled? v))
         (let
           [pwd (passwd<> (:passwd v)
                          (.pkey co))
            cfg (merge v
                       {:passwd (.text pwd)
                        :id k})]
           (->> (dbpool<> (dbspec<> cfg) cfg)
                (assoc! %1 k)))
         %1))
    (:rdbms conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init2 "" [^Execvisor co
                 {:keys [locale conf] :as env}]
  (let
    [mcz (strKW (get-in conf
                        [:info :main]))
     rts (.cljrt co)
     pid (.id co)
     ctx (.getx co)
     res (->>
           (format c-rcprops (.getLanguage ^Locale locale))
           (io/file (.homeDir co) dn-etc))]
    (when (fileRead? res)
      (->> (loadResource res)
           (I18N/setBundle pid))
      (log/info "loaded i18n resources"))
    (log/info "processing db-defs...")
    (doto->>
      (maybeInitDBs co conf)
      (.setv ctx :dbps)
      (log/debug "db [dbpools]\n%s"))
    ;; build the user data-models?
    (when-some+
      [dmCZ (strKW (:data-model conf))]
      (log/info "schema-func: %s" dmCZ)
      (if-some
        [sc (cast? Schema
                   (try! (.callEx rts
                                  dmCZ
                                  (vargs* Object co))))]
        (.setv ctx :schema sc)
        (trap! ConfigError
               "Invalid data-model schema ")))
    (. ^Activable (.core co) activate {})
    (xrefPlugs<> co (:plugins conf))
    (if (hgl? mcz) (.callEx rts mcz (vargs* Object co)))
    (log/info "execvisor: (%s) initialized - ok" pid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>
  "Create an Execvisor" ^Execvisor []

  (let
    [pid (str "exec#" (seqint2))
     impl (muble<> {:plugs {}})
     cpu (scheduler<> pid)
     rts (Cljshim/newrt (getCldr) pid)]
    (with-meta
      (reify Execvisor

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))
        (dftDbPool [this] (maybeGetDBPool this ""))
        (dftDbAPI [this] (maybeGetDBAPI this ""))

        (pkeyBytes [this] (bytesify (.pkey this)))
        (pkey [_] (->> (get-in (.getv impl :conf)
                               [:info :digest])
                       str
                       .toCharArray))

        (cljrt [_] rts)
        (version [_] (->> [:info :version]
                          (get-in (.getv impl :conf))))
        (id [_] pid)
        (getx [_] impl)

        (uptimeInMillis [_] (- (now<>) start-time))
        (kill9 [_] (apply (.getv impl :stop!) []))
        (homeDir [_] (.getv impl :homeDir))
        (locale [_] (.getv impl :locale))
        (startTime [_] start-time)

        (hasChild [_ sid]
          (in? (.getv impl :plugs) (keyword sid)))
        (child [_ sid]
          ((.getv impl :plugs) (keyword sid)))

        (core [_] cpu)
        (config [_] (.getv impl :conf))

        (init [this arg]
          (let [{:keys [encoding homeDir]} arg]
            (sysProp! "file.encoding" encoding)
            (logcomp "init" this)
            (.copyEx impl arg)
            (-> (io/file homeDir
                         dn-etc
                         "mime.properties")
                io/as-url
                setupCache)
            (log/info "loaded mime#cache - ok")
            (init2 this (.intern impl))))

        (stop [_]
          (let [svcs (.getv impl :plugs)]
            (log/info "execvisor stopping puglets...")
            (doseq [[k v] svcs] (.stop ^Pluglet v))
            (log/info "execvisor stopped")))

        (dispose [this]
          (let [svcs (.getv impl :plugs)]
            (log/info "execvisor disposing puglets...")
            (doseq [[k v] svcs]
              (.dispose ^Pluglet v))
            (relSysRes this)
            (log/info "execvisor disposed")))

        (start [this _]
          (let [svcs (.getv impl :plugs)
                jmx (:$$jmx svcs)]
            (log/info "execvisor starting puglets...")
            (doseq [[k v] svcs]
              (log/info "puglet: %s to start" k)
              (. ^Pluglet v start {}))
            (some-> ^JmxPluglet
                    jmx
                    (.reg this
                          "czlab"
                          "execvisor"
                          ["root=wabbit"]))
            (log/info "execvisor started"))))

      {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

