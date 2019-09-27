;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.plugs.mvc

  (:require [czlab.basal.xpis :as po]
            [clojure.walk :as cw]
            [czlab.basal.io :as i]
            [czlab.basal.log :as l]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.wabbit.core :as b]
            [czlab.niou.core :as cc]
            [czlab.niou.webss :as ss]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.wabbit.xpis :as xp]
            [czlab.nettio.resp :as nr]
            [czlab.wabbit.plugs.core :as pc])

  (:import [clojure.lang APersistentMap]
           [freemarker.template
            TemplateMethodModelEx
            TemplateBooleanModel
            TemplateCollectionModel
            TemplateDateModel
            TemplateHashModelEx
            TemplateNumberModel
            TemplateScalarModel
            TemplateSequenceModel
            TemplateMethodModel
            Configuration
            DefaultObjectWrapper]
           [java.net HttpCookie]
           [java.util Date]
           [czlab.basal XData]
           [java.io File Writer StringWriter]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol CljApi
  ""
  (x->clj [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-protocol CljApi
  TemplateCollectionModel
  (x->clj [_]
    (if-some [itr (.iterator _)]
      (loop [acc []]
        (if-not (.hasNext itr)
          acc
          (recur (conj acc
                       (x->clj (.next itr))))))))
  TemplateSequenceModel
  (x->clj [s]
    (for [n (range (.size s))] (x->clj (.get s n))))
  TemplateHashModelEx
  (x->clj [m]
    (zipmap (x->clj (.keys m)) (x->clj (.values m))))
  TemplateBooleanModel
  (x->clj [_] (.getAsBoolean _))
  TemplateNumberModel
  (x->clj [_] (.getAsNumber _))
  TemplateScalarModel
  (x->clj [_] (.getAsString _))
  TemplateDateModel
  (x->clj [_] (.getAsDate _))
  Object
  (x->clj [_]
    (u/throw-BadArg "Failed to convert %s" (class _))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- x->ftl<method>
  "Morph the func into a ftl method call."
  [func]
  (reify
    TemplateMethodModelEx
    (exec [_ args] (apply func (map x->clj args)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- skey
  "Sanitize the key."
  [[k v]]
  [(cs/replace (c/kw->str k) #"[$!#*+\-]" "_")  v])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- x->ftl<model>
  "Sanitize the model to be ftl compliant."
  [m]
  (cw/postwalk #(cond
                  (map? %) (c/map-> (map skey %))
                  (fn? %) (x->ftl<method> %) :else %) m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ftl-config<>
  "Create a FTL config."
  {:tag Configuration}
  ([root] (ftl-config<> root nil))
  ([root shared-vars]
   (c/do-with [cfg (Configuration.)]
     (let [dir (io/file root)]
       (assert (.exists dir)
               (c/fmt "Bad root dir %s." root))
       (l/info "freemarker root: %s." root)
       (doto cfg
         (.setDirectoryForTemplateLoading dir)
         (.setObjectWrapper (DefaultObjectWrapper.)))
       (doseq [[^String k v] (x->ftl<model>
                               (or shared-vars {}))]
         (.setSharedVariable cfg k v))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- render->ftl
  "Renders a template given by Configuration and a path
   using model as input and writes it to a output string.
   If xref?, x->ftl<model> is run on the model."
  {:tag String}
  ([path cfg model]
   (render->ftl path cfg model nil))
  ([path cfg model xref?]
   (c/do-with-str [out (StringWriter.)]
     (l/debug "about to render tpl: %s." path)
     (if-some [t (.getTemplate ^Configuration cfg ^String path)]
       (.process t
                 (if xref? (x->ftl<model> model) model) out)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn load-template
  "" [tpath cfg data]
  (let [ts (str "/" (c/triml tpath "/"))]
    {:data (-> (render->ftl ts cfg data) XData.)
     :ctype
     (cond (cs/ends-with? ts ".json") "application/json"
           (cs/ends-with? ts ".xml") "application/xml"
           (cs/ends-with? ts ".html") "text/html" :else "text/plain")}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- strip-url-crap??
  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg."
  ^String
  [^String path]
  (let [pos (cs/last-index-of path \/)]
    (if-not (c/spos? pos)
      path
      (let [p1 (cs/index-of path \? pos)
            p2 (cs/index-of path \& pos)
            p3 (cond (and (c/spos? p1)
                          (c/spos? p2))
                     (min p1 p2)
                     (c/spos? p1) p1
                     (c/spos? p2) p2 :else -1)]
        (if (c/spos? p3) (subs path 0 p3) path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- reply-static
  [{:keys [request] :as res} file]
  (let [plug (xp/get-pluglet request)
        cfg (xp/gconf plug)
        fp (io/file file)
        {:keys [uri]} request]
    (l/debug "serving file: %s." (u/fpath fp))
    (try (cc/reply-result
           (if (and fp
                    (.exists fp))
             (assoc res :body fp)
             (assoc res :status 404)))
         (catch Throwable _
           (l/error _ "get: %s." uri)
           (c/try! (-> res
                       (assoc :body nil)
                       (assoc :status 500) cc/reply-result))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn asset-loader
  "Load a file from the public folder."
  [{:keys [route] :as evt} res]
  (let [plug (xp/get-pluglet evt)
        {:as cfg
         :keys [file-access-check?]
         {:keys [public-dir]} :wsite}
        (xp/gconf plug)
        home-dir (u/fpath (-> plug
                              po/parent
                              xp/get-home-dir))
        mp (str (some-> route :info :mount))
        public-dir (b/expand-vars public-dir)
        pub-dir (io/file public-dir)
        fp (-> (reduce #(cs/replace-first
                          %1 "{}" %2)
                       mp (:groups route))
               strip-url-crap?? c/strim)
        ffile (io/file pub-dir fp)]
    (l/debug "request for asset: %s." ffile)
    (if (and (c/hgl? fp)
             (or (false? file-access-check?)
                 (cs/starts-with? (u/fpath ffile)
                                  (u/fpath pub-dir))))
      (reply-static res ffile)
      (do (l/warn "illegal uri access: %s." fp)
          (-> (assoc res :status 403) cc/reply-result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

