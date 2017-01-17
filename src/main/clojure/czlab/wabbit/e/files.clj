;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for FilePicker."
      :author "Kenneth Leung"}

  czlab.wabbit.io.files

  (:require [czlab.xlib.io :refer [mkdirs]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.io.loops]
        [czlab.wabbit.io.core])

  (:import [java.io FileFilter File IOException]
           [java.util Properties ResourceBundle]
           [czlab.wabbit.io IoService FileEvent]
           [org.apache.commons.io.filefilter
            SuffixFileFilter
            PrefixFileFilter
            RegexFileFilter
            FileFileFilter]
           [org.apache.commons.io.monitor
            FileAlterationListener
            FileAlterationMonitor
            FileAlterationObserver
            FileAlterationListenerAdaptor]
           [org.apache.commons.io FileUtils]
           [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Order of args must match
(defmethod ioevent<>
  ::FilePicker
  [^IoService co {:keys [fname fp]}]
  (let
    [eeid (seqint2)
     f (io/file fp)]
    (with-meta
      (reify FileEvent
        (checkAuthenticity [_] false)
        (originalFileName [_] fname)
        (source [_] co)
        (file [_] f)
        (id [_] eeid))
      {:typeid ::FileEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postPoll
  "Only look for new files"
  [^IoService co ^File f action]
  (let
    [{:keys [recvFolder]}
     (.config co)
     orig (.getName f)]
    (if-some
      [cf (if (and (not= action :FP-DELETED)
                   (some? recvFolder))
            (try!
              (doto->> (io/file recvFolder orig)
                       (FileUtils/moveFile f))))]
      (->> (ioevent<> co {:fname orig
                          :fp cf
                          :action action})
           (.dispatch co)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- toFMask
  ""
  ^FileFilter
  [^String mask]
  (cond
    (.startsWith mask "*.")
    (SuffixFileFilter. (.substring mask 1))
    (.endsWith mask "*")
    (PrefixFileFilter.
      (.substring mask
                  0
                  (dec (.length mask))))
    (> (.length mask) 0)
    (RegexFileFilter. mask)
    :else
    FileFileFilter/FILE))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init
  ""
  [^IoService co cfg0]
  (let
    [c2 (merge (.config co) cfg0)
     {:keys [recvFolder
             fmask
             targetFolder]} c2
     root (expandVars targetFolder)
     dest (expandVars recvFolder)
     ff (toFMask (str fmask))]
    (test-hgl "file-root-folder" root)
    (log/info
      (str "monitoring folder: %s\n"
           "rcv folder: %s") root (nsn dest))
    (->> (merge c2 {:targetFolder root
                    :recvFolder dest
                    :fmask ff})
         (.setv (.getx co) :emcfg))
    (let
      [obs (FileAlterationObserver. (io/file root) ff)
       mon (-> (s2ms (:intervalSecs c2))
               (FileAlterationMonitor. ))]
      (->>
        (proxy [FileAlterationListenerAdaptor][]
          (onFileCreate [f]
            (postPoll co f :FP-CREATED))
          (onFileChange [f]
            (postPoll co f :FP-CHANGED))
          (onFileDelete [f]
            (postPoll co f :FP-DELETED)))
        (.addListener obs ))
      (.addObserver mon obs)
      (doto (.getx co)
        (.setv :monitor mon)))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FilePicker
  ""
  [spec]
  (let
    [impl (muble<>)
     schedule
     (fn [_ _]
       (log/info "apache io monitor starting...")
       (some-> ^FileAlterationMonitor
               (.getv impl :monitor)
               (.start)))
     stop
     (fn [_]
       (log/info "apache io monitor stopping...")
       (some-> ^FileAlterationMonitor
               (.getv impl :monitor)
               (.stop))
       (.unsetv impl :monitor))
     par (ThreadedTimer)]
    (merge par
           {:schedule schedule
            :wakeup nil
            :init init
            :stop stop})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


