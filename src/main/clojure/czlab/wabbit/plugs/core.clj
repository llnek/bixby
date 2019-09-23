;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Core functions for all IO services."
      :author "Kenneth Leung"}

  czlab.wabbit.plugs.core

  (:require [czlab.basal.util :as u]
            [czlab.basal.log :as l]
            [czlab.basal.core :as c]
            [czlab.basal.proc :as p]
            [czlab.wabbit.core :as b]
            [czlab.basal.xpis :as po]
            [czlab.wabbit.xpis :as xp])

  (:import [java.util Timer TimerTask]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn error!
  [evt ex]
  (let [plug (xp/get-pluglet evt)
        e (xp/err-handler plug)]
    (if (var? e)
      (@e evt ex)
      (do (some-> ex l/exception)
          (l/error (str "event [%s] "
                        "%s dropped.") (b/gtid evt) (po/id plug))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- process-orphan
  ""
  ([evt] (process-orphan evt nil))
  ([evt ^Throwable e] (error! evt e)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro s2ms
  "Convert seconds to milliseconds."
  {:no-doc true}
  [s]
  `(let [t# ~s] (if (czlab.basal.core/spos? t#) (* 1000 t#) 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn dispatch!
  ""
  ([evt] (dispatch! evt nil))
  ([evt arg]
   (let [pid (str "disp#" (u/seqint2))
         {:keys [dispfn handler]} arg
         plug (xp/get-pluglet evt)
         ctr (po/parent plug)
         sc (xp/get-scheduler ctr)
         clj (xp/cljrt ctr)
         h (or handler
               (xp/user-handler plug))
         f (if (var? h) @h)]
     (c/do#nil
       (l/debug "plug = %s\narg = %s\ncb = %s." (b/gtid plug) arg h)
       (l/debug "#%s => %s :is disp!" (po/id evt) (po/id plug))
       (if-not (fn? f)
         (process-orphan evt)
         (p/run* sc
                 (or dispfn f)
                 (if dispfn [f evt] [evt])))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


