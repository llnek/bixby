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

  (:require [czlab.basal.resources :as r :refer [loadResource]]
            [czlab.basal.scheduler :as u :refer [scheduler<>]]
            [czlab.convoy.mime :as mi :refer [setupCache]]
            [czlab.horde.connect :as ht :refer [dbopen<+>]]
            [czlab.basal.meta :as m :refer [getCldr]]
            [czlab.basal.format :as f :refer [readEdn]]
            [czlab.twisty.codec :as cc :refer [pwd<>]]
            [czlab.basal.log :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.horde.core
             :as hc
             :refer [dbspec<>
                     dbpool<>
                     dbschema<>]]
            [czlab.wabbit.base :as b]
            [czlab.basal.core :as c]
            [clojure.walk :as cw]
            [czlab.basal.str :as s]
            [czlab.basal.io :as i]
            [czlab.wabbit.xpis :as xp])

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
  ^chars [evt] (some-> (xp/get-pluglet evt) xp/get-server xp/pkey-chars))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool "" [co gid]
  (get (:dbps @co) (keyword (s/stror gid b/dft-dbid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI "" [co gid]
  (when-some
    [p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (ht/dbopen<+> p (:schema @co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- relSysRes "" [exec]
  (log/info "execvisor releasing system resources")
  (some-> ^Disposable (xp/get-scheduler exec) .dispose)
  (doseq [[k v] (:dbps @exec)]
    (log/debug "finz dbpool %s" (name k))
    (hc/shut-down v))
  (i/closeQ (xp/cljrt exec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- plug! "" [co p cfg0]
  (c/do-with [p p]
    (log/info "preparing puglet %s..." p)
    (log/info "config params=\n%s" cfg0)
    (.init ^Initable p cfg0)
    (log/info "puglet - ok")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleDep "" [co out [dn dv]]
  ;;(log/debug "handleDep: %s %s" dn dv)
  (let [[dep cfg] dv
        v (xp/plugletViaType<> co dep (keyword dn))
        v (plug! co v cfg)]
    (assoc! out (c/id?? v) v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xrefPlugs<> "" [exec plugs]
  (let
    [ps
     (c/preduce<map>
       #(let
          [[k cfg] %2
           {:keys [$pluggable
                   enabled?]} cfg]
          (if-not (or (false? enabled?)
                      (nil? $pluggable))
            (let [v (xp/plugletViaType<> exec $pluggable k)
                  v (plug! exec v cfg)
                  deps (:deps (:pspec @v))]
              (log/debug "pluglet %s: deps = %s" k deps)
              (-> (reduce
                    (fn [m d]
                      (handleDep exec m d)) %1 deps)
                  (assoc! (c/id?? v) v)))
            %1))
       plugs)]
    (->>
      (let [api "czlab.wabbit.jmx.core/JmxMonitor"
            {:keys [jmx]} (:conf @exec)]
        (if (and (c/!false? (:enabled? jmx))
                 (not-empty ps))
          (let [x (xp/plugletViaType<> exec api :$jmx)
                x (plug! exec x jmx)]
            (assoc ps (c/id?? x) x))
          ps))
      (c/setf! exec :plugs))
    (log/info "+++++++++++++++++++++++++ pluglets +++++++++++++++++++")
    (doseq [[k _] (:plugs @exec)]
      (log/info "pluglet id= %s" k))
    (log/info "+++++++++++++++++++++++++ pluglets +++++++++++++++++++")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs "" [exec conf]
  (let [pk (xp/pkey-chars exec)]
    (c/preduce<map>
      #(let
         [[k v] %2]
         (if (c/!false? (:enabled? v))
           (let
             [pwd (-> (:passwd v) (cc/pwd<> pk) cc/p-text)
              cfg (merge v {:passwd pwd :id k})]
             (assoc! %1
                     k
                     (hc/dbpool<> (hc/dbspec<> cfg) cfg)))
           %1))
      (:rdbms conf))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init2 "" [exec {:keys [locale version conf] :as env}]
  (let
    [mcz (s/strKW (get-in conf
                          [:info :main]))
     rts (xp/cljrt exec)
     pid (c/id?? exec)
     res (->>
           (format b/c-rcprops
                   (.getLanguage ^Locale locale))
           (io/file (xp/get-home-dir exec) b/dn-etc))]
    (c/setf! exec :version version)
    (when (i/fileRead? res)
      (->> (r/loadResource res)
           (I18N/setBundle pid))
      (log/info "loaded i18n resources"))
    (log/info "processing db-defs...")
    (c/doto->>
      (maybeInitDBs exec conf)
      (c/setf! exec :dbps)
      (log/debug "db [dbpools]\n%s"))
    ;; build the user data-models?
    (c/when-some+
      [dmCZ (s/strKW (:data-model conf))]
      (log/info "schema-func: %s" dmCZ)
      (if-some
        [sc (c/try! (.callEx rts
                             dmCZ (c/vargs* Object exec)))]
        (c/setf! exec :schema sc)
        (c/trap! Exception
                 "Invalid data-model schema ")))
    (.activate ^Activable
               (xp/get-scheduler exec))
    (xrefPlugs<> exec (:plugins conf))
    (log/info "main func: %s" mcz)
    (if (s/hgl? mcz)
      (.callEx rts mcz (c/vargs* Object exec)))
    (log/info "execvisor: (%s) initialized - ok" pid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/decl-mutable ExecvisorObj

  xp/Execvisor

  (uptime-in-millis [_] (- (c/now<>) start-time))
  (get-home-dir [me] (:homeDir @me))
  (get-locale [me] (:locale @me))
  (get-start-time [_] start-time)
  (cljrt [me] (:rts @me))
  (kill9! [me] ((:stop! @me) ))
  (has-child? [me sid]
    (c/in? (:plugs @me) (keyword sid)))
  (get-child [me sid]
    (get (:plugs @me) (keyword sid)))
  (get-scheduler [me] (:cpu @me))

  xp/SqlAccess
  (acquire-db-pool [this gid] (maybeGetDBPool this gid))
  (acquire-db-api [this gid] (maybeGetDBAPI this gid))
  (dft-db-pool [this] (maybeGetDBPool this ""))
  (dft-db-api [this] (maybeGetDBAPI this ""))

  xp/KeyAccess
  (pkey-bytes [me] (-> (get-in (:conf @me)
                               [:info :digest]) c/bytesit))
  (pkey-chars [me] (-> (get-in (:conf @me)
                               [:info :digest]) c/charsit))

  Versioned
  (version [me] (:version @me))

  Initable
  (init [me arg]
    (let [{:keys [encoding homeDir]} arg]
      (c/sysProp! "file.encoding" encoding)
      (b/logcomp "init" me)
      (c/copy* me arg)
      (-> (io/file homeDir
                   b/dn-etc
                   "mime.properties") mi/setupCache)
      (log/info "loaded mime#cache - ok")
      (init2 me @me)))

  Startable
  (start [me _]
    (let [svcs (:plugs @me)
          jmx (:$jmx svcs)]
      (log/info "execvisor starting puglets...")
      (doseq [[k v] svcs]
        (log/info "puglet: %s to start" k)
        (.start ^Startable v))
      (some-> jmx
              (xp/jmx-reg me
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

  (let [pid (s/toKW "Execvisor." (c/seqint2))
        ps (s/sname pid)]
    (c/mutable<> ExecvisorObj
                 {:cpu (u/scheduler<> ps)
                  :plugs {}
                  :dbps {}
                  :id pid
                  :rts (Cljrt/newrt (m/getCldr) ps) })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

