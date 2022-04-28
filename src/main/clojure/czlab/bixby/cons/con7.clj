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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns czlab.bixby.cons.con7

  (:gen-class)

  (:require [czlab.table.core :as tbl]
            [czlab.bixby.core :as b]
            [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.bixby.cons.con1 :as c1]
            [czlab.bixby.cons.con2 :as c2]
            [czlab.basal.core :as c :refer [is?]])

  (:import [czlab.basal DataError]
           [java.io File]
           [java.util ResourceBundle List Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-cmd-info

  "Collect all cmdline usage messages."
  [rcb pod?]

  (->> (concat (if-not pod?
                 '()
                 '(["usage.debug"] ["usage.debug.desc"]
                   ["usage.start"] ["usage.start.desc"]
                   ["usage.stop"] ["usage.stop.desc"]))
               '(["usage.gen"] [ "usage.gen.desc"]
                 ["usage.version"] [ "usage.version.desc"]
                 ;;["usage.testjce"] ["usage.testjce.desc"]
                 ["usage.help"] ["usage.help.desc"]))
       (apply u/rstr* rcb)
       (partition 2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- usage

  "Echo usage."
  [pod?]

  (let [walls ["" "        " ""]
        rcb (b/get-rc-base)
        style {:middle ["" "" ""]
               :bottom ["" "" ""]
               :top ["" "" ""]
               :dash " "
               :body-walls walls
               :header-walls walls}]
    (-> (b/banner)
        ansi/bold-magenta c/prn!!)

    (c/prn!! "%s\n"
             (u/rstr rcb "bixby.desc"))
    (c/prn!! "%s"
             (u/rstr rcb "cmds.header"))
    ;; prepend blanks to act as headers
    (c/prn!! "%s\n"
             (c/strim
               (with-out-str
                 (-> (concat '(("" ""))
                             (get-cmd-info rcb pod?))
                     (tbl/table :style style)))))
    (c/prn!! "%s\n" (u/rstr rcb "cmds.trailer"))
    ;;the table module causes some agent stuff to hang
    ;;the vm without exiting, so shut them down
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main

  "Main function."
  [& args]

  (let [[options args] (u/parse-options args)
        ver (u/load-resource b/c-verprops)
        rcb (u/get-resource b/c-rcb-base)
        home (io/file (or (:home options)
                          (u/get-user-dir)))
        verStr (c/stror (some-> ver
                                (.getString "version")) "?")]
    (u/set-sys-prop! "bixby.user.dir" (u/fpath home))
    (u/set-sys-prop! "bixby.version" verStr)
    (b/set-rc-base! rcb)
    (let [cfg (if-some
                [f (c/try!
                     (b/get-conf-file))]
                (b/slurp-conf f))]
      (try
        (if (empty? args)
          (u/throw-BadData "CmdError!"))
        (let [pk (get-in cfg [:info :digest])
              [f _] (->> (c/_1 args)
                         keyword
                         c1/bixby-tasks)]
          (if (fn? f)
            (binding [c1/*pkey-object* pk
                      c1/*config-object* cfg]
              (f (drop 1 args)))
            (u/throw-BadData "CmdError!")))
        (catch Throwable _
          (if (is? DataError _)
            (usage cfg) (u/prn-stk _)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

