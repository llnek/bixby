;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for FilePicker."
      :author "Kenneth Leung"}

  czlab.wabbit.plugs.files

  (:require [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.log :as l]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.str :as s]
            [czlab.basal.proto :as po]
            [czlab.wabbit.plugs.core :as pc]
            [czlab.wabbit.plugs.loops :as pl]
            [czlab.basal.core :as c :refer [n#]])

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
  (let [{:as c2
         :keys [fmask]
         dest :recv-folder
         root :target-folder} (merge conf cfg0)
        ff (to-fmask (str fmask))]
    (c/test-hgl "file-root-folder" root)
    (l/info (str "monitoring folder: %s\n"
                 "rcv folder: %s") root (s/nsn dest))
    (merge c2 {:target-folder root
               :fmask ff :recv-folder dest})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- file-mon<>
  ^FileAlterationMonitor [plug]
  (let [{:keys [target-folder recv-folder
                interval-secs ^FileFilter fmask] :as cfg}
        (xp/get-conf plug)
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
      (.addObserver mon obs)
      (.start mon))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet
  [plug _id spec]
  (let [impl (atom {:conf (:conf spec)
                    :info (:info spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :handler]))
      (get-conf [_] (:conf @impl))
      (err-handler [_]
        (or (get-in @impl
                    [:conf :error]) (:error spec)))
      po/Hierarchical
      (parent [me] plug)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (swap! impl
               (c/fn_1 (update-in ____1
                                  [:conf]
                                  #(b/prevar-cfg (init2 % arg))))))
      po/Finzable
      (finz [_] (po/stop _))
      po/Startable
      (start [_] (po/start _ nil))
      (start [me arg]
        (let [w (c/fn_0 (let [m (file-mon<> me)]
                          (swap! impl #(assoc % :mon m))))]
          (l/info "apache io monitor starting...")
          (swap! impl
                 #(assoc %
                         :ttask
                         (pl/cfg-timer (Timer. true) w (:conf @impl) false)))))
      (stop [me]
        (l/info "apache io monitor stopping...")
        (u/cancel-timer-task! (:ttask @impl))
        (some-> ^FileAlterationMonitor (:mon @impl) .stop)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def FilePickerSpec {:info {:name "File Picker"
                            :version "1.0.0"}
                     :conf {:$pluggable ::file-picker<>
                            :target-folder "/home/dropbox"
                            :recv-folder "/home/joe"
                            :fmask ""
                            :interval-secs 300
                            :delay-secs 0
                            :handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn file-picker<>
  ([_ id]
   (file-picker<> _ id FilePickerSpec))
  ([_ id spec]
   (pluglet _ id (update-in spec
                            [:conf] b/expand-vars-in-form))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

