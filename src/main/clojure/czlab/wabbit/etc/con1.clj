;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.etc.con1

  (:require [czlab.twisty.codec :refer [strongPwd<> passwd<>]]
            [czlab.xlib.format :refer [writeEdnStr readEdn]]
            [czlab.wabbit.sys.core :refer [startViaCons]]
            [czlab.twisty.core :refer [assertJce]]
            [czlab.xlib.resources :refer [rstr]]
            [czlab.xlib.logging :as log]
            [czlab.antclj.antlib :as a]
            [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.wabbit.etc.con2]
        [czlab.xlib.guids]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.xlib.meta]
        [czlab.wabbit.etc.svcs]
        [czlab.wabbit.etc.core])

  (:import [org.apache.commons.io FileUtils]
           [czlab.wabbit.etc CmdError]
           [czlab.twisty IPassword]
           [java.util
            ResourceBundle
            Properties
            Calendar
            Map
            Date]
           [java.io File]
           [czlab.xlib I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bannerText
  ""
  {:no-doc true
   :tag String}
  []
  (str " __    __   ____  ____   ____   ____  ______"   "\n"
       " |  |__|  | /    ||    \\ |    \\ |    ||      |" "\n"
       " |  |  |  ||  o  ||  o  )|  o  ) |  | |      |" "\n"
       " |  |  |  ||     ||     ||     | |  | |_|  |_|" "\n"
       " |  '  '  ||  _  ||  O  ||  O  | |  |   |  |"   "\n"
       "  \\      / |  |  ||     ||     | |  |   |  |"   "\n"
       "   \\_/\\_/  |__|__||_____||_____||____|  |__|"   "\n"
       "\n"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelpXXX
  ""
  [pfx end]
  (let [rcb (I18N/base)]
    (dotimes [n end]
      (printf "%s\n" (rstr rcb
                           (str pfx (+ n 1)))))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Create
  "" [] (onHelpXXX "usage.new.d" 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onCreate
  "Create a new pod"
  {:no-doc true}
  [args]
  (if (> (count args) 1)
    (createPod (args 0) (args 1))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Podify
  "" [] (onHelpXXX "usage.podify.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- bundlePod
  "Bundle an app"
  [homeDir podDir outDir]
  (let [dir (mkdirs (io/file outDir))
        a (io/file podDir)]
    (->>
      (a/antZip
        {:destFile (io/file dir (str (.getName a) ".zip"))
         :basedir a
         :includes "**/*"})
      (a/runTasks* ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onPodify
  "Package the pod"
  {:no-doc true}
  [args]
  (if-not (empty? args)
    (bundlePod (getHomeDir)
               (getProcDir) (args 0))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Start
  "" [] (onHelpXXX "usage.start.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- runPodBg
  "Run the pod in the background"
  [homeDir podDir]
  (let
    [progW (io/file homeDir "bin/wabbit.bat")
     prog (io/file homeDir "bin/wabbit")
     tk (if (isWindows?)
          (a/antExec
            {:executable "cmd.exe"
             :dir podDir}
            [[:argvalues ["/C" "start" "/B"
                          "/MIN"
                          (fpath progW) "run"]]]))
     _ (if false
          (a/antExec
            {:executable (fpath prog)
             :dir podDir}
            [[:argvalues ["run" "bg"]]]))]
    (if (some? tk)
      (a/runTasks* tk)
      (trap! CmdError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onStart
  "Start and run the pod"
  {:no-doc true}
  [args]
  (let [home (getHomeDir)
        cwd (getProcDir)
        s2 (first args)]
    ;; background job is handled differently on windows
    (if (and (in? #{"-bg" "--background"} s2)
             (isWindows?))
      (runPodBg home cwd)
      (do
        (println (bannerText))
        (startViaCons home cwd)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Debug
  "" [] (onHelpXXX "usage.debug.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDebug
  "Debug the pod" {:no-doc true} [args] (onStart args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Demos
  "" [] (onHelpXXX "usage.demo.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onDemos
  "Generate demo apps"
  {:no-doc true}
  [args]
  (if-not (empty? args)
    (publishSamples (args 0))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genPwd
  ""
  [args]
  (let [c (first args)
        n (convLong (str c) 16)]
    (if (and (>= n 8)
             (<= n 32))
      (println (.text (strongPwd<> n)))
      (trap! CmdError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genWwid "" [] (println (wwid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genGuid "" [] (println (uuid<>)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHash
  "Generate a hash"
  [args]
  (if-not (empty? args)
    (->> (passwd<> (first args))
         (.hashed)
         (:hash )
         (println))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEncrypt
  "Encrypt the data"
  [args]
  (if (> (count args) 1)
    (->> (passwd<> (args 1) (args 0))
         (.encoded)
         (println))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onDecrypt
  "Decrypt the cypher"
  [args]
  (if (> (count args) 1)
    (->> (passwd<> (args 1) (args 0))
         (.text )
         (println ))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Generate
  "" [] (onHelpXXX "usage.gen.d" 8))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onGenerate
  "Generate a bunch of crypto stuff"
  {:no-doc true}
  [args]
  (let [c (first args)
        args (vec (drop 1 args))]
    (cond
      (contains? #{"-p" "--password"} c)
      (genPwd args)
      (contains? #{"-h" "--hash"} c)
      (onHash args)
      (contains? #{"-u" "--uuid"} c)
      (genGuid)
      (contains? #{"-w" "--wwid"} c)
      (genWwid)
      (contains? #{"-e" "--encrypt"} c)
      (onEncrypt args)
      (contains? #{"-d" "--decrypt"} c)
      (onDecrypt args)
      :else (trap! CmdError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-TestJCE
  "" [] (onHelpXXX "usage.testjce.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onTestJCE
  "Test if JCE (crypto) is ok"
  {:no-doc true}
  [args]
  (let [rcb (I18N/base)]
    (assertJce)
    (println (rstr rcb "usage.testjce.ok"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Version
  "" [] (onHelpXXX "usage.version.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onVersion
  "Show the version of system"
  {:no-doc true}
  [args]
  (let [rcb (I18N/base)]
    (->> (sysProp "wabbit.version")
         (rstr rcb "usage.version.o1")
         (printf "%s\n" ))
    (->> (sysProp "java.version")
         (rstr rcb "usage.version.o2")
         (printf "%s\n" ))
    (println)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanJars
  ""
  [^StringBuilder out ^File dir]
  (let [sep (sysProp "line.separator")]
    (reduce
      (fn [^StringBuilder b f]
         (.append b
                  (str "<classpathentry  "
                       "kind=\"lib\""
                       " path=\"" (fpath f) "\"/>"))
         (.append b sep))
      out
      (listFiles dir "jar"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- genEclipseProj
  ""
  [pdir]
  (let [ec (io/file pdir "eclipse.projfiles")
        poddir (io/file pdir)
        pod (.getName poddir)
        sb (strbf<>)]
    (mkdirs ec)
    (FileUtils/cleanDirectory ec)
    (writeFile
      (io/file ec ".project")
      (-> (resStr (str "czlab/wabbit/eclipse/"
                       "java"
                       "/project.txt"))
          (cs/replace "${APP.NAME}" pod)
          (cs/replace "${JAVA.TEST}"
                      (fpath (io/file poddir
                                      "src/test/java")))
          (cs/replace "${JAVA.SRC}"
                      (fpath (io/file poddir
                                      "src/main/java")))
          (cs/replace "${CLJ.TEST}"
                      (fpath (io/file poddir
                                      "src/test/clojure")))
          (cs/replace "${CLJ.SRC}"
                      (fpath (io/file poddir
                                      "src/main/clojure")))))
    (mkdirs (io/file poddir dn-build "classes"))
    (doall
      (map (partial scanJars sb)
           [(io/file (getHomeDir) dn-dist)
            (io/file (getHomeDir) dn-lib)
            (io/file poddir dn-target)]))
    (writeFile
      (io/file ec ".classpath")
      (-> (resStr (str "czlab/wabbit/eclipse/"
                       "java"
                       "/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-IDE
  "" [] (onHelpXXX "usage.ide.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onIDE
  "Generate IDE project files"
  {:no-doc true}
  [args]
  (if (and (not-empty args)
           (in? #{"-e" "--eclipse"} (args 0)))
    (genEclipseProj (getProcDir))
    (trap! CmdError)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onHelp-Service
  "" [] (onHelpXXX "usage.svc.d" 8))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onSvc
  ""
  ([id hint] (onSvc id hint nil))
  ([id hint svc]
   (let
     [cf (slurpXXXConf (getProcDir) cfg-pod-cf)
      root (:services cf)
      nw
      (if (< hint 0)
        (dissoc root id)
        (when-some
          [gist (:conf (*emitter-defs* svc))]
          (if (in? root id) (trap! CmdError))
          (assoc root id (assoc gist :service svc))))]
     (if (some? nw)
       (spitXXXConf (getProcDir) cfg-pod-cf nw)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onService
  ""
  {:no-doc true}
  [args]
  (if (< (count args) 2) (trap! CmdError))
  (let
    [id (keyword (args 1))
     cmd (args 0)
     [hint svc]
     (cond
       (in? #{"-r" "--remove"} cmd)
       [-1 "?"]
       (in? #{"-a" "--add"} cmd)
       (if (< (count args) 3)
         (trap! CmdError)
         [1 (args 2)])
       :else (trap! CmdError))
     t (case (keyword svc)
         :repeat :czlab.wabbit.io.loops/RepeatingTimer
         :files :czlab.wabbit.io.files/FilePicker
         :once :czlab.wabbit.io.loops/OnceTimer
         :tcp :czlab.wabbit.io.socket/Socket
         :web :czlab.wabbit.io.http/WebMVC
         :pop3 :czlab.wabbit.io.mails/POP3
         :imap :czlab.wabbit.io.mails/IMAP
         :http :czlab.wabbit.io.http/HTTP
         :jms :czlab.wabbit.io.jms/JMS
         :? nil
         (trap! CmdError))]
    (onSvc id hint t)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn onHelp-Help "" [] (trap! CmdError))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(declare getTasks)
(defn onHelp
  "Show help"
  {:no-doc true}
  [args]
  (let
    [c (keyword (first args))
     [_ h] ((getTasks) c)]
    (if (fn? h)
      (h)
      (trap! CmdError))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def
  ^:dynamic
  *wabbit-tasks*
  {:service [onService onHelp-Service]
   :new [onCreate onHelp-Create]
   :ide [onIDE onHelp-IDE]
   :podify [onPodify onHelp-Podify]
   :debug [onDebug onHelp-Debug]
   :help [onHelp onHelp-Help]
   :run [onStart onHelp-Start]
   :demos [onDemos onHelp-Demos]
   :generate [onGenerate onHelp-Generate]
   :testjce [onTestJCE onHelp-TestJCE]
   :version [onVersion onHelp-Version]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getTasks "" [] *wabbit-tasks*)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


