;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.jmx.bean

  (:require
    [czlab.xlib.util.core
        :refer [ThrowBadArg MakeMMap notnil? tryc]]
    [czlab.xlib.util.str :refer [HasAny?]])

  (:require [czlab.xlib.util.logging :as log])
  (:use [czlab.xlib.util.meta])

  (:import
    [java.lang Exception IllegalArgumentException]
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.frwk.jmx NameParams]
    [java.lang.reflect Field Method]
    [java.util Arrays]
    [com.zotohlab.skaro.core Muble]
    [javax.management Attribute AttributeList
       AttributeNotFoundException
       DynamicMBean
       MBeanAttributeInfo
       MBeanException
       MBeanInfo
       MBeanOperationInfo
       MBeanParameterInfo
       ReflectionException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

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

  (let
    [impl (MakeMMap {:getr getr
                     :setr setr
                     :type nil}) ]
    (reify BPropInfo
      (getType [this]
        (let [^Method g (.getter this)
              ^Method s (.setter this) ]
          (if (some? g)
            (.getReturnType g)
            (when (some? s)
              (let [ps (.getParameterTypes s) ]
                (when (== 1 (count ps)) (first ps)))))))
      (getter [_] (.getv impl :getr))
      (getName [_] prop)
      (desc [_] descn)
      (setter [_] (.getv impl :setr))
      (setSetter [_ m] (.setv impl :setr m))
      (setGetter [_ m] (.setv impl :getr m))
      (isQuery [this]
        (if-let [^Method g (.getter this) ]
          (and (-> g (.getName)
                   (.startsWith "is"))
               (IsBoolean? (.getType this)))
          false)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwUnknownError ""

  [attr]

  (throw (AttributeNotFoundException. (str "Unknown property " attr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBeanError ""

  [^String msg]

  (throw (MBeanException. (Exception. msg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- assertArgs ""

  [mtd ptypes n]

  (when-not (== n (count ptypes))
    (ThrowBadArg (str "\"" mtd "\" needs " n "args."))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetPropName ""

  ^String
  [^String mn]

  (let
    [pos (cond
           (or (.startsWith mn "get")
               (.startsWith mn "set"))
           3
           (.startsWith mn "is")
           2
           :else -1) ]
    (if (< pos 0)
      ""
      (str (Character/toLowerCase (.charAt mn pos))
           (.substring mn (+ pos 1))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkParameterInfo ""

  [^Method mtd]

  (with-local-vars
    [ptypes (.getParameterTypes mtd)
     ctr 1
     rc (transient []) ]
    (doseq [^Class t (seq @ptypes) ]
      (var-set rc
               (->> (MBeanParameterInfo. (str "p" @ctr)
                                         (.getName t) "")
                    (conj! @rc)))
      (var-set ctr (inc @ctr)))
    (persistent! @rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testJmxType ""

  [^Class cz]

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
(defn- testJmxTypes

  "Make sure we are dealing with primitive types"

  [^Class rtype ptypes]

  (if (and (not (empty? ptypes))
           (true? (some #(if (testJmxType %) false true)
                        (seq ptypes))) )
    false
    (testJmxType rtype)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleProps ""

  [^Class cz]

  (with-local-vars
    [mtds (.getMethods cz)
     props (transient {})
     ba (transient []) ]
    (doseq [^Method mtd (seq @mtds) ]
      (let [ptypes (.getParameterTypes mtd)
            mn (.getName mtd)
            pname (maybeGetPropName mn)
            rtype (.getReturnType mtd)
            ^czlab.xlib.jmx.bean.BPropInfo
            methodInfo (props pname) ]
        (cond
          (or (.startsWith mn "get")
              (.startsWith mn "is"))
          (when (empty? ptypes)
            (if (nil? methodInfo)
              (var-set props
                       (->> (mkBPropInfo pname "" mtd nil)
                            (assoc! @props pname)))
              (.setGetter methodInfo mtd)))

          (.startsWith mn "set")
          (when (== 1 (count ptypes))
            (if (nil? methodInfo)
              (var-set props
                       (->> (mkBPropInfo pname "" nil mtd)
                            (assoc! @props pname)))
              (.setSetter methodInfo mtd)))

          :else nil)))
    (let [rc (persistent! @props) ]
      (doseq [[k ^czlab.xlib.jmx.bean.BPropInfo v] rc ]
        (when-let [^Class mt (testJmxType (.getType v)) ]
          (conj! @ba
                 (MBeanAttributeInfo. (.getName v)
                                      (.getName mt)
                                      (.desc v)
                                      (some? (.getter v))
                                      (some? (.setter v))
                                      (.isQuery v)))) )
      [ (persistent! @ba) rc ] )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFlds

  [^Class cz]

  (with-local-vars
    [dcls (.getDeclaredFields cz)
     flds (transient {})
     rc (transient []) ]
    (doseq [^Field field (seq @dcls) ]
      (let [fnm (.getName field) ]
        (when (.isAccessible field)
          (var-set flds
                   (->> (mkBFieldInfo field true true)
                        (assoc! @flds fnm)))
          (var-set rc
                   (->> (MBeanAttributeInfo.
                          fnm
                          (-> field
                              (.getType)
                              (.getName) )
                          (str fnm " attribute")
                          true
                          true
                          (and (.startsWith fnm "is")
                               (IsBoolean? (.getType field))))
                        (conj! @rc))))))
    [ (persistent! @rc) (persistent! @flds) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMethods ""

  [^Class cz]

  (with-local-vars
    [dcls (.getMethods cz)
     metds (transient {})
     rc (transient []) ]
    (log/info "jmx-bean: processing class: %s" cz)
    (doseq [^Method m (seq @dcls) ]
      (let [^Class rtype (.getReturnType m)
            ptypes (.getParameterTypes m)
            mn (.getName m) ]
        (cond
          (HasAny? mn [ "_QMARK" "_BANG" "_STAR" ])
          nil;;(log/info "JMX-skipping " mn)

          (testJmxTypes rtype ptypes)
          (let [pns (map #(.getName ^Class %) (seq ptypes))
                nameParams (NameParams. mn
                                        (into-array String pns))
                pmInfos (mkParameterInfo m) ]
            (var-set metds (assoc! @metds nameParams m))
            (log/info "jmx-adding method %s" mn)
            (var-set rc
                     (->> (MBeanOperationInfo.
                            mn
                            (str mn " operation")
                            (into-array MBeanParameterInfo pmInfos)
                            (.getName rtype)
                            MBeanOperationInfo/ACTION_INFO )
                          (conj! @rc))))
          :else
          (log/info "JMX-skipping %s" mn) )))
    [ (persistent! @rc) (persistent! @metds) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeJmxBean

  "Make a JMX bean from this object"

  ^DynamicMBean
  [^Object obj]

  (let
    [cz (.getClass obj)
     impl (MakeMMap)
     ;; we are ignoring props and fields
     propsMap {}
     fldsMap {}
     ps []
     fs []
     ;; just methods
     [ms mtdsMap]
     (handleMethods cz)
     bi (MBeanInfo.
          (.getName cz)
          (str "Information about: " cz)
          (into-array MBeanAttributeInfo (concat ps fs))
          nil
          (into-array MBeanOperationInfo ms)
          nil) ]
    (reify DynamicMBean
      (getAttribute [_ attrName]
        (let [^czlab.xlib.jmx.bean.BPropInfo
              prop (get propsMap attrName)
              ^czlab.xlib.jmx.bean.BFieldInfo
              fld (get fldsMap attrName) ]
          (cond
            (nil? prop)
            (do
              (when (or (nil? fld)
                        (not (.isGetter fld)))
                (throwUnknownError attrName))
              (let [^Field f (.field fld) ]
                (.get f obj)))

            (nil? (.getter prop))
            (throwUnknownError attrName)

            :else
            (-> ^Method (.getter prop)
                (.invoke obj (object-array 0))))))

      (getAttributes [this attrNames]
        (let [rcl (AttributeList.) ]
          (doseq [^String nm (seq attrNames) ]
            (try
              (->> (->> (.getAttribute this nm)
                        (Attribute. nm))
                   (.add rcl))
              (catch Throwable e#
                (log/error e# "")
                (->> (->> (.getMessage e#)
                          (Attribute. nm))
                     (.add rcl)))))
          rcl))

      (getMBeanInfo [_] bi)

      (setAttribute [_ a]
        (let [^Attribute attr a
              v (.getValue attr)
              an (.getName attr)
              ^czlab.xlib.jmx.bean.BPropInfo
              prop (propsMap an)
              ^czlab.xlib.jmx.bean.BFieldInfo
              fld (fldsMap an) ]
          (cond
            (nil? prop)
            (do
              (when (or (nil? fld)
                        (not (.isSetter fld)))
                (throwUnknownError an))
              (let [^Field f (.field fld) ]
                (.set f obj v)))

            (nil? (.setter prop))
            (throwUnknownError an)

            :else
            (-> ^Method (.setter prop)
                (.invoke obj v)))))

      (setAttributes [this attrs]
        (let [rcl (AttributeList. (count attrs)) ]
          (doseq [^Attribute a (seq attrs) ]
            (let [nn (.getName a) ]
              (try
                (.setAttribute this a)
                (.add rcl
                      (Attribute. nn
                                  (.getAttribute this nn)))
                (catch Throwable e#
                  (log/error e# "")
                  (.add rcl
                        (Attribute. nn
                                    (.getMessage e#)))))) )
          rcl))

      (invoke [_ opName params sig]
        (let [^Method mtd (mtdsMap (NameParams. opName sig)) ]
          (log/debug "JMX-invoking method %s%s%s%s%s"
                     opName
                     "\n(params) "
                     (seq params)
                     "\n(sig) " (seq sig))
          (when (nil? mtd)
            (throwBeanError (str "Unknown operation \"" opName "\"")))
          (tryc
            (if (empty? params)
              (.invoke mtd obj (object-array 0))
              (.invoke mtd obj params))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

