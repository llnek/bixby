;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.core

  (:require [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [clojure.walk :as cw]
            [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [czlab.basal.log :as l]
            [clojure.string :as cs]
            [czlab.basal.core :as c]
            [czlab.basal.proc :as p])

  (:import [java.util ResourceBundle Locale]
           [org.apache.commons.io FileUtils]
           [java.io File IOException]
           [org.apache.commons.lang3.text StrSubstitutor]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^String c-verprops "czlab/wabbit/version.properties")
(def ^String c-rcb-base "czlab.wabbit/Resources")
(def ^String en-rcprops  "Resources_en.properties")
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
(def ^String pod-cf  "pod.conf")
(def ^String cfg-pod-cf  (str dn-conf "/" pod-cf))
(def ^String cfg-pub-pages  (str dn-pub "/" dn-pages))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def evt-opts :____eventoptions)
(def jslot-cred :credential)
(def jslot-user :principal)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/defonce- rc-bundles-cache (atom {:base nil :rcbs {}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn del-rc-bundle!

  "Remove a resource bundle from the cache."
  [b]

  (swap! rc-bundles-cache update-in [:rcbs] dissoc b)
  rc-bundles-cache)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn put-rc-bundle!

  "Add a resource bundle to the cache."
  [b rc]

  (swap! rc-bundles-cache update-in [:rcbs] assoc b rc)
  rc-bundles-cache)

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

  (swap! rc-bundles-cache assoc :base base)
  rc-bundles-cache)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn banner

  "A ASCII banner."
  {:no-doc true :tag String} []

  (str  "              __   __   _ __ " "\n"
        " _    _____ _/ /  / /  (_) /_" "\n"
        "| |/|/ / _ `/ _ \\/ _ \\/ / __/" "\n"
        "|__,__/\\_,_/_.__/_.__/_/\\__/ " "\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-proc-dir

  "Get the current directory."
  ^File []

  (io/file (u/get-sys-prop "wabbit.user.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-sys-props

  "Expand system properties found in value."
  ^String [value]

  (if (c/nichts? value)
    value (StrSubstitutor/replaceSystemProperties ^String value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-env-vars

  "Expand environment vars found in value."
  ^String [value]

  (if (c/nichts? value)
    value (-> (System/getenv) StrSubstitutor. (.replace ^String value))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-vars

  "Replace all variables in value."
  ^String [value]

  (if (or (c/nichts? value)
          (nil? (cs/index-of value "${")))
    value
    (-> (cs/replace value
                    "${pod.dir}"
                    "${wabbit.user.dir}")
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
              (l/debug "[%s]\n%s." file))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the directory is readable & writable.
(defn precond-dir

  "Assert dir(s) are read-writeable?"
  [f & dirs]

  (c/let#true [base (get-rc-base)]
    (doseq [d (cons f dirs)]
      (->> (i/dir-read-write? d)
           (c/test-cond (u/rstr base "dir.no.rw" d))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn precond-file

  "Assert file(s) are readable?"
  [ff & files]

  (c/let#true [base (get-rc-base)]
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
      (u/throw-IOE (u/rstr (get-rc-base) "wabbit.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn expand-conf

  "Expand vars in this config object."
  [cfgObj]

  (expand-vars* cfgObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn slurp-conf

  "Parse config file."

  ([podDir conf]
   (slurp-conf podDir conf false))

  ([podDir conf expVars?]
   (let [f (io/file podDir conf)
         s (str "{\n" (i/slurp-utf8 f) "\n}")]
     (i/read-edn
       (if-not expVars?
         s
         (expand-vars (cs/replace s
                                  "${pod.dir}"
                                  "${wabbit.user.dir}")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn spit-conf

  "Write out config file."
  [podDir conf cfgObj]

  (let [f (io/file podDir conf)
        s (c/strim (i/fmt->edn cfgObj))]
    (i/spit-utf8 f
                 (if-not (c/wrapped? s "{" "}")
                   s
                   (-> (c/drop-head s 1) (c/drop-tail 1))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn prevar-cfg

  "Scan the config object looking for references to
  functions, supported markers are $error, $handler.
  For each one found, load it as a Var via clojure/RT."
  [cfg]

  (let [lock (atom 0)
        rt (u/cljrt<>)]
    (cw/postwalk
      #(if (= 1 @lock)
         (let [h (if (keyword? %) (c/kw->str %) %)]
           (reset! lock 0)
           (cond (c/hgl? h) (u/var* rt h)
                 (or (var? h)
                     (nil? h)) h
                 :else
                 (c/raise! "Bad handler: %s!" %)))
         (if (or (= :$error %)
                 (= :$handler %)) (do (reset! lock 1) %) %)) cfg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn delete-dir

  "Safely delete a folder."
  [dir]

  (c/try! (FileUtils/deleteDirectory (io/file dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn clean-dir

  "Safely clear a folder."
  [dir]

  (c/try! (FileUtils/cleanDirectory (io/file dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

