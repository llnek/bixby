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
           [czlab.xlib LifeCycle]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evt<>
  ""
  [^IoService co {:keys [fname fp]}]
  (let
    [eeid (str "file#" (seqint2))
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
      (->> (evt<> co {:fname orig
                      :fp cf
                      :action action})
           (dispatchEvent co)))))

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
  [co conf cfg0]
  (let
    [{:keys [recvFolder
             fmask
             targetFolder] :as c2}
     (merge conf cfg0)
     root (expandVars targetFolder)
     dest (expandVars recvFolder)
     ff (toFMask (str fmask))]
    (test-hgl "file-root-folder" root)
    (log/info
      (str "monitoring folder: %s\n"
           "rcv folder: %s") root (nsn dest))
    (let
      [obs (FileAlterationObserver. (io/file root) ff)
       c2 (merge c2 {:targetFolder root
                     :fmask ff
                     :recvFolder dest})
       mon (-> (s2ms (:intervalSecs c2))
               (FileAlterationMonitor.))]
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
      [c2 mon])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FilePicker
  ""
  [co {:keys [conf] :as spec}]
  (let
    [mee (keyword (juid))
     impl (muble<>)
     schedule
     #(let [_ %]
        (log/info "apache io monitor starting...")
        (some-> ^FileAlterationMonitor
                (.getv impl mee) (.start)))
     par (threadedTimer {:schedule schedule})]
    (reify LifeCycle
      (init [_ arg]
        (let [[c m] (init co conf arg)]
          (.copyEx impl c)
          (.setv impl mee m)))
      (config [_] (.intern impl))
      (start [_ _]
        ((:start par) (.intern impl)))
      (stop [_]
       (log/info "apache io monitor stopping...")
       (some-> ^FileAlterationMonitor
               (.getv impl mee) (.stop))
       (.unsetv impl mee))
      (parent [_] co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


