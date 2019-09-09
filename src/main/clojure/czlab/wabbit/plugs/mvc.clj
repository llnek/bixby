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

  (:require [clojure.walk :refer [postwalk]]
            [czlab.basal.proto :as po]
            [czlab.basal.log :as l]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.nettio.resp :as nr]
            [czlab.wabbit.core :as b]
            [czlab.niou.core :as cc]
            [czlab.niou.webss :as ss]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.str :as s]
            [czlab.wabbit.xpis :as xp]
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
           [java.io File Writer StringWriter]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol CljApi
  ""
  (->clj [_] "" ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-protocol CljApi
  TemplateCollectionModel
  (->clj [_]
    (if-some [itr (.iterator _)]
      (loop [acc []]
        (if-not (.hasNext itr)
          acc
          (recur (conj acc
                       (->clj (.next itr))))))))
  TemplateSequenceModel
  (->clj [s]
    (for [n (range (.size s))] (->clj (.get s n))))
  TemplateHashModelEx
  (->clj [m]
    (zipmap (->clj (.keys m)) (->clj (.values m))))
  TemplateBooleanModel
  (->clj [_] (.getAsBoolean _))
  TemplateNumberModel
  (->clj [_] (.getAsNumber _))
  TemplateScalarModel
  (->clj [_] (.getAsString _))
  TemplateDateModel
  (->clj [_] (.getAsDate _))
  Object
  (->clj [_]
    (u/throw-BadArg "Failed to convert %s" (class _))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- as-ftl-method
  [func]
  (reify
    TemplateMethodModelEx
    (exec [_ args] (apply func (map ->clj args)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- skey
  [[k v]]
  [(cs/replace (s/kw->str k) #"[$!#*+\-]" "_")  v])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- as-ftl-model
  [m]
  (postwalk #(cond
               (map? %) (into {} (map skey %))
               (fn? %) (as-ftl-method %) :else %) m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn gen-ftl-config
  ""
  {:tag Configuration}
  ([] (gen-ftl-config nil))
  ([{:keys [root shared] :or {shared {}}}]
   (c/do-with [cfg (Configuration.)]
     (if-some [dir (io/file root)]
       (when (.exists dir)
         (l/info "freemarker template source: %s." root)
         (doto cfg
           (.setDirectoryForTemplateLoading dir)
           (.setObjectWrapper (DefaultObjectWrapper.)))
         (doseq [[k v] (as-ftl-model shared)]
           (.setSharedVariable cfg ^String k v)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn render-ftl
  "Renders a template given by Configuration cfg and a path
   using model as input and writes it to out
   If out is not provided, returns a string
   If xrefModel? is true, asFtlModel is run on the model."
  {:tag String}

  ([cfg writer path model]
   (render-ftl cfg writer path model nil))

  ([cfg path model]
   (render-ftl cfg (StringWriter.) path model nil))

  ([^Configuration cfg ^Writer out
    ^String path model {:keys [xref-model?]
                        :or {xref-model? true}}]
   (l/debug "request to render tpl: %s." path)
   (let [t (.getTemplate cfg path)
         m (if xref-model? (as-ftl-model model) model)]
     (some-> t (.process m out))
     (str out))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn load-template
  "" [co tpath data]
  (let
    [ts (str "/" (s/triml tpath "/"))
     c (:ftl-cfg @co)
     out (render-ftl c ts data)]
    {:data (czlab.basal.XData. out)
     :ctype
     (cond (cs/ends-with? ts ".json") "application/json"
           (cs/ends-with? ts ".xml") "application/xml"
           (cs/ends-with? ts ".html") "text/html" :else "text/plain")}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-strip-url-crap
  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg."
  ^String
  [^String path]
  (let [pos (cs/last-index-of path \/)]
    (if (c/spos? pos)
      (let [p1 (cs/index-of path \? pos)
            p2 (cs/index-of path \& pos)
            p3 (cond (and (c/spos? p1)
                          (c/spos? p2))
                     (min p1 p2)
                     (c/spos? p1) p1
                     (c/spos? p2) p2 :else -1)]
        (if (c/spos? p3)
          (subs path 0 p3) path))
      path)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-static
  [res file]
  (let [evt (:request res)
        ch (:socket evt)
        co (xp/get-pluglet evt)
        cfg (xp/get-conf co)
        fp (io/file file)]
    (l/debug "serving file: %s." (u/fpath fp))
    (try (if (or (nil? fp)
                 (not (.exists fp)))
           (-> (assoc res :status 404) cc/reply-result )
           (-> (assoc res :body fp) cc/reply-result ))
         (catch Throwable Q
           (l/error Q "get: %s" (:uri evt))
           (c/try! (-> (assoc res :status 500)
                       (assoc :body nil)
                       cc/reply-result ))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn asset-loader
  "" [evt res]
  (let [co (xp/get-pluglet evt)
        {{:keys [public-root-dir]} :wsite :as cfg} (xp/get-conf co)
        home-dir (u/fpath (-> co po/parent xp/get-home-dir))
        r (:route evt)
        mp (str (some-> (:info r) :mount-point))
        ;;need to do this for testing only since expandvars
        ;;not called
        public-root-dir (b/expand-vars public-root-dir)
        pub-dir (io/file public-root-dir)
        fp (-> (reduce
                 #(cs/replace-first %1 "{}" %2) mp (:groups r))
               maybe-strip-url-crap s/strim)
        ffile (io/file pub-dir fp)
        check? (:file-access-check? cfg)]
    (l/info "request for asset: dir=%s, fp=%s." public-root-dir fp)
    (if (and (s/hgl? fp)
             (or (false? check?)
                 (cs/starts-with? (u/fpath ffile)
                                  (u/fpath pub-dir))))
      (get-static res ffile)
      (let [ch (:socket evt)]
        (l/warn "illegal uri access: %s." fp)
        (-> (assoc res :status 403) cc/reply-result )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

