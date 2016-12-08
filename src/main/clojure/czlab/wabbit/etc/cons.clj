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
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.etc.cons

  (:gen-class)

  (:require [czlab.xlib.io :refer [dirRead?]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io]
            [czlab.table.core :as tbl])

  (:use [czlab.wabbit.etc.con2]
        [czlab.wabbit.etc.core]
        [czlab.xlib.resources]
        [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.consts]
        [czlab.wabbit.etc.con1])

  (:import [czlab.wabbit.etc CmdError]
           [czlab.xlib I18N]
           [java.io File]
           [java.util ResourceBundle List Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getCmdInfo
  ""
  [rcb]
  (partition
    2
    (rstr*
      rcb
      ["usage.new"] ["usage.new.desc"]
      ["usage.svc"] ["usage.svc.desc"]
      ["usage.podify"] ["usage.podify.desc"]
      ["usage.ide"] [ "usage.ide.desc"]
      ["usage.build"] [ "usage.build.desc"]
      ["usage.test"] [ "usage.test.desc"]

      ["usage.debug"] ["usage.debug.desc"]
      ["usage.start"] ["usage.start.desc"]

      ["usage.gen"] [ "usage.gen.desc"]
      ["usage.demo"] [ "usage.demo.desc"]
      ["usage.version"] [ "usage.version.desc"]

      ["usage.testjce"] ["usage.testjce.desc"]
      ["usage.help"] ["usage.help.desc"])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn usage
  ""
  []
  (let
    [walls ["" "   " ""]
     style {:middle ["" "" ""]
            :bottom ["" "" ""]
            :top ["" "" ""]
            :dash " "
            :header-walls walls
            :body-walls walls}
     rcb (I18N/base)]
    (println (bannerText))
    (printf "%s\n\n" (rstr rcb "wabbit.desc"))
    (printf "%s\n" (rstr rcb "cmds.header"))
    ;; prepend blanks to act as headers
    (printf "%s\n\n"
            (strim
              (with-out-str
                (-> (concat '(("" ""))
                            (getCmdInfo rcb))
                    (tbl/table :style style)))))
    (printf "%s\n" (rstr rcb "cmds.trailer"))
    (println)
    ;;the table module causes some agent stuff to hang
    ;;the vm without exiting, so shut them down
    (shutdown-agents)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main
  "Main Entry"
  [& args]
  (let [ver (loadResource C_VERPROPS)
        rcb (getResource C_RCB)
        home (first args)
        args (drop 1 args)]
    (I18N/setBase rcb)
    (if (and (hgl? home)
             (dirRead? (io/file home)))
      (do
        (sysProp! "wabbit.proc.dir" (fpath (getCwd)))
        (sysProp! "wabbit.home.dir" home)
        (sysProp! "wabbit.version"
                  (or (some-> ver
                              (.getString "version"))
                      "?"))
        (try
          (if (empty? args)(trap! CmdError))
          (let [[f _]
                (-> (keyword (first args))
                    (*wabbit-tasks* ))]
            (if (fn? f)
              (f (vec (drop 1 args)))
              (trap! CmdError)))
          (catch Throwable _
            (if (inst? CmdError _) (usage) (prtStk _)))))
      (usage))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


