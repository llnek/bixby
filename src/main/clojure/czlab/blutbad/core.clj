;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.core

  (:require [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [clojure.walk :as cw]
            [clojure.string :as cs]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.proc :as p])

  (:import [java.util ResourceBundle Locale]
           [java.io File IOException]
           [java.util Locale]
           [javax.management ObjectName]
           [org.apache.commons.text StringSubstitutor]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro s2ms
  "Convert seconds to milliseconds."
  [s]
  `(let [t# ~s] (if (czlab.basal.core/spos? t#) (* 1000 t#) 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String c-verprops "czlab/blutbad/version.properties")
(def ^String c-rcb-base "czlab.blutbad/Resources")
(def ^String en-rcprops "Resources_en.properties")
(def ^String c-rcprops  "Resources_%s.properties")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String shutdown-uri "/kill9")
(def ^String dft-dbid "default")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String meta-inf  "META-INF")
(def ^String web-inf  "WEB-INF")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String dn-target "target")
(def ^String dn-build "build")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String dn-templates  "templates")
(def ^String dn-classes "classes")
(def ^String dn-bin "bin")
(def ^String dn-conf "conf")
(def ^String dn-lib "lib")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String dn-cfgapp "etc/app")
(def ^String dn-etc "etc")
(def ^String dn-cfgweb "etc/web")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String dn-scripts "scripts")
(def ^String dn-styles "styles")
(def ^String dn-pub "public")
(def ^String dn-logs "logs")
(def ^String dn-tmp "tmp")
(def ^String dn-dbs "dbs")
(def ^String dn-dist "dist")
(def ^String dn-views  "htmls")
(def ^String dn-pages  "pages")
(def ^String dn-patch "patch")
(def ^String dn-media "media")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String web-classes  (str web-inf  "/" dn-classes))
(def ^String web-lib  (str web-inf  "/" dn-lib))
(def ^String web-log  (str web-inf  "/logs"))
(def ^String web-xml  (str web-inf  "/web.xml"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String mn-rnotes (str meta-inf "/" "RELEASE-NOTES.txt"))
(def ^String mn-readme (str meta-inf "/" "README.md"))
(def ^String mn-notes (str meta-inf "/" "NOTES.txt"))
(def ^String mn-lic (str meta-inf "/" "LICENSE.txt"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String pod-cf  "app.conf")
(def ^String cfg-pod-cf  (str dn-conf "/" pod-cf))
(def ^String cfg-pub-pages  (str dn-pub "/" dn-pages))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def evt-opts :____eventoptions)
(def jslot-cred :credential)
(def jslot-user :principal)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/defonce-
  rc-bundles-cache
  (atom {:base nil :rcbs {}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn del-rc-bundle!

  "Remove a resource bundle from the cache."
  [b]

  (doto rc-bundles-cache
    (swap! update-in [:rcbs] dissoc b)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn put-rc-bundle!

  "Add a resource bundle to the cache."
  [b rc]

  (doto rc-bundles-cache
    (swap! update-in [:rcbs] assoc b rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-rc-bundle

  "Get a cached bundle."
  [b]

  (if b (get-in @rc-bundles-cache [:rcbs b])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-rc-base

  "Get the baseline bundle."
  []

  (:base @rc-bundles-cache))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn set-rc-base!

  "Set the baseline resource bundle."
  [base]

  (doto rc-bundles-cache (swap! assoc :base base)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn banner

  "A ASCII banner."
  {:no-doc true :tag String} []

  (str "___.   .__          __ ___.               .___" "\n"
       "\\_ |__ |  |  __ ___/  |\\_ |__ _____     __| _/" "\n"
       " | __ \\|  | |  |  \\   __\\ __ \\\\__  \\   / __ | " "\n"
       " | \\_\\ \\  |_|  |  /|  | | \\_\\ \\/ __ \\_/ /_/ | " "\n"
       " |___  /____/____/ |__| |___  (____  /\\____ | " "\n"
       "     \\/                     \\/     \\/      \\/ " "\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-proc-dir

  "Get the current directory."
  ^File []

  (c/if-some+
    [d (u/get-sys-prop "blutbad.user.dir")] (io/file d) (u/get-user-dir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-conf-file

  "Get the app's config file - app.conf.
  First check the app-dir, then app-dir/conf."
  ^File []

  (let [f1 (io/file (get-proc-dir) pod-cf)
        f2 (io/file (get-proc-dir) dn-conf pod-cf)]
    (cond (i/file-read? f1) f1
          (i/file-read? f2) f2
          :else (u/throw-IOE "No config file or not readable."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-sys-props

  "Expand system properties found in value."
  ^String [value]

  (if (c/nichts? value)
    value (StringSubstitutor/replaceSystemProperties value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-env-vars

  "Expand environment vars found in value."
  ^String [value]

  (if (c/nichts? value)
    value (-> (System/getenv) StringSubstitutor. (.replace ^String value))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-vars

  "Replace all variables in value."
  ^String [value]

  (if (or (c/nichts? value)
          (nil? (cs/index-of value "${")))
    value
    (-> (cs/replace value
                    "${pod.dir}"
                    "${blutbad.user.dir}")
        expand-sys-props expand-env-vars)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-vars*

  "Replace all variables in this form."
  [form]

  (cw/postwalk #(if (string? %) (expand-vars %) %) form))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn read-conf

  "Parse an edn configuration file."
  {:tag String}

  ([podDir confile]
   (read-conf (io/file podDir dn-conf confile)))

  ([file]
   (c/doto->> (i/change-content (io/file file)
                                #(expand-vars %))
              (c/debug "[%s]\n%s." file))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the directory is readable & writable.
(defn precond-dir

  "Assert dir(s) are read-writeable?"
  [f & dirs]

  (c/let#true
    [base (get-rc-base)]
    (doseq [d (cons f dirs)]
      (->> (i/dir-read-write? d)
           (c/test-cond (u/rstr base "dir.no.rw" d))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn precond-file

  "Assert file(s) are readable?"
  [ff & files]

  (c/let#true
    [base (get-rc-base)]
    (doseq [f (cons ff files)]
      (->> (i/file-read? f)
           (c/test-cond (u/rstr base "file.no.r" f))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn maybe-dir??

  "If the key maps to a File?"
  ^File [m kn]

  (let [v (get m kn)]
    (condp instance? v
      String (io/file v)
      File v
      (u/throw-IOE (u/rstr (get-rc-base) "blutbad.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn slurp-conf

  "Parse config file."

  ([podDir cf]
   (slurp-conf (io/file podDir cf)))

  ([f]
   (-> (str "{\n"
            (i/slurp-utf8 f) "\n}") expand-vars i/read-edn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn spit-conf

  "Write out config file."
  ([podDir conf cfgObj]
   (spit-conf (io/file podDir conf) cfgObj))

  ([f cfgObj]
  (let [s (c/strim (i/fmt->edn cfgObj))]
    (i/spit-utf8 f
                 (if-not (c/wrapped? s "{" "}")
                   s (-> (c/drop-head s 1) (c/drop-tail 1)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn prevar-cfg

  "Scan the config object looking for references to
  functions, supported markers are $error, $action
  For each one found, load it as a Var via clojure/RT."
  [cfg]
  ;the lock flag is used to signal that the next
  ;string is to be resolved.

  (let [lock (atom 0)
        rt (u/cljrt<>)]
    (cw/postwalk
      #(if (== 1 @lock)
         (let [h (if (keyword? %) (c/kw->str %) %)]
           (reset! lock 0)
           (cond (c/hgl? h)
                 (do (c/debug "calling rt-var %s." h)
                     (u/var* rt h))
                 (or (var? h)
                     (nil? h)) h
                 :else
                 (c/raise! "Bad action %s!" %)))
         (if (or (= :$error %)
                 (= :$action %)) (do (reset! lock 1) %) %)) cfg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol JmxAPI
  (jmx-dereg [_ ^ObjectName nname] "")
  (^ObjectName jmx-reg [_ obj ^String domain ^String nname paths] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol Execvisor
  (has-plugin? [_ id] "")
  (get-plugin [_ id] "")
  (locale [_] "")
  (start-time [_] "")
  (pkey-chars [_] "")
  (pkey-bytes [_] "")
  (kill9! [_] "")
  (cljrt [_] "")
  (scheduler [_] "")
  (home-dir [_] "")
  (uptime [_] "")
  (dbapi?? [_] [_ id] "")
  (dbpool?? [_] [_ id] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn plugin<>

  "Create a Service."
  [exec id {:keys [enabled? $pluggable] :as cfg}]

  (when (and $pluggable
             (c/!false? enabled?))
    (some-> (u/call* (cljrt exec)
                     $pluggable
                     (c/vargs* Object exec id)) (c/init cfg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn dispatch

  ([evt]
   (dispatch evt nil))

  ([evt arg]
   (letfn
     [(err [plug evt]
        (let [ex (Exception. "No handler.")]
          (c/if-fn [e (get-in plug [:conf :$error])]
            (e evt ex)
            (do (c/exception ex)
                (c/error (str "event#%s dropped.") (c/id evt))))))]
     (let [plug (c/parent evt)
           ctr (c/parent plug)
           sc (scheduler ctr)
           clj (cljrt ctr)
           h (or arg
                 (get-in plug
                         [:conf :$action]))
           f (if (var? h) (var-get h) h)]
       (c/do#nil
         ;(c/debug "plug handler type = %s." (type h))
         ;(c/debug "plug handler func = %s." (type f))
         (c/debug "plug = %s, fn = %s." (c/id plug) f)
         (if-not (fn? f)
           (err plug evt)
           (let [f' #(try (f %1)
                          (catch Throwable _
                            (c/exception _)))]
             (p/run* sc f' [evt])
             (c/debug "dispatched %s => %s." (c/id evt) (c/id plug)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

