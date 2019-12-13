;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.exec

  (:gen-class)

  (:require [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [clojure.string :as cs]
            [czlab.basal.util :as u]
            [czlab.basal.proc :as p]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.niou.mime :as mi]
            [czlab.hoard.core :as h]
            [czlab.blutbad.core :as b]
            [czlab.twisty.codec :as cc]
            [czlab.hoard.connect :as hc])

  (:import [java.io File StringWriter]
           [java.util Date Locale]
           [java.net URL]
           [clojure.lang Atom]
           [java.security SecureRandom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-dbpool??

  "Get this db config, or default."
  [ctx gid]

  (get (:dbps @ctx) (keyword (c/stror gid b/dft-dbid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-dbapi??

  "Create a db-pool from this config, or default."
  [ctx gid]

  (when-some
    [p (get-dbpool?? ctx gid)]
    (try (hc/dbio<+> p (:schema @ctx))
         (finally
           (c/debug "using db-config: %s." (i/fmt->edn p))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sys-finz

  "Release all system resources."
  [exec ctx]

  (c/info "releasing sys-resources...")
  (doseq [[k v] (:dbps @ctx)]
    (c/finz v)
    (c/debug "finz'ed dbpool %s." k))
  (some-> exec b/scheduler c/finz))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- xref-plugs

  "Scan and instantiate plugins from the config."
  [exec ctx]

  (let [jmx (some->> (get-in @ctx [:conf :jmx])
                     (b/plugin<> exec :$jmx))
        ps (c/preduce<map>
             #(let [[k cfg] %2
                    p (b/plugin<> exec k cfg)]
                (if (nil? p)
                  %1 (assoc! %1 (c/id p) p)))
             (get-in @ctx [:conf :plugins]))
        ps (if (nil? jmx) ps (assoc ps (c/id jmx) jmx))]
    (swap! ctx update-in [:conf] assoc :plugins ps)
    (c/info "+++++++++++++++++++++++++ plugins +++++++++++++++++++")
    (doseq [[k _]
            (get-in @ctx
                    [:conf :plugins])] (c/info "plugin id = %s." k))
    (c/info "+++++++++++++++++++++++++ plugins +++++++++++++++++++")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init-dbs??

  "Initialize and connect all required dbs."
  [exec ctx]

  (let [pk (b/pkey-chars exec)
        m (c/preduce<map>
            #(let [[k v] %2
                   pwd (-> (:passwd v)
                           (cc/pwd<> pk) cc/pw-text)
                   {:keys [driver url
                           user passwd] :as cfg}
                   (merge v {:id k :passwd pwd})]
               (assoc! %1
                       k (h/dbpool<> (h/dbspec<> driver
                                                 url
                                                 user
                                                 passwd) cfg)))
            (filter (fn [[k v :as xs]]
                      (if-not (false? (:enabled? v)) xs))
                    (get-in @ctx [:conf :rdbms])))]
    (try (update-in @ctx [:conf] assoc :rdbms m)
         (finally
           (c/debug "db [dbpools]\n%s" (i/fmt->edn m))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2

  "Step 2 of the initialization."
  [exec ctx]

  (let [{:keys [locale version conf cljrt]} @ctx
        {:keys [info data-model]} conf
        mcz (c/kw->str (:main info))
        id (c/id exec)
        res (->> (c/fmt b/c-rcprops
                        (.getLanguage ^Locale locale))
                 (io/file (b/home-dir exec) b/dn-etc))]
    ;load application resources
    (when (i/file-read? res)
      (b/put-rc-bundle! id (u/load-resource res))
      (c/info "loaded i18n resource: %s." (str res)))
    ;; build the user data-models?
    (c/when-some+
      [dmCZ (c/kw->str data-model)]
      (c/info "schema-func: %s." dmCZ)
      (if-some
        [sc (c/try! (u/call* cljrt
                             dmCZ
                             (c/vargs* Object @ctx)))]
        (swap! ctx assoc :schema sc)
        (c/raise! "Invalid data-model schema: %s!" dmCZ)))
    ;any databases?
    (init-dbs?? exec ctx)
    ;run the main app function, if any
    (when (c/hgl? mcz)
      (c/info "main func: %s." mcz)
      (u/call* cljrt mcz (c/vargs* Object @ctx)))
    ;ok
    ;start the main scheduler
    (c/activate (b/scheduler exec))
    ;build plugins
    (xref-plugs exec ctx)
    (c/info "execvisor: (%s) initialized - ok." id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- execvisor<>

  "Create an Execvisor."
  [ctx]

  (let [cpu (p/scheduler<>)
        _start-time (u/system-time)
        _id (c/x->kw "exec#" (u/seqint2))]
    (reify
      c/Idable
      (id [_] _id)
      c/Versioned
      (version [_] (:version @ctx))
      c/Initable
      (init [me arg]
        (let [{:keys [encoding home-dir]} @arg]
          (c/doto->>
            (io/file home-dir
                     b/dn-etc "mime.properties")
            mi/setup-cache
            (c/info "loaded mime#cache: %s."))
          (u/set-sys-prop!
            "file.encoding" encoding)
          (init2 me arg) me))
      c/Startable
      (start [me]
        (.start me nil))
      (start [me _]
        (let [plugins (get-in @ctx
                              [:conf :plugins])]
          (c/info "execvisor starting plugins...")
          (doseq [[k v] plugins]
            (c/start v)
            (c/info "plugin: %s -> start()" k))
          (some-> (:$jmx plugins)
                  (b/jmx-reg me
                             "czlab"
                             "blutbad"
                             ["root=execvisor"]))
          (c/info "execvisor started - ok.")))
      (stop [me]
        (c/info "execvisor stopping plugins...")
        (doseq [[k v]
                (get-in @ctx
                        [:conf :plugins])]
          (c/stop v)
          (c/info "plugin: %s -> stop()" k))
        (c/info "execvisor stopped - ok."))
      c/Finzable
      (finz [me]
        (c/info "execvisor disposing plugins...")
        (doseq [[k v]
                (get-in @ctx
                        [:conf :plugins])]
          (c/finz v)
          (c/info "plugin: %s -> finz()" k))
        (sys-finz me ctx)
        (c/info "execvisor terminated - ok."))
      b/Execvisor
      (start-time [_] _start-time)
      (uptime [_]
        (- (u/system-time) _start-time))
      (cljrt [_] (:cljrt @ctx))
      (scheduler [_] cpu)
      (home-dir [_] (:home-dir @ctx))
      (locale [_] (:locale @ctx))
      (kill9! [_] (c/funcit?? (:stop! @ctx)))
      (has-plugin? [_ sid]
        (c/in? (get-in @ctx
                       [:conf :plugins]) (keyword sid)))
      (get-plugin [_ sid]
        (get (get-in @ctx
                     [:conf :plugins]) (keyword sid)))
      (dbpool?? [_ gid] (get-dbpool?? ctx gid))
      (dbpool?? [_] (get-dbpool?? ctx ""))
      (dbapi?? [_ gid] (get-dbapi?? ctx gid))
      (dbapi?? [_ ] (get-dbapi?? ctx ""))
      (pkey-bytes [_] (-> (get-in @ctx [:conf :info :digest]) i/x->bytes))
      (pkey-chars [_] (-> (get-in @ctx [:conf :info :digest]) i/x->chars)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- stop-cli

  "This function will stop the process."
  [exec ctx]

  #(let [{:keys [stopcli?]} @ctx]
     (when-not @stopcli?
       (vreset! stopcli? true)
       (c/info "stopping blutbad server...")
       (c/stop exec)
       (c/finz exec)
       (shutdown-agents)
       (c/info "blutbad has stopped - ok."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- primodial

  "Create the execvisor."
  [ctx]

  (c/info "%s" (c/repeat-str 78 "="))
  (c/info "primodial().")
  (c/info "%s" (c/repeat-str 78 "="))
  (c/do-with
    [e (execvisor<> ctx)]
    (->> (stop-cli e ctx)
         (swap! ctx assoc :stop!))
    (c/init e ctx)
    (c/info "%s" (c/repeat-str 78 "*"))
    (c/info "starting blutbad server...")
    (c/info "%s" (c/repeat-str 78 "*"))
    (c/start e)
    (c/info "blutbad started - ok.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-via-config

  "Start blutbad."

  ([home confObj]
   (start-via-config home confObj false))

  ([home confObj join?]
   (let [{{:keys [encoding]} :info
          {:keys [country lang]} :locale} confObj
         ctx (atom {:encoding (c/stror encoding "utf-8")
                    :stopcli? (volatile! false)
                    :home-dir (io/file home)
                    :cljrt (u/cljrt<>)
                    :locale (Locale. (c/stror lang "en")
                                     (c/stror country "US"))
                    :conf confObj
                    :pid-file (io/file home "blutbad.pid")
                    :version (str (some-> b/c-verprops
                                          u/load-resource
                                          (.getString "version")))})]
     ;show class loaders
     (let [cz (u/get-cldr)]
       (c/info "app-loader: %s." (type cz))
       (c/info "sys-loader: %s." (type (.getParent cz))))
     ;show basic info
     (c/info "blutbad.user.dir = %s." (u/fpath home))
     (c/info "blutbad.version = %s." (:version @ctx))
     ;set base bundle
     (c/doto->> (:locale @ctx)
                (u/get-resource b/c-rcb-base)
                b/set-rc-base!
                (c/test-some "base resource"))
     (c/info "blutbad's i18n.base loaded - ok.")
     ;bring up the app
     (primodial ctx)
     ;keep track of process id
     (doto ^File
       (:pid-file @ctx)
       (i/spit-utf8 (p/process-pid)) .deleteOnExit)
     (c/info "wrote blutbad.pid - ok.")
     ;install exit function
     (p/exit-hook (:stop! @ctx))
     (c/info "added shutdown hook - ok.")
     (c/info "blutbad is now running...")
     ;block?
     (when join?
       (loop []
         (if @(:stopcli? @ctx)
           (shutdown-agents) (do (u/pause 3000) (recur))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-via-cons

  "Starts blutbad."
  [home]

  (let [cf (io/file home b/cfg-pod-cf)]
    ;print app banner
    (-> (b/banner) ansi/bold-yellow c/prn!!)
    ;check for config file
    (b/precond-file cf)
    (c/debug "checking for config file: %s" cf)
    ;read config file and continue
    (start-via-config home (b/slurp-conf cf true) true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main

  "Main function."
  [& args]

  (let [[opts _] (u/parse-options args)
        dir (c/if-some+
              [h (:home opts)]
              (io/file h) (u/get-user-dir))]
    (->> dir
         u/fpath
         (u/set-sys-prop!
           "blutbad.user.dir"))
    (start-via-cons dir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;EOF

