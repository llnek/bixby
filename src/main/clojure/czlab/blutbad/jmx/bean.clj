;; Copyright © 2013-202020, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.jmx.bean

  (:require [clojure.string :as cs]
            [czlab.basal.util :as u]
            [czlab.basal.core :as c]
            [czlab.basal.meta :as m])

  (:import [java.lang Exception IllegalArgumentException]
           [java.lang.reflect Field Method]
           [java.util Arrays]
           [javax.management
            AttributeList
            Attribute
            DynamicMBean
            MBeanException
            MBeanInfo
            MBeanAttributeInfo
            MBeanOperationInfo
            MBeanParameterInfo
            ReflectionException
            AttributeNotFoundException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord NameParams [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- to-string-name-params

  [nps]

  (let [{:keys [name params]} nps]
    (if (empty? params) name (str name "/" (cs/join "#" params)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- name-params<>

  ([name]
   (name-params<> name nil))

  ([name pms]
   (c/object<> NameParams
               :name name
               :params (or pms []))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord BFieldInfo [])
(defrecord BPropInfo[] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- mk-bfield-info

  [field getter? setter?]

  (c/object<> BFieldInfo
              :getter? getter?
              :setter? setter? :field field))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-prop-type

  [prop]

  (if-some [g (:getter prop)]
    (.getReturnType ^Method g)
    (let [ps (some-> ^Method
                     (:setter prop)
                     .getParameterTypes)] (if (c/one? ps) (c/_1 ps)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- is-prop-query?

  [prop]

  (if-some [^Method g (:getter prop)]
    (and (-> (.getName g)
             (cs/starts-with? "is"))
         (m/is-boolean? (.getReturnType g))) false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- mk-bprop-info

  [prop desc getr setr]

  (c/object<> BPropInfo
              :desc desc
              :name prop
              :getter getr
              :setter setr))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- throw-UnknownError

  [attr]

  (c/trap! AttributeNotFoundException
           (c/fmt "Unknown property %s" attr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- throw-BeanError

  [msg]

  (c/trap! MBeanException (c/exp! Exception ^String msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- assert-args

  [mtd ptypes n]

  (if (not= n (count ptypes))
    (u/throw-BadArg (str "\"" mtd "\" needs " n "args"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- maybe-get-prop-name

  ^String [^String mn]

  (let [pos (cond (or (cs/starts-with? mn "get")
                      (cs/starts-with? mn "set")) 3
                  (cs/starts-with? mn "is") 2
                  :else -1)]
    (if (< pos 0)
      ""
      (str (Character/toLowerCase (.charAt mn pos)) (subs mn (+ pos 1))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- mk-parameter-info

  [^Method mtd]

  (c/preduce<vec>
    #(let [[^Class t n] %2]
       (conj! %1 (MBeanParameterInfo.
                   (format "p%d" n) (.getName t) "")))
    (partition 2 (interleave
                   (vec (.getParameterTypes mtd)) (map inc (range))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- test-jmx-type

  "If primitive types?"
  ^Class [cz]

  (if (or (m/is-boolean? cz)
          (m/is-void? cz)
          (m/is-object? cz)
          (m/is-string? cz)
          (m/is-short? cz)
          (m/is-long? cz)
          (m/is-int? cz)
          (m/is-double? cz)
          (m/is-float? cz)
          (m/is-char? cz)) cz))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- test-jmx-types?

  "Make sure we are dealing with primitive types."
  [^Class rtype ptypes]

  (if (some #(nil? (test-jmx-type %))(seq ptypes))
    false
    (some? (test-jmx-type rtype))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- bean-attr-info<>

  ^MBeanAttributeInfo
  [{:keys [name desc getter setter] :as prop}]

  (MBeanAttributeInfo. name
                       (str (some-> ^Class
                                    (get-prop-type prop) .getName))
                       desc
                       (some? getter)
                       (some? setter)
                       (is-prop-query? prop)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- bean-field-info<>

  [^Field f]

  (let [fnm (.getName f)
        t (.getType f)]
    (MBeanAttributeInfo. fnm
                         (.getName t)
                         (str fnm " attribute")
                         true
                         true
                         (and (m/is-boolean? t)
                              (cs/starts-with? fnm "is")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- bean-op-info<>

  [^Method m]

  (let [t (.getReturnType m)
        mn (.getName m)]
    (MBeanOperationInfo. mn
                         (str mn " operation")
                         (->> (mk-parameter-info m)
                              (c/vargs MBeanParameterInfo))
                         (.getName t)
                         MBeanOperationInfo/ACTION_INFO)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-props2

  "Only deal with getters and setters."
  [^Method mtd propsBin]

  (let [ptypes (.getParameterTypes mtd)
        rtype (.getReturnType mtd)
        mn (.getName mtd)
        pname (maybe-get-prop-name mn)
        methodInfo (propsBin pname)]
    (cond (c/nichts? pname)
          propsBin
          (and (c/sw-any? mn ["get" "is"])
               (empty? ptypes))
          (if (nil? methodInfo)
            (->> (mk-bprop-info pname "" mtd nil)
                 (assoc! propsBin pname))
            (->> (assoc methodInfo :getter mtd)
                 (assoc! propsBin pname)))
          (and (cs/starts-with? mn "set")
               (c/one? ptypes))
          (if (nil? methodInfo)
            (->> (mk-bprop-info pname "" nil mtd)
                 (assoc! propsBin pname))
            (->> (assoc methodInfo :setter mtd)
                 (assoc! propsBin pname)))
      :else propsBin)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-props

  [cz]

  (let [props (c/preduce<map>
                #(handle-props2 %2 %1) (.getMethods ^Class cz))
        ba (c/preduce<vec>
             #(let [[k v] %2]
                (if-some [mt (test-jmx-type
                               (get-prop-type v))]
                  (conj! %1 (bean-attr-info<> v)) %1)) props)]
    [ba props]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-flds

  [cz]

  (let [dcls (.getDeclaredFields ^Class cz)
        flds (c/preduce<map>
               #(let [^Field f %2
                      fnm (.getName f)]
                  (if (.isAccessible f)
                    (->> (mk-bfield-info f true true) (assoc! %1 fnm)) %1)) dcls)
        rc (c/preduce<vec>
             #(let [^Field f %2]
                (if-not (.isAccessible f)
                  (conj! %1 (bean-field-info<> f)) %1)) dcls)]
    [rc flds]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-methods2

  [^Method m mtds rc]

  (let [ptypes (.getParameterTypes m)
        rtype (.getReturnType m)
        mn (.getName m)]
    (if (some? (test-jmx-types? rtype ptypes))
      [(assoc! mtds
               (->> (mapv (fn [^Class c]
                            (.getName c)) ptypes)
                    (name-params<> mn)) m)
       (conj! rc (bean-op-info<> m))]
      [mtds rc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- handle-methods

  [cz]

  (c/info "jmx-bean: processing methods for class: %s." cz)
  (loop [ms (.getMethods ^Class cz)
         mtds (transient {})
         rc (transient [])]
    (if (empty? ms)
      [(c/persist! rc) (c/persist! mtds)]
      (let [[m r] (handle-methods2 (c/_1 ms) mtds rc)]
        (recur (rest ms) m r)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn jmx-bean<>

  "Make a JMX bean from this object."
  {:tag DynamicMBean
   :arglists '([obj])}
  [^Object obj]
  {:pre [(some? obj)]}

  (let [cz (.getClass obj)
        [ps propsMap] (handle-props cz)
        [fs fldsMap] (handle-flds cz)
        [ms mtdsMap] (handle-methods cz)
        bi (MBeanInfo. (.getName cz)
                       (str "About: " cz)
                       (->> (concat ps fs)
                            (c/vargs MBeanAttributeInfo))
                       nil
                       (c/vargs MBeanOperationInfo ms)
                       nil)]
    (reify
      DynamicMBean
      (getAttribute [_ attr]
        (let [prop (propsMap attr)
              fld (fldsMap attr)]
          (cond (nil? prop)
                (do (if (or (nil? fld)
                            (not (:getter? fld)))
                      (throw-UnknownError attr))
                    (.get ^Field (:field fld) obj))
                (nil? (:getter prop))
                (throw-UnknownError attr)
                :else
                (-> ^Method (:getter prop)
                    (.invoke obj (object-array 0))))))
      (getAttributes [this attrs]
        (c/do-with [rcl (AttributeList.)]
          (doseq [^String nm (seq attrs)]
            (try (->> (.getAttribute this nm)
                      (Attribute. nm)
                      (.add rcl))
                 (catch Throwable e#
                   (c/exception e#)
                   (->> (.getMessage e#)
                        (Attribute. nm) (.add rcl)))))))
      (getMBeanInfo [_] bi)
      (setAttribute [_ attr]
        (let [v (.getValue attr)
              an (.getName attr)
              prop (propsMap an)
              fld (fldsMap an)]
          (cond (nil? prop)
                (do (if (or (nil? fld)
                            (not (:setter? fld)))
                      (throw-UnknownError an))
                    (.set ^Field (:field fld) obj v))
                (nil? (:setter prop))
                (throw-UnknownError an)
                :else
                (-> ^Method (:setter prop) (.invoke obj v)))))
      (setAttributes [this attrs]
        (c/do-with [rcl (AttributeList. (count attrs))]
          (doseq [^Attribute a (seq attrs)
                 :let [nn (.getName a)]]
            (try (.setAttribute this a)
                 (->> (.getAttribute this nn)
                      (Attribute. nn)
                      (.add rcl))
                 (catch Throwable e#
                   (c/exception e#)
                   (->> (.getMessage e#)
                        (Attribute. nn) (.add rcl)))))))
      (invoke [_ opName params sig]
        (if-some [^Method mtd (mtdsMap (name-params<>
                                         opName (into [] sig)))]
          (c/try! (c/debug "jmx-invoke: '%s'\n%s%s\n%s%s"
                           opName "(params) "
                           (seq params) "(sig) " (seq sig))
                  (if (empty? params)
                    (.invoke mtd obj (object-array 0))
                    (.invoke mtd obj params)))
          (throw-BeanError
            (format "Unknown operation '%s'." opName)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

