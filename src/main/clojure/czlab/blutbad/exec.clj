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
            [czlab.hoard.connect :as hc]
            [czlab.blutbad.jmx.core :as jmx])

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

  (try
    (some-> (get-dbpool?? ctx gid)
            (hc/dbio<+> (:schema @ctx)))
    (finally
      (c/debug "using db-config: %s." gid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- xref-plugs

  "Scan and instantiate plugins from the config."
  [exec ctx]

  (let [jmx (some->> (get-in @ctx
                             [:conf :jmx])
                     (b/plugin<> exec :$jmx))
        ps (c/preduce<map>
             #(let [[k cfg] %2
                    p (b/plugin<> exec k cfg)]
                (if (nil? p)
                  %1 (assoc! %1 (c/id p) p)))
             (get-in @ctx [:conf :plugins]))
        ps (if (nil? jmx) ps (assoc ps (c/id jmx) jmx))]
    (swap! ctx
           #(update-in % [:conf] assoc :plugins ps))
    (c/info "+++++++++++++++++++++++++ plugins +++++++++++++++++++")
    (doseq [[k v]
            (get-in @ctx
                    [:conf :plugins])]
      (c/info "plugin id = %s." k)
      (c/info "%s" (:conf v)))
    (c/info "+++++++++++++++++++++++++ plugins +++++++++++++++++++")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init-dbs??

  "Initialize and connect all required dbs."
  [exec ctx]

  (let [pk (i/x->chars (b/pkey exec))
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
    (try (swap! ctx
                #(update-in % [:conf] assoc :rdbms m))
         (finally
           (c/debug "db [dbpools]\n%s" (i/fmt->edn m))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2

  "Step 2 of the initialization."
  [exec ctx]

  (let [{:keys [locale version conf cljrt]} @ctx
        {:keys [env jmx info data-model]} conf
        {:keys [main]} info
        id (c/id exec)
        sys' (merge {:threads (u/pthreads)} env)
        jmx' (-> jmx/JMXSpec :conf (merge jmx))
        res (->> (c/fmt b/c-rcprops
                        (.getLanguage ^Locale locale))
                 (io/file (b/home-dir exec) b/dn-etc))]
    (swap! ctx
           #(update-in %
                       [:conf]
                       assoc :jmx jmx' :env sys'))
    ;load application resources
    (when (i/file-read? res)
      (b/put-rc-bundle! id (u/load-resource res))
      (c/info "loaded i18n resource: %s." (str res)))
    ;; build the user data-models?
    (when (some? data-model)
      (c/info "schema-func: %s." data-model)
      (if-some
        [sc (c/try! (u/call* cljrt
                             data-model
                             (c/vargs* Object @ctx)))]
        (swap! ctx assoc :schema sc)
        (c/raise! "Invalid data-model schema: %s!" data-model)))
    ;any databases?
    (init-dbs?? exec ctx)
    ;run the main app function, if any
    (when (c/hgl? (str main))
      (c/info "main func: %s." main)
      (u/call* cljrt main (c/vargs* Object @ctx)))
    ;start the main scheduler
    (swap! ctx
           assoc
           :cpu (-> (p/scheduler<> id sys') c/activate))
    ;build plugins
    (xref-plugs exec ctx)
    (c/info "execvisor: (%s) initialized - ok." id) exec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- execvisor<>

  "Create an Execvisor."
  [ctx]

  (let [_start-time (u/system-time)
        _id (c/x->kw "exec#" (u/seqint2))]
    (reify
      c/Idable
      (id [_] _id)
      c/Versioned
      (version [_] (:version @ctx))
      c/Initable
      (init [me arg]
        (let [{:keys [encoding home-dir]} @arg
              mf (io/file home-dir
                          b/dn-etc
                          "mime.properties")
              rc (mi/setup-cache mf)]
          (u/set-sys-prop! "file.encoding" encoding)
          (c/info "loaded mime#cache: %s."
                  (if (c/is? File rc) rc "built-in"))
          (init2 me arg)))
      c/Startable
      (start [me]
        (.start me nil))
      (start [me _]
        (c/info "execvisor starting plugins...")
        (let [ps (into {}
                       (map #(let [[k v] %
                                   v' (c/start v)]
                               (c/info "plugin-start: %s" k)
                               (c/info "%s" (:conf v))
                               [k (if (not= k :$jmx)
                                    v'
                                    (c/_2 (b/jmx-reg v'
                                                     me
                                                     "czlab"
                                                     "blutbad"
                                                     ["root=execvisor"])))])
                            (get-in @ctx [:conf :plugins])))]
          (swap! ctx
                 #(update-in %
                             [:conf] assoc :plugins ps))
          (c/info "execvisor started - ok.")))
      (stop [me]
        (c/info "execvisor stopping plugins...")
        (let [ps (into {} (map #(let [[k v] %
                                      v' (c/stop v)]
                                  (c/info "plugin-stop: %s" k)
                                  [k v'])
                               (get-in @ctx [:conf :plugins])))]
          (swap! ctx
                 #(update-in %
                             [:conf] assoc :plugins ps))
          (c/info "execvisor stopped - ok.")))
      c/Finzable
      (finz [me]
        (c/info "execvisor disposing plugins...")
        (let [ps (into {} (map #(let [[k v] %
                                      v' (c/finz v)]
                                  (c/info "plugin-finz: %s" k)
                                  [k v'])
                               (get-in @ctx [:conf :plugins])))
              ds (into {} (map #(let [[k v] %
                                      v' (c/finz v)]
                                  (c/info "db-finz: %s" k)
                                  [k v'])
                               (get-in @ctx [:conf :rdbms])))]
          (swap! ctx
                 #(update-in %
                             [:conf]
                             assoc
                             :rdbms ds
                             :plugins ps))
          (swap! ctx
                 #(assoc :cpu (c/finz (:cpu %))))
          (c/info "execvisor terminated - ok.")))
      b/Execvisor
      (start-time [_] _start-time)
      (uptime [_]
        (- (u/system-time) _start-time))
      (cljrt [_] (:cljrt @ctx))
      (scheduler [_] (:cpu @ctx))
      (home-dir [_] (:home-dir @ctx))
      (locale [_] (:locale @ctx))
      (kill9! [_] (c/funcit?? (:stop! @ctx)))
      (has-plugin? [_ sid]
        (c/in? (get-in @ctx
                       [:conf :plugins]) (keyword sid)))
      (find-plugin [_ ptype]
        (some #(let [[_ v] %]
                 (c/if-inst ptype v v))
              (get-in @ctx [:conf :plugins])))
      (get-plugin [_ sid]
        (get (get-in @ctx
                     [:conf :plugins]) (keyword sid)))
      (dbpool?? [_ gid] (get-dbpool?? ctx gid))
      (dbpool?? [_] (get-dbpool?? ctx ""))
      (dbapi?? [_ gid] (get-dbapi?? ctx gid))
      (dbapi?? [_ ] (get-dbapi?? ctx ""))
      (pkey [_] (-> (get-in @ctx [:conf :info :digest]) i/x->bytes)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- primodial

  "Create the execvisor."
  [ctx stopcli?]

  (c/info "%s" (c/repeat-str 78 "="))
  (c/info "primodial().")
  (c/info "%s" (c/repeat-str 78 "="))
  (c/do-with
    [e (execvisor<> ctx)]
    (->> #(when-not @stopcli?
            (vreset! stopcli? true)
            (c/info "stopping blutbad server...")
            (c/stop e)
            (c/finz e)
            (shutdown-agents)
            (c/info "blutbad has stopped - ok."))
         (c/assoc!! ctx :stop!)
         (c/init e))
    (c/info "%s" (c/repeat-str 78 "*"))
    (c/info "starting blutbad server...")
    (c/info "%s" (c/repeat-str 78 "*"))
    (c/start e)
    (c/info "blutbad started - ok.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start*

  [home confObj join?]

  (let [{{:keys [encoding]} :info
         {:keys [country lang]} :locale} confObj
        stopcli? (volatile! false)
        ctx (atom {:encoding (c/stror encoding "utf-8")
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
    (primodial ctx stopcli?)

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
        (if @stopcli?
          (shutdown-agents) (do (u/pause 3000) (recur)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-via-config

  "Start blutbad."

  ([home confObj]
   (start-via-config home confObj false))

  ([home confObj join?]
   (try
     (start* home confObj join?)
     (catch Throwable _ (c/exception _)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-via-cons

  "Starts blutbad."
  [home]

  (-> (b/banner) ansi/bold-yellow c/prn!!)
  (c/debug "checking for <app.conf>...")
  (-> (b/get-conf-file) b/slurp-conf ((partial start-via-config home) true)))

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

