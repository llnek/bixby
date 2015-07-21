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

(ns ^{:doc "Ways to generate an unique id."
      :author "kenl" }

  czlabclj.xlib.util.guids

  (:require [czlabclj.xlib.util.core
             :refer
             [NextInt NowMillis TryC NewRandom]]
            [czlabclj.xlib.util.str :refer [nsb Left Right]]
            [czlabclj.xlib.util.io :refer [ReadInt ReadLong]])

  (:require [clojure.tools.logging :as log])

  (:import  [java.lang StringBuilder]
            [java.net InetAddress]
            [java.util UUID]
            [java.lang Math]
            [java.security SecureRandom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;pre-shuffle the chars in string.
;;"0123456789AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz"
(def ^:private ^String _SS "YcQnPuzVAvpi7taGj1XwoJbIK3smye96NlHrR2DZS0CUxkLF5O4g8fBTqMEdhW")
(def ^:private ^chars  _CHARS (.toCharArray _SS))
(def ^:private _UUIDLEN (.length _SS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^String LONG_MASK "0000000000")
(def ^:private ^String INT_MASK "00000")
;;(def ^:private LONG_MASK "0000000000000000")
;;(def ^:private INT_MASK "00000000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmt ""

  ^String
  [^String pad ^String mask]

  (let [mlen (.length mask)
        plen (.length pad) ]
    (if (>= mlen plen)
      (.substring mask 0 plen)
      (nsb (.replace (StringBuilder. pad) (- plen mlen) plen mask ) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtInt ""

  ^String
  [nm]

  (fmt INT_MASK (Integer/toHexString nm)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtLong ""

  ^String
  [nm]

  (fmt LONG_MASK (Long/toHexString nm)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- splitTime  ""

  []

  (let [s (fmtLong (NowMillis))
        n (.length s) ]
    [ (Left s (/ n 2))
      (Right s (max 0 (- n (/ n 2 )) )) ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeSetIP

  ^long
  []

  (TryC
    (let [neta (InetAddress/getLocalHost)
          b (.getAddress neta) ]
      (if (.isLoopbackAddress neta)
        (.nextLong (NewRandom))
        (if (== 4 (alength b))
          (long (ReadInt b))
          (ReadLong b))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private _IP (Math/abs (maybeSetIP)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewUUid "RFC4122, version 4 form."

  ^String
  []

  (nsb (UUID/randomUUID)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc MyOwnNewUUid

  "RFC4122, version 4 form."

  ^String
  []

  ;; At i==19 set the high bits of clock sequence as per rfc4122, sec. 4.1.5
  (let [rc (char-array _UUIDLEN)
        rnd (NewRandom) ]
    (dotimes [n (alength rc) ]
      (aset-char rc
                 n
                 (case n
                   (8 13 18 23) \-
                   (14) \4
                   (let [d (Double. (* (.nextDouble rnd) 16))
                         r (bit-or 0 (.intValue d))
                         pos (if (= n 19)
                               (bit-or (bit-and r 0x3) 0x8)
                               (bit-and r 0xf)) ]
                     (aget ^chars _CHARS pos))) ))
    (String. rc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn NewWWid "Return a new guid based on time and ip-address."

  ^String
  []

  (let [seed (.nextInt (NewRandom) (Integer/MAX_VALUE))
        ts (splitTime) ]
    (str (nth ts 0)
         (fmtLong _IP)
         (fmtInt seed)
         (fmtInt (NextInt))
         (nth ts 1))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

