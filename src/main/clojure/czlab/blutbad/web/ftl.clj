;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.web.ftl

  (:require [clojure.java.io :as io]
            [clojure.walk :as cw]
            [clojure.string :as cs]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u])

  (:import [freemarker.template
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
           [java.util Date]
           [czlab.basal XData]
           [java.io File Writer StringWriter]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol CljApi
  (x->clj [_] "Turn something into clojure."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol ConfigAPI
  (load-template [_ tpath data] "")
  (render->ftl [_ path model]
               [_ path model xref?] ""))

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
(defn- x->ftl<model>

  "Sanitize the model to be ftl compliant."
  [m]

  (letfn
    [(skey [[k v]]
       [(cs/replace (c/kw->str k) #"[$!#*+\-]" "_") v])
     (x->ftl [func]
       (reify TemplateMethodModelEx
         (exec [_ args] (apply func (map x->clj args)))))]
    (cw/postwalk #(cond
                    (map? %) (c/map-> (map skey %))
                    (fn? %) (x->ftl %) :else %) m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ftl-config<>

  "Create a FTL config."
  {:tag Configuration}

  ([root]
   (ftl-config<> root nil))

  ([root shared-vars]
   (c/do-with [cfg (Configuration.)]
     (let [dir (io/file root)]
       (assert (.exists dir)
               (c/fmt "Bad root dir %s." root))
       (c/info "freemarker root: %s." root)
       (doto cfg
         (.setDirectoryForTemplateLoading dir)
         (.setObjectWrapper (DefaultObjectWrapper.)))
       (doseq [[^String k v] (x->ftl<model>
                               (or shared-vars {}))]
         (.setSharedVariable cfg k v))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(extend-protocol ConfigAPI
  Configuration
  (load-template [cfg tpath data]
    (let [ts (str "/" (c/triml tpath "/"))]
      {:data (XData. (render->ftl cfg ts data))
       :ctype (condp #(cs/ends-with? %2 %1) ts
                ".json" "application/json"
                ".xml" "application/xml"
                ".html" "text/html"
                ;else
                "text/plain")}))
  (render->ftl
    ([cfg path model]
     (render->ftl cfg path model nil))
    ([cfg path model xref?]
     (c/do-with-str [out (StringWriter.)]
       (c/debug "about to render tpl: %s." path)
       (if-some [t (.getTemplate cfg ^String path)]
         (.process t
                   (if xref? (x->ftl<model> model) model) out))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

