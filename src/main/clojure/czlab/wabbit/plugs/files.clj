;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc "Implementation for FilePicker."
    :author "Kenneth Leung"}

  czlab.wabbit.plugs.files

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.wabbit
             [core :as b]
             [xpis :as xp]]
            [czlab.wabbit.plugs
             [core :as pc]
             [loops :as pl]]
            [czlab.basal
             [util :as u]
             [io :as i]
             [log :as l]
             [xpis :as po]
             [core :as c :refer [n#]]])

  (:import [java.util Timer Properties ResourceBundle]
           [java.io FileFilter File IOException]
           [clojure.lang APersistentMap]
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
           [org.apache.commons.io FileUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord FileMsg []
  po/Idable
  (id [_] (:id _))
  xp/PlugletMsg
  (get-pluglet [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>
  [co fname fp action]
  (c/object<> FileMsg
              :file (io/file fp)
              :source co
              :original-fname fname
              :id (str "FileMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- post-poll
  "Only look for new files."
  [plug recvFolder f action]
  (let [orig (i/fname f)]
    (when-some
      [cf (if (and recvFolder
                   (not= action :FP-DELETED))
            (c/try! (c/doto->> (io/file recvFolder orig)
                               (FileUtils/moveFile ^File f))))]
      (pc/dispatch! (evt<> plug orig cf action)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- to-fmask
  ^FileFilter [mask]
  (cond (cs/starts-with? mask "*.")
        (SuffixFileFilter. (subs mask 1))
        (cs/ends-with? mask "*")
        (PrefixFileFilter.
          (subs mask 0 (- (n# mask) 1)))
        (not-empty mask)
        (RegexFileFilter. ^String mask)
        :else
        FileFileFilter/FILE))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2
  [conf cfg0]
  (let [{root :target-folder
         dest :recv-folder
         :keys [fmask]
         :as c2} (merge conf cfg0)
        ff (to-fmask (str fmask))]
    (assert (c/hgl? root)
            (c/fmt "Bad file-root-folder %s." root))
    (l/info (str "monitoring dir: %s\n"
                 "receiving dir: %s") root (c/nsn dest))
    (merge c2 {:target-folder root
               :fmask ff :recv-folder dest})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- file-mon<>
  ^FileAlterationMonitor [plug]
  (let [{:keys [target-folder recv-folder
                interval-secs ^FileFilter fmask] :as cfg}
        (xp/gconf plug)
        obs (-> (io/file target-folder)
                (FileAlterationObserver. fmask))]
    (c/do-with [mon (FileAlterationMonitor.
                      (pc/s2ms interval-secs))]
      (.addListener obs
                    (proxy [FileAlterationListenerAdaptor][]
                      (onFileCreate [f]
                        (post-poll plug recv-folder f :FP-CREATED))
                      (onFileChange [f]
                        (post-poll plug recv-folder f :FP-CHANGED))
                      (onFileDelete [f]
                        (post-poll plug recv-folder f :FP-DELETED))))
      (doto mon (.addObserver obs) .start))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet
  [plug _id spec]
  (let [impl (atom {:conf (:conf spec)
                    :info (:info spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :$handler]))
      (err-handler [_] (get-in @impl [:conf :$error]))
      (gconf [_] (:conf @impl))
      po/Hierarchical
      (parent [_] plug)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (swap! impl
               update-in
               [:conf]
               #(-> (init2 % arg)
                    b/expand-vars* b/prevar-cfg)) me)
      po/Finzable
      (finz [_] (po/stop _) _)
      po/Startable
      (start [_] (po/start _ nil))
      (start [me arg]
        (let [w #(swap! impl
                        assoc
                        :mon (file-mon<> me))]
          (l/info "apache io monitor starting...")
          (swap! impl
                 assoc
                 :ttask
                 (pl/cfg-timer (Timer. true)
                               w
                               (:conf @impl) false)) me))
      (stop [me]
        (l/info "apache io monitor stopping...")
        (u/cancel-timer-task! (:ttask @impl))
        (some-> ^FileAlterationMonitor (:mon @impl) .stop) me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def FilePickerSpec
  {:info {:name "File Picker"
          :version "1.0.0"}
   :conf {:$pluggable ::file-picker<>
          :target-folder "/home/dropbox"
          :recv-folder "/home/joe"
          :fmask ""
          :interval-secs 300
          :delay-secs 0
          :$handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn file-picker<>
  "Create a File Picker Pluglet."
  ([_ id spec]
   (pluglet _ id spec))
  ([_ id]
   (file-picker<> _ id FilePickerSpec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

