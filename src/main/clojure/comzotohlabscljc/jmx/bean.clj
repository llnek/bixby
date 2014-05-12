;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.jmx.bean

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [ThrowBadArg MakeMMap notnil? TryC] ])
  (:use [comzotohlabscljc.util.str :only [HasAny?] ])
  (:use [comzotohlabscljc.util.meta :only [IsBoolean? IsVoid? IsObject?
                                       IsString? IsShort? IsLong?
                                       IsInt? IsDouble? IsFloat? IsChar? ] ])
  (:import (java.lang Exception IllegalArgumentException))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (com.zotohlabs.frwk.jmx NameParams))
  (:import (java.lang.reflect Field Method))
  (:import (java.util Arrays))
  (:import (javax.management Attribute AttributeList
                             AttributeNotFoundException
                             DynamicMBean
                             MBeanAttributeInfo
                             MBeanException
                             MBeanInfo
                             MBeanOperationInfo
                             MBeanParameterInfo
                             ReflectionException)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private BFieldInfo

  ""

  (isGetter [_] )
  (isSetter [_] )
  (field [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkBFieldInfo ""

  [^Field fld getr setr]

  (reify BFieldInfo
    (isGetter [_] getr)
    (isSetter [_] setr)
    (field [_] fld)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private BPropInfo

  ""

  (getType [_] )
  (desc [_] )
  (getter [_] )
  (getName [_] )
  (setter [_] )
  (setSetter [_ m] )
  (setGetter [_ m] )
  (isQuery [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkBPropInfo ""

  [^String prop ^String descn ^Method getr ^Method setr]

  (let [ impl (MakeMMap) ]
    (.setf! impl :getr getr)
    (.setf! impl :setr setr)
    (.setf! impl :type nil)
    (reify BPropInfo
      (getType [this]
        (let [ ^Method g (.getter this)
               ^Method s (.setter this) ]
          (if (notnil? g)
              (.getReturnType g)
              (if (nil? s)
                  nil
                  (let [ ps (.getParameterTypes s) ]
                    (if (== 1 (count ps)) (first ps) nil))))))
      (getter [_] (.getf impl :getr))
      (getName [_] prop)
      (desc [_] descn)
      (setter [_] (.getf impl :setr))
      (setSetter [_ m] (.setf! impl :setr m))
      (setGetter [_ m] (.setf! impl :getr m))
      (isQuery [this]
        (let [ ^Method g (.getter this) ]
          (if (nil? g)
            false
            (and (-> g (.getName)(.startsWith "is"))
                 (IsBoolean? (.getType this))))))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- unknownError ""

  [attr]

  (AttributeNotFoundException. (str "Unknown property " attr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- beanError ""

  [^String msg]

  (MBeanException. (Exception. msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- assertArgs ""

  [mtd ptypes n]

  (when (not (== n (count ptypes)))
    (ThrowBadArg (str "\"" mtd "\" needs " n "args."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetPropName ""

  ^String
  [^String mn]

  (let [ pos (cond
               (or (.startsWith mn "get")
                   (.startsWith mn "set"))
               3
               (.startsWith mn "is")
               2
               :else
               -1) ]
    (if (< pos 0)
        ""
        (str (Character/toLowerCase (.charAt mn pos))
             (.substring mn (+ pos 1))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkParameterInfo ""

  [^Method mtd]

  (with-local-vars [ ptypes (.getParameterTypes mtd)
                     ctr 1
                     rc (transient []) ]
    (doseq [ ^Class t (seq @ptypes) ]
      (var-set rc
               (conj! @rc (MBeanParameterInfo. (str "p" @ctr)
                                               (.getName t) "")))
      (var-set ctr (inc @ctr)))
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testJmxType ""

  [ ^Class cz]

  (if (or (IsBoolean? cz)
          (IsVoid? cz)
          (IsObject? cz)
          (IsString? cz)
          (IsShort? cz)
          (IsLong? cz)
          (IsInt? cz)
          (IsDouble? cz)
          (IsFloat? cz)
          (IsChar? cz))
    cz
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testJmxTypes ""

  [^Class rtype ptypes]

  (if (and (not (empty? ptypes))
           (true? (some (fn [^Class c] (if (testJmxType c) false true))
                   (seq ptypes))) )
    false
    (testJmxType rtype)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleProps ""

  [^Class cz mtds]

  (with-local-vars [ props (transient {}) ba (transient []) ]
    (doseq [ ^Method mtd (seq mtds) ]
      (let [ mn (.getName mtd)
             ptypes (.getParameterTypes mtd)
             rtype (.getReturnType mtd)
             pname (maybeGetPropName mn)
             ^comzotohlabscljc.jmx.bean.BPropInfo
             methodInfo (props pname) ]
        (cond
          (or (.startsWith mn "get")
              (.startsWith mn "is"))
          (if (== 0 (count ptypes))
            (if (nil? methodInfo)
              (var-set props
                       (assoc! @props pname (mkBPropInfo pname "" mtd nil)))
              (.setGetter methodInfo mtd)))

          (.startsWith mn "set")
          (if (== 1 (count ptypes))
            (if (nil? methodInfo)
              (var-set props
                       (assoc! @props pname (mkBPropInfo pname "" nil mtd)))
              (.setSetter methodInfo mtd)))
          :else nil)))
    (let [ rc (persistent! @props) ]
      (doseq [ [k ^comzotohlabscljc.jmx.bean.BPropInfo v] rc ]
        (when-let [ ^Class mt (testJmxType (.getType v)) ]
          (conj! @ba
                 (MBeanAttributeInfo.
                   (.getName v)
                   ;; could NPE here if type is nil!
                   (.getName mt)
                   (.desc v)
                   (notnil? (.getter v))
                   (notnil? (.setter v))
                   (.isQuery v)))) )
      [ (persistent! @ba) rc ] )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFlds

  [^Class cz]

  (with-local-vars [ flds (transient {})
                     rc (transient []) ]
    (doseq [ ^Field field (seq (.getDeclaredFields cz)) ]
      (let [ fnm (.getName field) ]
        (when (.isAccessible field)
          (var-set flds
                   (assoc! @flds fnm (mkBFieldInfo field true true)))
          (var-set rc
                   (conj! @rc
                     (MBeanAttributeInfo.
                       fnm
                       (-> field (.getType)(.getName) )
                       (str fnm " attribute")
                       true
                       true
                       (and (.startsWith fnm "is")
                            (IsBoolean? (.getType field)))))))))
    [ (persistent! @rc)(persistent! @flds) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMethods ""

  [^Class cz mtds]

  (log/info "jmx-bean: processing class: " cz)
  (with-local-vars [ metds (transient {}) rc (transient []) ]
    (doseq [ ^Method m (seq mtds) ]
      (let [ ^Class rtype (.getReturnType m)
             ptypes (.getParameterTypes m)
             mn (.getName m) ]
        (cond
          (HasAny? mn [ "_QMARK" "_BANG" "_STAR" ])
          (log/info "jmx-skipping " mn)
          (testJmxTypes rtype ptypes)
          (let [ pns (map (fn [^Class c] (.getName c)) (seq ptypes))
                 nameParams (NameParams. mn (into-array String pns))
                 pmInfos (mkParameterInfo m) ]
            (var-set metds (assoc! @metds nameParams m))
            (log/info "jmx-adding method " mn)
            (var-set rc
                     (conj! @rc (MBeanOperationInfo.
                        mn
                        (str mn " operation")
                        (into-array MBeanParameterInfo pmInfos)
                        (.getName rtype)
                        MBeanOperationInfo/ACTION_INFO ))))

          :else
          (log/info "jmx-skipping " mn) )))
    [ (persistent! @rc) (persistent! @metds)]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeJmxBean "Make a JMX bean from this object."

  [^Object obj]

  (let [ impl (MakeMMap) cz (.getClass obj)
         ms (.getMethods cz)
         ;;[ps propsMap] (handleProps cz ms)
         ps [] propsMap {}
         ;;[fs fldsMap] (handleFlds cz)
         fs [] fldsMap {}
         [ms mtdsMap] (handleMethods cz ms)
         bi (MBeanInfo. (.getName cz)
                        (str "Information about: " cz)
                        (into-array MBeanAttributeInfo (concat ps fs))
                        nil
                        (into-array MBeanOperationInfo ms)
                        nil) ]
    (reify DynamicMBean
      (getMBeanInfo [_] bi)
      (getAttribute [_ attrName]
        (let [ ^comzotohlabscljc.jmx.bean.BPropInfo
               prop (get propsMap attrName)
               ^comzotohlabscljc.jmx.bean.BFieldInfo
               fld (get fldsMap attrName) ]
          (cond
            (nil? prop)
            (do
              (when (or (nil? fld)
                        (not (.isGetter fld)))
                (throw (unknownError attrName)))
              (let [ ^Field f (.field fld) ]
                (.get f obj)))

            (nil? (.getter prop))
            (throw (unknownError attrName))

            :else
            (let [ ^Method f (.getter prop) ]
              (.invoke f obj (into-array Object []))))))

      (getAttributes [this attrNames]
        (let [ rcl (AttributeList.) ]
          (doseq [ ^String nm (seq attrNames) ]
            (try
              (.add rcl (Attribute. nm (.getAttribute this nm)))
              (catch Throwable e#
                (log/error e# "")
                (.add rcl (Attribute. nm (.getMessage e#))))))
          rcl))

      (setAttribute [_ attr]
        (let [ v (.getValue ^Attribute attr)
               an (.getName ^Attribute attr)
               ^comzotohlabscljc.jmx.bean.BPropInfo
               prop (get propsMap an)
               ^comzotohlabscljc.jmx.bean.BFieldInfo
               fld (get fldsMap an) ]
          (cond
            (nil? prop)
            (do
              (when (or (nil? fld)
                        (not (.isSetter fld)))
                (throw (unknownError an)))
              (let [ ^Field f (.field fld) ]
                (.set f obj v)))

            (nil? (.setter prop))
            (throw unknownError an)

            :else
            (let [ ^Method f (.setter prop) ]
              (.invoke f obj v)))))

      (setAttributes [this attrs]
        (let [ rcl (AttributeList. (count attrs)) ]
          (doseq [ ^Attribute a (seq attrs) ]
            (let [ nn (.getName a) ]
              (try
                (.setAttribute this a)
                (.add rcl (Attribute. nn (.getAttribute this nn)))
                (catch Throwable e#
                  (log/error e# "")
                  (.add rcl (Attribute. nn (.getMessage e#)))))) )
          rcl))

      (invoke [_ opName params sig]
        (let [ ^Method mtd (get mtdsMap (NameParams. opName sig)) ]
          (log/debug "jmx-invoking method " opName
            "\n(params) " (seq params)
            "\n(sig) " (seq sig))
          (when (nil? mtd)
            (throw (beanError (str "Unknown operation \"" opName "\""))))
          (TryC
            (if (empty? params)
              (.invoke mtd obj (into-array Object []))
              (.invoke mtd obj params)))))

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private bean-eof nil)

