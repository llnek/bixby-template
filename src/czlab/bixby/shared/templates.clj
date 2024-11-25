;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Original: https://github.com/technomancy/leiningen/src/leiningen/new/templates.clj
;;
(ns czlab.bixby.shared.templates

  (:require [clojure.string :as cs]
            [clojure.java.io :as io])

  (:import [java.util Calendar]
           [java.io BufferedReader File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; can't set this to stencil.core/render-string here because
;; pulling in the stencil lib in this library will cause
;; classloading issues when used by bixby-template as a
;; leiningen template.
;; this function should return back a string if using stencil
(def ^:dynamic *renderer-fn* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro
  ^:private trap!
  [fmt & args] `(throw (Exception. (str (format ~fmt ~@args)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- fix-line-seps
  [s]
  (cs/replace s
              "\n"
              (if (System/getenv "LEIN_NEW_UNIX_NEWLINES")
                "\n" (System/getProperty "line.separator"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- slurp->lf

  "Read file and sanitize line feeds."
  [^BufferedReader r]

  (let [sb (StringBuilder.)]
    (loop [s (.readLine r)]
      (when s
        (.append sb s)
        (.append sb "\n")
        (recur (.readLine r)))) (str sb)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- slurp-resource

  "Read and sanitize a resource."
  [res]

  (-> (if (string? res)
        (io/resource res) res) io/reader slurp->lf fix-line-seps))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro sanitize

  "Replace - with _."
  [s]

  `(clojure.string/replace ~s "-" "_"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn name->path

  "Sanitize a file path."
  [s]

  (cs/replace (sanitize s) "." java.io.File/separator))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn sanitize-nsp

  "Sanitize a namespace."
  [s]

  (cs/join "."
           (remove empty? (cs/split s #"[/.]+"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn group-name

  "Get the group name."
  [s]

  (let [grpseq (butlast (cs/split (sanitize-nsp s) #"\."))]
    (if (seq grpseq)
      (->> grpseq (interpose ".") (apply str)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn year

  "Get current year."
  []

  (.get (Calendar/getInstance) Calendar/YEAR))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn date

  "Get current date as string."
  []

  (-> (java.text.SimpleDateFormat. "yyyy-MM-dd")
      (.format (java.util.Date.))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- res-path

  "Get resource path."
  [path]

  (let
    [p (str "czlab/bixby/shared/new/" path)] [p (io/resource p)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn renderer

  "Get the render function."

  ([name]
   (renderer name nil))

  ([name render-fn]
   (let [render (or render-fn *renderer-fn*)]
     (fn [template & [data]]
       (let [template (if data
                        (render template data) template)
             [p r] (res-path template)]
         (if r
           (if (nil? data)
             (io/reader r)
             (render (slurp-resource r) data))
           (trap! "Resource '%s' not found" p)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn raw-resourcer

  "Read a resource."
  [name]

  (fn [file]
    (let [[p r] (res-path file)]
      (if r
        (io/input-stream r)
        (trap! "Resource '%s' not found" p)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- template-path

  "Get template file."
  ^File [name path data]

  (io/file name (*renderer-fn* path data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn x->files

  "Generate output files from templates."
  [{:keys [to-dir dir force?]} {:as data :keys [name]} paths]

  (let [out (if (cs/blank? to-dir) dir to-dir)]
    (if (or (.equals "." out)
            (.mkdirs (io/file out)) force?)
      (doseq [path paths]
        (if (string? path)
          (.mkdirs (template-path dir path data))
          (let [[path content & options] path
                path (template-path dir path data)
                options (apply array-map options)]
            (.mkdirs (.getParentFile path))
            (io/copy content (io/file path))
            (when (:exec options)
              (.setExecutable path true)))))
      (trap! (str "Could not create directory "
                  dir
                  ". Maybe it already exists?"
                  "  See also :force or --force")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


