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

  (:gen-class)

  (:require [czlab.basal.util :as u]
            [czlab.basal.proc :as p]
            [czlab.niou.mime :as mi]
            [io.aviso.ansi :as ansi]
            [czlab.basal.io :as i]
            [czlab.basal.log :as l]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.hoard.core :as h]
            [czlab.basal.core :as c]
            [czlab.basal.xpis :as po]
            [czlab.wabbit.core :as wc]
            [czlab.wabbit.xpis :as xp]
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
  [co gid]
  (get (:dbps @co) (keyword (c/stror gid wc/dft-dbid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-dbapi??
  [co gid]
  (when-some
    [p (get-dbpool?? co gid)]
    (try (hc/dbio<+> p (:schema @co))
         (finally
           (l/debug "using dbcfg: %s." p)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sys-finz

  ""
  [exec ctx]

  (l/info "execvisor releasing system resources.")
  (doseq [[k v] (:dbps @ctx)]
    (hc/db-finz v)
    (l/debug "finz'ed dbpool %s." k))
  (some-> exec xp/get-scheduler po/finz))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- xref-plugs

  ""
  [exec ctx]

  (let [api "czlab.wabbit.jmx.core/jmx-monitor<>"
        {:keys [jmx]} (:conf @ctx)
        ;process each plugin
        ps (c/preduce<map>
             #(let [[k cfg] %2
                    {:keys [$pluggable enabled?]} cfg]
                (if-some [p (and (c/!false? enabled?)
                                 $pluggable
                                 (-> (xp/pluglet<>
                                       exec $pluggable k)
                                     (po/init cfg)))]
                  (assoc! %1 (po/id p) p) %1))
             (get-in @ctx [:conf :plugins]))]
    ;add the jmx pluglet
    (->> (if-some [p (and (c/!false? (:enabled? jmx))
                          (-> (xp/pluglet<>
                                exec api :$jmx) (po/init jmx)))]
             (assoc ps (po/id p) p) ps)
         (swap! ctx assoc :plugins))
    (l/info "+++++++++++++++++++++++++ pluglets +++++++++++++++++++")
    (doseq [[k _] (:plugins @ctx)]
      (l/info "pluglet id= %s." k))
    (l/info "+++++++++++++++++++++++++ pluglets +++++++++++++++++++")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init-dbs??

  ""
  [exec ctx]

  (let [pk (xp/pkey-chars exec)
        m (c/preduce<map>
            #(let [[k v] %2]
               (if (false? (:enabled? v))
                 %1
                 (let [pwd (-> (:passwd v)
                               (cc/pwd<> pk) cc/pw-text)
                       {:keys [driver url
                               user passwd] :as cfg}
                       (merge v {:passwd pwd :id k})]
                   (assoc! %1
                           k (h/dbpool<> (h/dbspec<> driver
                                                     url
                                                     user
                                                     passwd) cfg)))))
            (get-in @ctx [:conf :rdbms]))]
    ;here we go
    (try (update-in @ctx [:conf] assoc :rdbms m)
         (finally
           (l/debug "db [dbpools]\n%s" (i/fmt->edn m))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2
  [exec ctx]
  (let [{:keys [locale version conf cljrt]} @ctx
        {:keys [data-model info]} conf
        mcz (c/kw->str (:main info))
        id (po/id exec)
        res (->> (format wc/c-rcprops
                         (.getLanguage ^Locale locale))
                 (io/file (xp/get-home-dir exec) wc/dn-etc))]
    (when (i/file-read? res)
      (wc/put-rc-bundle! id (u/load-resource res))
      (l/info "loaded i18n resources: %s." (str res)))
    (init-dbs?? exec ctx)
    ;; build the user data-models?
    (c/when-some+
      [dmCZ (c/kw->str data-model)]
      (l/info "schema-func: %s." dmCZ)
      (if-some
        [sc (c/try! (u/call* cljrt
                             dmCZ
                             (c/vargs* Object @ctx)))]
        (swap! ctx assoc :schema sc)
        (c/raise! "Invalid data-model schema! " dmCZ)))
    (po/activate (xp/get-scheduler exec))
    (xref-plugs exec ctx)
    (when (c/hgl? mcz)
      (l/info "main func: %s." mcz)
      (u/call* cljrt mcz (c/vargs* Object @ctx)))
    (l/info "execvisor: (%s) initialized - ok." id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn execvisor<>

  "Create an Execvisor."
  [ctx]

  (let [cpu (p/scheduler<>)
        start-time (u/system-time)
        _id (c/x->kw "exec#" (u/seqint2))]
    (reify
      po/Idable
      (id [_] _id)
      xp/Execvisor
      (get-start-time [_] start-time)
      (uptime-in-millis [_]
        (- (u/system-time) start-time))
      (cljrt [_] (:cljrt @ctx))
      (get-scheduler [_] cpu)
      (get-home-dir [_] (:home-dir @ctx))
      (get-locale [_] (:locale @ctx))
      (kill9! [_] (c/funcit?? (:stop! @ctx)))
      (has-plugin? [_ sid]
        (c/in? (:plugins @ctx) (keyword sid)))
      (get-plugin [_ sid]
        (get (:plugins @ctx) (keyword sid)))
      po/Versioned
      (version [_] (:version @ctx))
      po/Initable
      (init [me arg]
        (let [{:keys [encoding home-dir]} @arg
              f (io/file home-dir
                         wc/dn-etc "mime.properties")]
          (u/set-sys-prop! "file.encoding" encoding)
          (mi/setup-cache f)
          (l/info "loaded mime#cache: %s." f)
          (init2 me arg) me))
      po/Startable
      (start [me] (.start me nil))
      (start [me _]
        (let [{:keys [plugins]} @ctx]
          (l/info "execvisor starting plugins...")
          (doseq [[k v] plugins]
            (l/info "plugin: %s -> start()" k)
            (po/start v))
          (some-> (:$jmx plugins)
                  (xp/jmx-reg me
                              "czlab" "execvisor" ["root=wabbit"]))
          (l/info "execvisor started - ok.")))
      (stop [me]
        (let [{:keys [plugins]} @ctx]
          (l/info "execvisor stopping plugins...")
          (doseq [[k v] plugins] (po/stop v))
          (l/info "execvisor stopped - ok.")))
      po/Finzable
      (finz [me]
        (let [{:keys [plugins]} @ctx]
          (l/info "execvisor disposing plugins...")
          (doseq [[k v] plugins] (po/finz v))
          (sys-finz me ctx)
          (l/info "execvisor terminated - ok.")))
      xp/SqlAccess
      (acquire-dbpool?? [_ gid] (get-dbpool?? ctx gid))
      (acquire-dbpool?? [_] (xp/acquire-dbpool?? _ ""))
      (acquire-dbapi?? [_ gid] (get-dbapi?? ctx gid))
      (acquire-dbapi?? [_ ] (xp/acquire-dbapi?? _ ""))
      xp/KeyAccess
      (pkey-bytes [_] (-> (get-in (:conf @ctx)
                                  [:info :digest]) i/x->bytes))
      (pkey-chars [_] (-> (get-in (:conf @ctx)
                                  [:info :digest]) i/x->chars)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- stop-cli

  "This function will stop the process."
  [exec ctx]

  #(let [{:keys [stopcli? kill-hook]} @ctx]
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
  [exec ctx]
  (let [{:keys [stop! cljrt hook]} @ctx]
    (u/call* cljrt
             :czlab.wabbit.plugs.http/discarder! (c/vargs* Object stop! hook))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- enable-kill-hook
  [exec ctx]
  (l/info "enabling remote shutdown hook...")
  (swap! ctx assoc :kill-hook (hook-to-kill exec ctx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- primodial

  "Create the execvisor."
  [ctx]

  (l/info "\n%s\ninside primodial()\n%s."
          (c/repeat-str 78 "=") (c/repeat-str 78 "="))
  (c/do-with [e (execvisor<> ctx)]
    (swap! ctx assoc :stop! (stop-cli e ctx))
    ;TODO
    ;(enable-kill-hook e ctx)
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
         loc (Locale. (c/stror lang "en")
                      (c/stror country "US"))
         rc (u/get-resource wc/c-rcb-base loc)
         hook {:host "localhost" :port 4444 :threads 1}
         [h p] (-> "wabbit.kill.port" u/get-sys-prop (c/split ":"))
         hook (if (c/hgl? h) (assoc hook :host h) hook)
         hook (if (c/hgl? p) (assoc hook :port (c/s->long p 4444)) hook)
         v (str (some-> wc/c-verprops u/load-resource (.getString "version")))
         fp (io/file cwd "wabbit.pid")
         cz (u/get-cldr)
         ctx (atom {:stopcli? (volatile! false)
                    :home-dir (io/file cwd)
                    :cljrt (u/cljrt<>)
                    :version v
                    :hook hook
                    :dbps {}
                    :plugins {}
                    :pid-file fp
                    :locale loc
                    :conf confObj
                    :encoding (c/stror encoding "utf-8")})]
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
  (c/doto->>
    (io/file cwd wc/cfg-pod-cf)
    (l/debug "checking for file: %s")
    (wc/precond-file))
  (start-via-config cwd
                    (wc/slurp-conf cwd wc/cfg-pod-cf true) true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  [& args]
  (let [[p1 p2] args]
    (c/do-with [dir (io/file (if (or (= "-home" p1)
                                     (= "--home" p1)) p2 (u/get-user-dir)))]
      (u/set-sys-prop! "wabbit.user.dir" (u/fpath dir)) (start-via-cons dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;EOF

