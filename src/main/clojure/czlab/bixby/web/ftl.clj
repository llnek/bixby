;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns czlab.bixby.web.ftl

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
(defn- x->ftl-model<>

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
       (doseq [[^String k v] (x->ftl-model<>
                               (or shared-vars {}))]
         (.setSharedVariable cfg k v))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn render->ftl

  "Render a FreeMarker template."
  {:arglists '([cfg path model]
               [cfg path model xref?])}

  ([cfg path model]
   (render->ftl cfg path model nil))

  ([cfg path model xref?]
   {:pre [(c/is? Configuration cfg)]}
   (c/do-with-str [out (StringWriter.)]
     (c/debug "about to render tpl: %s." path)
     (if-some [t (.getTemplate ^Configuration cfg ^String path)]
       (.process t
                 (if xref? (x->ftl-model<> model) model) out)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn load-template

  "Load and evaluate a FreeMarker template."
  {:arglists '([cfg tpath data])}
  [cfg tpath data]
  {:pre [(c/is? Configuration cfg)]}

  (let [ts (str "/" (c/triml tpath "/"))]
    {:data (XData. (render->ftl cfg ts data))
     :ctype (condp #(cs/ends-with? %2 %1) ts
              ".json" "application/json"
              ".xml" "application/xml"
              ".html" "text/html"
              ;else
              "text/plain")}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

