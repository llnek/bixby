;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns

  testcljc.util.win32ini

  (:require [cmzlabclj.xlib.util.core :as CU]
            [cmzlabclj.xlib.util.ini :as WI])

  (:use [clojure.test])

  (:import  [com.zotohlab.frwk.util IWin32Conf]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^IWin32Conf ^:private INIFILE (WI/ParseInifile (CU/ResUrl "com/zotohlab/frwk/util/sample.ini")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftest testutil-wi32ini

(is (= (count (.sectionKeys INIFILE)) 2))

(is (map? (.getSection INIFILE "operating systems")))
(is (map? (.getSection INIFILE "boot loader")))

(is (true? (.endsWith ^String (.getString INIFILE "boot loader" "default") "WINDOWS")))

(is (true? (= (.getLong INIFILE "boot loader" "timeout") 30)))



)

(def ^:private win32ini-eof nil)

;;(clojure.test/run-tests 'testcljc.util.win32ini)

