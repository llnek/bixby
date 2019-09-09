;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.exec

  (:require [czlab.basal.util :as u]
            [czlab.basal.proc :as p]
            [czlab.niou.mime :as mi]
            [io.aviso.ansi :as ansi]
            [czlab.hoard.connect :as ht]
            [czlab.basal.io :as i]
            [czlab.twisty.codec :as cc]
            [czlab.basal.log :as l]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.hoard.core :as h]
            [czlab.hoard.connect :as hc]
            [czlab.basal.core :as c]
            [clojure.walk :as cw]
            [czlab.basal.cljrt :as rt]
            [czlab.basal.str :as s]
            [czlab.basal.proto :as po]
            [czlab.wabbit.core :as wc]
            [czlab.wabbit.xpis :as xp])

  (:import [java.security SecureRandom]
           [java.io File StringWriter]
           [java.util Date Locale]
           [java.net URL]
           [clojure.lang Atom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:private start-time (u/system-time))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pod-key-from-event
  "Get the secret application key."
  ^chars [evt] (some-> (xp/get-pluglet evt) po/parent xp/pkey-chars))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-get-dbpool
  [co gid]
  (get (:dbps @co) (keyword (s/stror gid wc/dft-dbid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-get-dbapi
  [co gid]
  (when-some
    [p (maybe-get-dbpool co gid)]
    (l/debug "using dbcfg: %s." p)
    (ht/dbio<+> p (:schema @co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- rel-sys-res
  [exec ctx]
  (l/info "execvisor releasing system resources.")
  (some-> (xp/get-scheduler exec) (po/dispose))
  (doseq [[k v] (:dbps @ctx)]
    (hc/db-finz v)
    (l/debug "finz'ed dbpool %s." k))
  (i/klose (xp/cljrt exec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- plug!
  [co p cfg0]
  (c/do-with [p]
    (l/info "preparing puglet %s." p)
    (l/info "cfg params= %s." cfg0)
    (po/init p cfg0)
    (l/info "puglet - ok.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-dep
  [co out [dn dv]]
  ;;(l/debug "handle-dep: %s %s." dn dv)
  (let [[dep cfg] dv
        v (xp/pluglet-via-type<> co dep (keyword dn))
        v (plug! co v cfg)]
    (assoc! out (po/id v) v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- xref-plugs<>
  [exec ctx]
  (let [ps
        (c/preduce<map>
          #(let [[k cfg] %2
                 {:keys [$pluggable enabled?]} cfg]
             (if-not (or (false? enabled?)
                         (nil? $pluggable))
               (let [v (xp/pluglet-via-type<> exec $pluggable k)
                     v (plug! exec v cfg)
                     deps (get-in @v [:pspec :deps])]
                 (l/debug "pluglet %s: deps = %s." k deps)
                 (-> (reduce
                       (fn [m d]
                         (handle-dep exec m d)) %1 deps)
                     (assoc! (po/id v) v)))
               %1)) (:plugins @ctx))]
    (swap! ctx
           #(assoc %
                   :plugins
                   (let [api "czlab.wabbit.jmx.core/JmxMonitor"
                         {:keys [jmx]} (:conf @ctx)]
                     (if (and (not-empty ps)
                              (c/!false? (:enabled? jmx)))
                       (let [x (xp/pluglet-via-type<> exec api :$jmx)
                             x (plug! exec x jmx)]
                         (assoc ps (po/id x) x)) ps))))
    (l/info "+++++++++++++++++++++++++ pluglets +++++++++++++++++++")
    (doseq [[k _] (:plugins @ctx)]
      (l/info "pluglet id= %s." k))
    (l/info "+++++++++++++++++++++++++ pluglets +++++++++++++++++++")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-init-dbs
  [exec conf]
  (let [pk (xp/pkey-chars exec)]
    (c/preduce<map>
      #(let [[k v] %2]
         (if (c/!false? (:enabled? v))
           (let [pwd (-> (:passwd v) (cc/pwd<> pk) cc/pw-text)
                 {:keys [driver url user passwd] :as cfg}
                 (merge v {:passwd pwd :id k})]
             (assoc! %1
                     k
                     (h/dbpool<> (h/dbspec<> driver url user passwd) cfg))) %1))
      (:rdbms conf))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2
  [exec ctx]
  (let
    [{:keys [locale version conf cljrt]} @ctx
     {:keys [info plugins]} conf
     mcz (s/kw->str (:main info))
     id (po/id exec)
     res (->> (format wc/c-rcprops
                      (.getLanguage ^Locale locale))
           (io/file (xp/get-home-dir exec) wc/dn-etc))]
    (when (i/file-read? res)
      (wc/put-rc-bundle! id (u/load-resource res))
      (l/info "loaded i18n resources, id= %s." id))
    (l/info "processing db-defs...")
    (let [ps (maybe-init-dbs exec conf)]
      (swap! ctx #(assoc % :dbps ps))
      (l/debug "db [dbpools]\n%s" (i/fmt->edn ps)))
    ;; build the user data-models?
    (c/when-some+
      [dmCZ (s/kw->str (:data-model conf))]
      (l/info "schema-func: %s." dmCZ)
      (if-some
        [sc (c/try! (rt/call* cljrt
                              dmCZ (c/vargs* Object @ctx)))]
        (swap! ctx #(assoc % :schema sc))
        (c/raise! "Invalid data-model schema!")))
    (po/activate (xp/get-scheduler exec))
    (xref-plugs<> exec ctx)
    (l/info "main func: %s." mcz)
    (if (s/hgl? mcz)
      (rt/call* cljrt mcz (c/vargs* Object @ctx)))
    (l/info "execvisor: (%s) initialized - ok." id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn execvisor<>
  "Create an Execvisor." [ctx]
  (let [cpu (p/scheduler<>)
        _id (s/x->kw "exec#" (u/seqint2))]
    (reify
      po/Idable
      (id [_] _id)
      xp/Execvisor
      (uptime-in-millis [_] (- (u/system-time) start-time))
      (get-start-time [_] start-time)
      (cljrt [_] (:cljrt @ctx))
      (get-scheduler [_] cpu)
      (get-home-dir [_] (:home-dir @ctx))
      (get-locale [_] (:locale @ctx))
      (kill9! [_] ((:stop! @ctx)))
      (has-child? [_ sid]
        (c/in? (:plugins @ctx) (keyword sid)))
      (get-child [_ sid]
        (get (:plugins @ctx) (keyword sid)))
      po/Versioned
      (version [_] (:version @ctx))
      po/Initable
      (init [me arg]
        (let [{:keys [encoding home-dir]} @arg]
          (u/sys-prop! "file.encoding" encoding)
          (wc/log-comp "init" me)
          (-> (io/file home-dir
                       wc/dn-etc
                       "mime.properties") mi/setup-cache)
          (l/info "loaded mime#cache - ok.")
          (init2 me arg)))
      po/Startable
      (start [me]
        (let [{:keys [plugins]} @ctx]
          (l/info "execvisor starting puglets...")
          (doseq [[k v] plugins]
            (l/info "puglet: %s -> start()" k)
            (po/start v))
          (some-> (:$jmx plugins)
                  (xp/jmx-reg me
                              "czlab" "execvisor" ["root=wabbit"]))
          (l/info "execvisor started - ok.")))
      (stop [me]
        (let [{:keys [plugins]} @ctx]
          (l/info "execvisor stopping puglets...")
          (doseq [[k v] plugins] (po/stop v))
          (l/info "execvisor stopped - ok.")))
      po/Finzable
      (finz [me]
        (let [{:keys [plugins]} @ctx]
          (l/info "execvisor disposing puglets...")
          (doseq [[k v] plugins] (po/finz v))
          (rel-sys-res me ctx)
          (l/info "execvisor terminated - ok.")))
      xp/SqlAccess
      (acquire-db-pool [_ gid] (maybe-get-dbpool ctx gid))
      (acquire-db-api [_ gid] (maybe-get-dbapi ctx gid))
      (dft-db-pool [_] (maybe-get-dbpool _ ""))
      (dft-db-api [_] (maybe-get-dbapi _ ""))
      xp/KeyAccess
      (pkey-bytes [_] (-> (get-in (:conf @ctx)
                                  [:info :digest]) i/x->bytes))
      (pkey-chars [_] (-> (get-in (:conf @ctx)
                                  [:info :digest]) i/x->chars)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- stop-cli
  [exec gist]
  #(let [{:keys [stopcli? kill-hook]} @gist]
     (when-not @stopcli?
       (vreset! stopcli? true)
       (l/info "\n\nunhooking remote shutdown...")
       (c/funcit?? kill-hook)
       (l/info "%s\n%s"
               "remote hook terminated - ok."
               "about to stop wabbit server...")
       (po/stop exec)
       (po/finz exec)
       (shutdown-agents)
       (l/info "wabbit has stopped successfully."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- hook-to-kill
  [exec gist]
  (let [{:keys [stop! cljrt hook]} @gist]
    (rt/call* cljrt
              "czlab.wabbit.plugs.http/discarder!" (c/vargs* Object stop! hook))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- enable-kill-hook
  [exec gist]
  (l/info "enabling remote shutdown hook...")
  (swap! gist
         #(assoc %
                 :kill-hook (hook-to-kill exec gist))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- primodial
  [ctx]
  (l/info "\n%s\ninside primodial()\n%s."
          (c/repeat-str 78 "=") (c/repeat-str 78 "="))
  (c/do-with [e (execvisor<>)]
    (swap! ctx #(assoc %
                       :stop! (stop-cli e ctx)))
    (enable-kill-hook e ctx)
    (po/init e ctx)
    (l/info "\n%s\nstarting wabbit server...\n%s."
            (c/repeat-str 78 "*") (c/repeat-str 78 "*"))
    (po/start e)
    (l/info "wabbit started - ok.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-via-config
  ([cwd confObj]
   (start-via-config cwd confObj false))
  ([cwd confObj join?]
   (let [{{:keys [encoding]} :info
          {:keys [country lang]} :locale} confObj
         loc (Locale. (s/stror lang "en")
                      (s/stror country "US"))
         rc (u/get-resource wc/c-rcb-base loc)
         hook {:host "localhost" :port 4444 :threads 1}
         [h p] (-> "wabbit.kill.port" u/sys-prop (s/split ":"))
         hook (if (s/hgl? h) (assoc hook :host h) hook)
         hook (if (s/hgl? p) (assoc hook :port (c/s->long p 4444)) hook)
         v (str (some-> wc/c-verprops u/load-resource (.getString "version")))
         fp (io/file cwd "wabbit.pid")
         cz (u/get-cldr)
         ctx (atom {:version v
                    :hook hook
                    :dbps {}
                    :plugins {}
                    :stopcli? (volatile! false)
                    :home-dir (io/file cwd)
                    :cljrt (rt/cljrt<> (u/get-cldr))
                    :pid-file fp
                    :locale loc
                    :conf confObj
                    :encoding (s/stror encoding "utf-8")})]
     (l/info (str "wabbit.user.dir = %s."
                  "wabbit.version = %s.") (u/fpath cwd) v)
     (c/doto->> rc
                wc/set-rc-base! (c/test-some "base resource"))
     (l/info "wabbit's i18n#base loaded - ok.")
     (c/do-with [exec (primodial ctx)]
       (doto fp
         (i/spit-utf8 (p/process-pid)) .deleteOnExit)
       (l/info "wrote wabbit.pid - ok.")
       (p/exit-hook (:stop! @ctx))
       (l/info "added shutdown hook- ok.")
       (l/info "app-loader: %s." (type cz))
       (l/info "sys-loader: %s." (type (.getParent cz)))
       (l/debug "%s" (i/fmt->edn @ctx))
       (l/info "wabbit is now running...")
       (when join?
         (loop []
           (if @(:stopcli? @ctx)
             (shutdown-agents)
             (do (u/pause 3000) (recur)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-via-cons
  "" [cwd]
  (c/prn!! (ansi/bold-yellow (wc/banner)))
  (wc/precond-file
    (io/file cwd wc/cfg-pod-cf))
  (start-via-config cwd
                    (wc/slurp-conf cwd wc/cfg-pod-cf true) true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  [& args]
  (let [[p1 p2] args]
    (c/do-with [dir (io/file (if (or (= "-home" p1)
                                     (= "--home" p1)) p2 (u/get-cwd)))]
      (u/sys-prop! "wabbit.user.dir" (u/fpath dir)) (start-via-cons dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;EOF

