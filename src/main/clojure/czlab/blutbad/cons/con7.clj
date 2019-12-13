;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.cons.con7

  (:gen-class)

  (:require [czlab.table.core :as tbl]
            [czlab.blutbad.core :as b]
            [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.blutbad.cons.con1 :as c1]
            [czlab.blutbad.cons.con2 :as c2]
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
                 ["usage.testjce"] ["usage.testjce.desc"]
                 ["usage.help"] ["usage.help.desc"]))
       (apply u/rstr* rcb)
       (partition 2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- usage

  "Echo usage."
  [pod?]

  (let [walls ["" "        " ""]
        style {:middle ["" "" ""]
               :bottom ["" "" ""]
               :top ["" "" ""]
               :dash " "
               :body-walls walls
               :header-walls walls}
        rcb (b/get-rc-base)]
    (-> (b/banner) ansi/bold-yellow c/prn!!)
    (c/prn! "%s\n\n"
            (u/rstr rcb "blutbad.desc"))
    (c/prn! "%s\n"
            (u/rstr rcb "cmds.header"))
    ;; prepend blanks to act as headers
    (c/prn! "%s\n\n"
            (c/strim
              (with-out-str
                (-> (concat '(("" ""))
                            (get-cmd-info rcb pod?))
                    (tbl/table :style style)))))
    (c/prn! "%s\n\n" (u/rstr rcb "cmds.trailer"))
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
        cf (io/file home b/cfg-pod-cf)
        pod? (i/file-ok? cf)
        verStr (c/stror (some-> ver
                                (.getString "version")) "?")]
    (u/set-sys-prop! "blutbad.user.dir" (u/fpath home))
    (u/set-sys-prop! "blutbad.version" verStr)
    (b/set-rc-base! rcb)
    (try (if (empty? args)
           (u/throw-BadData "CmdError!"))
         (let [[f _] (->> (c/_1 args)
                          keyword
                          c1/blutbad-tasks)]
           (if (fn? f)
             (f (drop 1 args))
             (u/throw-BadData "CmdError!")))
         (catch Throwable _
           (if (is? DataError _)
             (usage pod?) (u/prn-stk _))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

