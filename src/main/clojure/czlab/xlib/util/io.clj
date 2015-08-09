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

(ns ^{:doc "Util functions related to stream/io"
      :author "kenl" }

  czlab.xlib.util.io

  (:require
    [czlab.xlib.util.core :refer [spos? try!]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as cstr])

  (:import
    [java.util.zip GZIPInputStream GZIPOutputStream]
    [java.io ByteArrayInputStream
     ByteArrayOutputStream DataInputStream
     DataInputStream DataOutputStream
     FileInputStream FileOutputStream
     CharArrayWriter OutputStreamWriter
     File InputStream InputStreamReader
     Closeable
     OutputStream Reader Writer]
    [java.nio ByteBuffer CharBuffer]
    [java.nio.charset Charset]
    [com.zotohlab.frwk.io XData XStream]
    [org.apache.commons.codec.binary Base64]
    [org.apache.commons.lang3 StringUtils]
    [org.apache.commons.io IOUtils]
    [org.xml.sax InputSource]
    [java.nio.charset Charset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _wd (atom (File. (System/getProperty "java.io.tmpdir"))))
(def ^:private _slimit (atom (* 4 1024 1024)))
(def ^:private ^chars HEX_CHS (.toCharArray "0123456789ABCDEF"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StreamLimit

  "Beyond this limit, data will be swapped out to disk (temp file)"

  ^long
  []
  @_slimit)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetStreamLimit! ""

  ^long
  [limit]

  (when (spos? limit)
    (reset! _slimit limit))
  @_slimit)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn WorkDir "" ^File [] @_wd)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetWorkDir! ""

  ^File
  [dir]

  (->> (doto (io/file dir) (.mkdirs))
       (reset! _wd))
  @_wd)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ByteOS

  "Make a byte array output stream"

  ^ByteArrayOutputStream
  []

  (ByteArrayOutputStream. (int 4096)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti WriteBytes "Write this long value out as byte[]" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToBytes

  "Convert char[] to byte[]"

  ^bytes
  [^chars chArray ^String encoding]

  (-> (Charset/forName encoding)
      (.encode (CharBuffer/wrap chArray))
      (.array)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToChars

  "Convert byte[] to char[]"

  ^chars
  [^bytes byteArray ^String encoding]

  (-> (Charset/forName encoding)
      (.decode (ByteBuffer/wrap byteArray))
      (.array)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadLong

  "a long by scanning the byte[]"

  [^bytes byteArray]

  (-> (ByteArrayInputStream. byteArray)
      (DataInputStream.  )
      (.readLong )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadInt

  "an int by scanning the byte[]"

  [^bytes byteArray]

  (-> (ByteArrayInputStream. byteArray)
      (DataInputStream.  )
      (.readInt )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod WriteBytes Integer

  ^bytes
  [nnum]

  (with-open [baos (ByteOS)]
    (doto (DataOutputStream. baos)
      (.writeInt (int nnum))
      (.flush))
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod WriteBytes Long

  ^bytes
  [nnum]

  (with-open [baos (ByteOS) ]
    (doto (DataOutputStream. baos)
      (.writeLong ^long nnum)
      (.flush))
    (.toByteArray baos)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Streamify

  "Wrapped these bytes in an input-stream"

  ^InputStream
  [^bytes bits]

  (when (some? bits)
    (ByteArrayInputStream. bits)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CloseQ

  "Quietly close this object"

  [obj]

  (when (instance? Closeable obj)
    (try! (.close ^Closeable obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HexifyChars

  "Turn bytes into hex chars"

  ^chars
  [^bytes bits]

  (let [len (if (nil? bits) 0 (* 2 (alength bits)))
        out (char-array len)]
    (loop [k 0 pos 0]
      (when-not (>= pos len)
        (let [n (bit-and (aget ^bytes bits k) 0xff) ]
          (aset-char out pos
                     (aget ^chars HEX_CHS (bit-shift-right n 4))) ;; high 4 bits
          (aset-char out (+ pos 1)
                     (aget ^chars HEX_CHS (bit-and n 0xf))) ;; low 4 bits
          (recur (inc k) (+ 2 pos)) )))
    out
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HexifyString

  "Turn bytes into hex string"

  ^String
  [^bytes bits]

  (when (some? bits)
    (String. (HexifyChars bits))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti OpenFile "Open this file path" class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Gzip

  "Gzip these bytes"

  ^bytes
  [^bytes bits]

  (when (some? bits)
    (let [baos (ByteOS)]
      (with-open [g (GZIPOutputStream. baos)]
        (.write g bits 0 (alength bits)))
      (.toByteArray baos))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Gunzip

  "Gunzip these bytes"

  ^bytes
  [^bytes bits]

  (when (some? bits)
    (IOUtils/toByteArray (GZIPInputStream. (Streamify bits)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResetStream!

  "Call reset on this input stream"

  [^InputStream inp]

  (try! (when (some? inp) (.reset inp)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod OpenFile String

  ^XStream
  [^String fp]

  (when (some? fp)
    (XStream. (io/file fp))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod OpenFile File

  ^XStream
  [^File f]

  (when (some? f) (XStream. f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FromGZB64

  "Unzip content which is base64 encoded + gziped"

  ^bytes
  [^String gzb64]

  (when (some? gzb64)
    (Gunzip (Base64/decodeBase64 gzb64))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ToGZB64

  "Zip content and then base64 encode it"

  ^String
  [^bytes bits]

  (when (some? bits)
    (Base64/encodeBase64String (Gzip bits))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Available

  "Get the available bytes in this stream"

  ;; int
  [^InputStream inp]

  (if (nil? inp)
    0
    (.available inp)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn TempFile ""

  ^File
  [ & [pfx sux] ]

  (let [pfx (or pfx "tmp-")
        sux (or sux ".dat")]
    (File/createTempFile
      (if (> (count pfx) 2) pfx "tmp-")
      (if (> (count pfx) 2) sux ".dat"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OpenTempFile

  "a Tuple(2) [ File, OutputStream? ]"

  []

  (let [fp (TempFile)]
    [fp (FileOutputStream. fp)]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyStream

  "Copy content from this input-stream to a temp file"

  ^File
  [^InputStream inp]

  (let [[^File fp ^OutputStream os]
        (OpenTempFile) ]
    (try
      (IOUtils/copy inp os)
      (finally
        CloseQ os))
    fp
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CopyBytes

  "Copy x number of bytes from the source input-stream"

  [^InputStream src ^OutputStream out bytesToCopy]

  (when (> bytesToCopy 0)
    (IOUtils/copyLarge src out 0 ^long bytesToCopy)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ResetSource!

  "Reset an input source"

  [^InputSource inpsrc]

  (when (some? inpsrc)
    (let [rdr (.getCharacterStream inpsrc)
          ism (.getByteStream inpsrc) ]
      (try! (when (some? ism) (.reset ism)) )
      (try! (when (some? rdr) (.reset rdr)) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeXData

  "a newly created XData"

  ^XData
  [ &[usefile] ]

  (if usefile
    (XData. (TempFile))
    (XData.)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SwapBytes

  "Swap bytes in buffer to file, returning a [File,OStream] tuple"

  [^ByteArrayOutputStream baos]

  (let [[^File fp ^OutputStream os]
        (OpenTempFile) ]
    (doto os
      (.write (.toByteArray baos))
      (.flush))
    (.close baos)
    [fp os]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SwapChars

  "Swap chars in writer to file, returning a [File,OWriter] tuple"

  [^CharArrayWriter wtr]

  (let [[^File fp ^OutputStream out]
        (OpenTempFile)
        w (OutputStreamWriter. out "utf-8") ]
    (doto w
      (.write (.toCharArray wtr))
      (.flush))
    (CloseQ wtr)
    [fp w]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slurp-bytes ""

  ^XData
  [^InputStream inp limit]

  (with-local-vars
    [os (ByteOS) fout nil]
    (loop [bits (byte-array 4096)
           cnt 0
           c (.read inp bits)]
      (if
        (< c 0)
        (try
          (if (some? @fout)
            (XData. @fout)
            (XData. @os))
          (finally
            (CloseQ @os)))
        ;;else
        (do
          (when (> c 0)
            (.write ^OutputStream @os bits 0 c)
            (if (and (nil? @fout)
                     (> (+ c cnt) limit))
              (let [[f o] (SwapBytes @os)]
                (var-set fout f)
                (var-set os o))))
          (recur bits
                 (+ c cnt)
                 (.read inp bits)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- slurp-chars ""

  ^XData
  [^Reader rdr limit]

  (with-local-vars
    [wtr (CharArrayWriter. (int 4096))
     fout nil]
    (loop [carr (char-array 4096)
           cnt 0
           c (.read rdr carr)]
      (if
        (< c 0)
        (try
          (if (some? @fout)
            (XData. @fout)
            (XData. @wtr))
          (finally
            (CloseQ @wtr)))
        ;;else
        (do
          (when (> c 0)
            (.write ^Writer @wtr carr 0 c)
            (if (and (nil? @fout)
                     (> (+ c cnt) limit))
              (let [[f w] (SwapChars @wtr)]
                (var-set fout f)
                (var-set wtr w))))
          (recur carr
                 (+ c cnt)
                 (.read rdr carr)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadBytes

  "Read bytes and return a XData"

  ^XData
  [^InputStream inp & [usefile]]

  (slurp-bytes inp (if usefile 1 (StreamLimit))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadChars

  "Read chars and return a XData"

  ^XData
  [^Reader rdr & [usefile]]

  (slurp-chars rdr (if usefile 1 (StreamLimit))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MorphChars

  "Convert these bytes to chars"

  (^chars
    [^bytes bits]
    (MorphChars bits (Charset/forName "utf-8")) )

  (^chars
    [^bytes bits ^Charset charSet]
    (when-not (nil? bits)
      (IOUtils/toCharArray (Streamify bits) charSet))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
