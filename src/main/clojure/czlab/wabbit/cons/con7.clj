;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.cons.con7

  (:gen-class)

  (:require [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.wabbit.core :as b]
            [czlab.basal.log :as l]
            [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [czlab.table.core :as tbl]
            [czlab.basal.core :as c :refer [is?]]
            [czlab.basal.str :as s]
            [czlab.wabbit.cons.con2 :as c2]
            [czlab.wabbit.cons.con1 :as c1])

  (:import [czlab.basal DataError]
           [java.io File]
           [java.util ResourceBundle List Locale]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)
(def ^:private c-rcb "czlab.wabbit/Resources")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-cmd-info
  [rcb pod?]
  (let [arr '(["usage.gen"] [ "usage.gen.desc"]
              ["usage.version"] [ "usage.version.desc"]
              ["usage.testjce"] ["usage.testjce.desc"]
              ["usage.help"] ["usage.help.desc"])
        arr (if pod?
              (concat '(["usage.debug"] ["usage.debug.desc"]
                        ["usage.start"] ["usage.start.desc"]
                        ["usage.stop"] ["usage.stop.desc"]) arr) arr)]
    (partition 2 (apply u/rstr* rcb arr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- usage
  [pod?]
  (let [walls ["" "        " ""]
        style {:middle ["" "" ""]
               :bottom ["" "" ""]
               :top ["" "" ""]
               :dash " "
               :body-walls walls
               :header-walls walls}
        rcb (b/get-rc-base)]
    (c/prn!! (ansi/bold-yellow (b/banner)))
    (c/prn! "%s\n\n" (u/rstr rcb "wabbit.desc"))
    (c/prn! "%s\n" (u/rstr rcb "cmds.header"))
    ;; prepend blanks to act as headers
    (c/prn! "%s\n\n"
            (s/strim
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
  "" [& args]
  (let [ver (u/load-resource b/c-verprops)
        rcb (u/get-resource c-rcb)
        [p1 p2 & _] args
        pod? (= "-domus" p1)
        verStr (or (some-> ver (.getString "version")) "?")]
    (u/sys-prop! "wabbit.version" verStr)
    (b/set-rc-base! rcb)
    (try (if (empty? args)
           (u/throw-BadData "CmdError!"))
         (let [pred #(and (or (= "-home" %1)
                              pod?) (s/hgl? %2))
               args (if (pred p1 p2) (drop 2 args) args)
               _ (->> (if (pred p1 p2) p2 (u/get-cwd))
                      io/file u/fpath
                      (u/sys-prop! "wabbit.user.dir"))
               cfg (b/slurp-conf
                     (c1/get-home-dir) b/cfg-pod-cf)
               [f _] (c1/wabbit-tasks (keyword (c/_1 args)))]
           (if (fn? f)
            (binding [c1/*config-object* cfg
                      c1/*pkey-object* (i/x->chars  (get-in cfg
                                                            [:info :digest]))]
              (f (drop 1 args)))
            (u/throw-BadData "CmdError!")))
         (catch Throwable _
           (if (is? DataError _) (usage pod?) (u/prn-stk _))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


