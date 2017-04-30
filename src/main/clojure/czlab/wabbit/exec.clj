;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.exec

  (:require [czlab.basal.resources :refer [loadResource]]
            [czlab.basal.scheduler :refer [scheduler<>]]
            [czlab.convoy.mime :refer [setupCache]]
            [czlab.horde.connect :refer [dbopen<+>]]
            [czlab.basal.meta :refer [getCldr]]
            [czlab.basal.format :refer [readEdn]]
            [czlab.twisty.codec :refer [pwd<>]]
            [czlab.basal.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.horde.core
             :refer [dbspec<>
                     dbpool<>
                     dbschema<>]])

  (:use [czlab.twisty.codec]
        [czlab.wabbit.base]
        [czlab.horde.core]
        [czlab.basal.core]
        [clojure.walk]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.wabbit.xpis])

  (:import [java.security SecureRandom]
           [java.io File StringWriter]
           [java.util Date Locale]
           [czlab.basal Cljrt]
           [czlab.jasal
            Versioned
            Initable
            Startable
            Idable
            Config
            I18N
            Activable
            Disposable]
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
  ^chars [evt] (some-> (get-pluglet evt) get-server pkey-chars))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool "" [co gid]
  (get (:dbps @co) (keyword (stror gid dft-dbid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI "" [co gid]
  (when-some
    [p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (dbopen<+> p (:schema @co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- relSysRes "" [exec]
  (log/info "execvisor releasing system resources")
  (some-> ^Disposable (get-scheduler exec) .dispose)
  (doseq [[k v] (:dbps @exec)]
    (log/debug "finz dbpool %s" (name k))
    (shut-down v))
  (closeQ (cljrt exec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- plug! "" [co p cfg0]
  (log/info "preparing puglet %s..." p)
  (log/info "config params=\n%s" cfg0)
  (.init ^Initable p cfg0)
  (log/info "puglet - ok")
  p)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleDep "" [co out [dn dv]]
  (log/debug "handleDep: %s %s" dn dv)
  (let [[dep cfg] dv
        v (plugletViaType<> co dep (keyword dn))
        v (plug! co v cfg)]
    (assoc! out (id?? v) v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xrefPlugs<> "" [exec plugs]
  (let
    [ps
     (preduce<map>
       #(let
          [[k cfg] %2
           {:keys [$pluggable
                   enabled?]} cfg]
          (if-not (or (false? enabled?)
                      (nil? $pluggable))
            (let [v (plugletViaType<> exec $pluggable k)
                  v (plug! exec v cfg)
                  deps (:deps (:pspec @v))]
              (log/debug "pluglet %s: deps = %s" k deps)
              (-> (reduce
                    (fn [m d]
                      (handleDep exec m d)) %1 deps)
                  (assoc! (id?? v) v)))
            %1))
       plugs)]
    (->>
      (let [api :czlab.wabbit.jmx.core/JmxMonitor
            {:keys [jmx] :as conf} (.config ^Config exec)]
        (if (and (!false? (:enabled? jmx))
                 (not-empty ps))
          (let [x (plugletViaType<> exec api :$jmx)
                x (plug! exec x jmx)]
            (assoc ps (id?? x) x))
          ps))
      (alter-atomic exec assoc :plugs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs "" [exec conf]
  (preduce<map>
    #(let
       [[k v] %2]
       (if (!false? (:enabled? v))
         (let
           [pwd (pwd<> (:passwd v)
                          (pkey-chars exec))
            cfg (merge v
                       {:passwd (p-text pwd)
                        :id k})]
           (->> (dbpool<> (dbspec<> cfg) cfg)
                (assoc! %1 k)))
         %1))
    (:rdbms conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init2 "" [exec {:keys [locale conf] :as env}]
  (let
    [mcz (strKW (get-in conf
                        [:info :main]))
     rts (cljrt exec)
     pid (id?? exec)
     res (->>
           (format c-rcprops (.getLanguage ^Locale locale))
           (io/file (get-home-dir exec) dn-etc))]
    (when (fileRead? res)
      (->> (loadResource res)
           (I18N/setBundle pid))
      (log/info "loaded i18n resources"))
    (log/info "processing db-defs...")
    (doto->>
      (maybeInitDBs exec conf)
      (alter-atomic exec assoc :dbps)
      (log/debug "db [dbpools]\n%s"))
    ;; build the user data-models?
    (when-some+
      [dmCZ (strKW (:data-model conf))]
      (log/info "schema-func: %s" dmCZ)
      (if-some
        [sc (try! (.callEx rts
                           dmCZ (vargs* Object exec)))]
        (alter-atomic exec assoc :schema sc)
        (trap! Exception
               "Invalid data-model schema ")))
    (.activate ^Activable (get-scheduler exec))
    (xrefPlugs<> exec (:plugins conf))
    (log/info "main func: %s" mcz)
    (if (hgl? mcz) (.callEx rts mcz (vargs* Object exec)))
    (log/info "execvisor: (%s) initialized - ok" pid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-atomic ExecvisorObj

  Execvisor
  (cljrt [me] (:rts @me))
  (uptime-in-millis [_] (- (now<>) start-time))
  (kill9! [me] (apply (:stop! @me) []))
  (get-home-dir [me] (:homeDir @me))
  (get-locale [me] (:locale @me))
  (get-start-time [_] start-time)
  (has-child? [me sid]
    (in? (:plugs @me) (keyword sid)))
  (get-child [me sid]
    (get (:plugs @me) (keyword sid)))
  (get-scheduler [me] (:cpu @me))

  SqlAccess
  (acquire-db-pool [this gid] (maybeGetDBPool this gid))
  (acquire-db-api [this gid] (maybeGetDBAPI this gid))
  (dft-db-pool [this] (maybeGetDBPool this ""))
  (dft-db-api [this] (maybeGetDBAPI this ""))

  KeyAccess
  (pkey-bytes [this] (->> (get-in (:conf @this)
                                  [:info :digest])
                          str
                          bytesit))
  (pkey-chars [me] (->> (get-in (:conf @me)
                               [:info :digest])
                       str
                       .toCharArray))

  Versioned
  (version [me] (->> [:info :version]
                    (get-in (:conf @me))))

  Config
  (config [me] (:conf @me))

  Initable
  (init [me arg]
    (let [{:keys [encoding homeDir]} arg]
      (sysProp! "file.encoding" encoding)
      (logcomp "init" me)
      (alter-atomic me merge arg)
      (-> (io/file homeDir
                   dn-etc
                   "mime.properties")
          setupCache)
      (log/info "loaded mime#cache - ok")
      (init2 me @me)))

  Startable
  (start [me _]
    (let [svcs (:plugs @me)
          jmx (:$$jmx svcs)]
      (log/info "execvisor starting puglets...")
      (doseq [[k v] svcs]
        (log/info "puglet: %s to start" k)
        (.start ^Startable v))
      (some-> jmx
              (jmx-reg me
                      "czlab" "execvisor" ["root=wabbit"]))
      (log/info "execvisor started")))
  (start [me] (.start me nil))
  (stop [me]
    (let [svcs (:plugs @me)]
      (log/info "execvisor stopping puglets...")
      (doseq [[k v] svcs] (.stop ^Startable v))
      (log/info "execvisor stopped")))

  Idable
  (id [me] (:id @me))

  Disposable
  (dispose [me]
    (let [svcs (:plugs @me)]
      (log/info "execvisor disposing puglets...")
      (doseq [[k v] svcs]
        (.dispose ^Disposable v))
      (relSysRes me)
      (log/info "execvisor disposed"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<> "Create an Execvisor" []

  (let [pid (toKW "Execvisor." (seqint2))
        ps (sname pid)]
    (atomic<> ExecvisorObj
              {:plugs {}
               :id pid
               :cpu (scheduler<> ps)
               :rts (Cljrt/newrt (getCldr) ps) })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

