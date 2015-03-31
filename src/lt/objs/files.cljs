(ns lt.objs.files
  (:refer-clojure :exclude [open exists?])
  (:require [lt.object :as object]
            [lt.util.load :as load]
            [clojure.string :as string]
            [lt.util.js :refer [now]])
  (:require-macros [lt.macros :refer [behavior]]))

(def fpath (js/require "path"))
(def os (js/require "os"))
(def app (.-App (js/require "nw.gui")))
(def data-path (let [path (.-dataPath app)]
                 (if (array? path)
                   (first path)
                   path)))

(defn typelist->index [cur types]
  (let [full (map (juxt :name identity) types)
        ext (for [cur types
                  ext (:exts cur)]
              [ext (:name cur)])]
    {:types (into (:types cur {}) full)
     :exts (into (:exts cur {}) ext)}))

(defn join [& segs]
  (apply (.-join fpath) (filter string? (map str segs))))

(def ignore-pattern #"(^\..*)|\.class$|target/|svn|cvs|\.git|\.pyc|~|\.swp|\.jar|.DS_Store")

(declare files-obj)

(behavior ::file-types
                  :triggers #{:object.instant}
                  :type :user
                  :desc "Files: Associate file types"
                  :params [{:label "types"
                            :example "[{:exts [:wisp],\n  :mime \"text/x-clojurescript\",\n  :name \"Wisp\",\n  :tags [:editor.wisp]}]"}]
                  :reaction (fn [this types]
                              (object/merge! files-obj (typelist->index @files-obj types))))

(behavior ::file.ignore-pattern
                  :triggers #{:object.instant}
                  :type :user
                  :exclusive true
                  :desc "Files: Set ignore pattern"
                  :params [{:label "pattern"
                            :example "\"\\\\.git|\\\\.pyc\""}]
                  :reaction (fn [this pattern]
                              (set! ignore-pattern (js/RegExp. pattern))))


(def files-obj (object/create (object/object* ::files
                                              :tags [:files]
                                              :exts {}
                                              :types {})))

(def line-ending (.-EOL os))
(def separator (.-sep fpath))
(def available-drives #{})
(def pwd (.resolve fpath "."))

(when (= separator "\\")
  (.exec (js/require "child_process") "wmic logicaldisk get name"
         (fn [_ out _]
           (let [ds (rest (.split out #"\r\n|\r|\n"))
                 ds (map #(str (.trim %) separator) (remove empty? ds))]
             (set! available-drives (into #{} ds)))
           )))

(defn basename
  ([path] (.basename fpath path))
  ([path ext] (.basename fpath path ext)))

(defn get-roots []
  (if (= separator "\\")
    available-drives
    #{"/"}))

(defn get-file-parts [path]
  (let [filename (basename path)
        file-parts (string/split filename #"\.")]
    (loop [parts file-parts
           acc []]
      (if (empty? parts)
        acc
        (recur (rest parts) (conj acc (string/join "." parts)))))))

(defn ext [path]
  (subs (.extname fpath path) 1))

(defn without-ext [path]
  (let [i (.lastIndexOf path ".")]
    (if (> i 0)
      (subs path 0 i)
      path)))

(defn ext->type [ext]
  (let [exts (:exts @files-obj)
        types (:types @files-obj)]
    (-> exts (get ext) types)))

(defn ext->mode [ext]
  (:mime (ext->type ext)))

(defn path->type [path]
  (->> path
       get-file-parts
       (map #(ext->type (keyword %)))
       (remove nil?)
       first))

(defn path->mode [path]
  (->> path
       get-file-parts
       (map #(ext->mode (keyword %)))
       (remove nil?)
       first))

(defn determine-line-ending [text]
  (let [text (subs text 0 1000)
        rn (re-seq #"\r\n" text)
        n (re-seq #"[^\r]\n" text)]
    (cond
      (and rn n) line-ending
      (and (not rn) (not n)) line-ending
      (not n) "\r\n"
      :else "\n")))

(defn absolute? [path]
  (boolean (re-seq #"^[\\\/]|([\w]+:[\\\/])" path)))

;; --------------------------VFS-----------------------

(def fs (js/require "fs"))
(def wrench (load/node-module "wrench"))

(defn fs-for-path [path]
  (let [filesystem (when path (first (filter (fn [fs] ((@fs :accept) fs path))
                                             (object/by-tag :filesystem))))]
    (if filesystem (@filesystem :fs-type) :local)))

;; (defn fs-for-path [path]
;;       :local)

(defmulti exists? fs-for-path)
(defmethod exists? :local [path]
  (.existsSync fs path))


(defmulti stats fs-for-path)
(defmethod stats :local [path]
  (when (exists? path)
    (.statSync fs path)))

(defmulti dir? fs-for-path)
(defmethod dir? :local [path]
  (when (exists? path)
    (let [stat (.statSync fs path)]
      (.isDirectory stat))))

(defmulti file? fs-for-path)
(defmethod file? :local [path]
  (when (exists? path)
    (let [stat (.statSync fs path)]
      (.isFile stat))))

(defmulti writable? fs-for-path)
(defmethod writable? :local [path]
  (let [perm (-> (.statSync fs path)
                 (.mode.toString 8)
                 (js/parseInt 10)
                 (str))
        perm (subs perm (- (count perm) 3))]
    (#{"7" "6" "3" "2"} (first perm))))

(defmulti resolve (fn [base cur] (fs-for-path base)))
(defmethod resolve :local [base cur]
  (.resolve fpath base cur))

(defmulti real-path fs-for-path)
(defmethod real-path :local [c]
  (.realpathSync fs c))

(defmulti bomless-read fs-for-path)
(defmethod bomless-read :local [path]
  (let [content (.readFileSync fs path "utf-8")]
    (string/replace content "\uFEFF" "")))

(defmulti write-file (fn [path content] (fs-for-path path)))
(defmethod write-file :local [path content]
  (.writeFileSync fs path content))

(defmulti append-file (fn [path content] (fs-for-path path)))
(defmethod append-file :local [path content]
  (.appendFileSync fs path content))

(defmulti delete! fs-for-path)
(defmethod delete! :local [path]
  (if (dir? path)
    (.rmdirSyncRecursive wrench path)
    (.unlinkSync fs path)))

(declare save)
(declare open-sync)

(defmulti move! (fn [from to] (fs-for-path from)))
(defmethod move! :local [from to]
  (if (dir? from)
    (do
      (.copyDirSyncRecursive wrench from to)
      (.rmdirSyncRecursive wrench from))
    (do
      (save to (:content (open-sync from)))
      (delete! from))))

(defmulti copy (fn[from to] (fs-for-path from)))
(defmethod copy :local [from to]
  (if (dir? from)
    (.copyDirSyncRecursive wrench from to)
    (save to (:content (open-sync from)))))

(defmulti mkdir fs-for-path)
(defmethod mkdir :local [path]
  (.mkdirSync fs path))

(defmulti unwatch (fn [path alert] (fs-for-path path)))
(defmethod unwatch :local [path alert]
  (.unwatchFile fs path alert))

(defmulti watch (fn [path options alert] (fs-for-path path)))
(defmethod watch :local [path options alert]
  (.watchFile fs path options alert))

(defmulti stat fs-for-path)
(defmethod stat :local [path]
  (.statSync fs path))

(defmulti write-stream fs-for-path)
(defmethod write-stream :local [path]
  (.createWriteStream fs path))

(defmulti read-stream fs-for-path)
(defmethod read-stream :local [path]
  (.createReadStream fs path))

(defmulti read-dir fs-for-path)
(defmethod read-dir :local [path]
  (.readdirSync fs path))

;; --------------------------VFS-----------------------

(defn ->file|dir [path f]
  (if (dir? (str path separator f))
    (str f separator)
    (str f)))

(defn open [path cb]
  (try
    (let [content (bomless-read path)]
      ;;TODO: error handling
      (when content
        (let [e (ext path)]
          (cb {:content content
               :line-ending (determine-line-ending content)
               :type (or (path->mode path) e)})
          (object/raise files-obj :files.open content))
        ))
    (catch js/Error e
      (object/raise files-obj :files.open.error path e)
      (when cb (cb nil e)))
    (catch js/global.Error e
      (object/raise files-obj :files.open.error path e)
      (when cb (cb nil e)))
    ))

(defn open-sync [path]
  (try
    (let [content (bomless-read path)]
      ;;TODO: error handling
      (when content
        (let [e (ext path)]
          (object/raise files-obj :files.open content)
          {:content content
           :line-ending (determine-line-ending content)
           :type (or (ext->mode (keyword e)) e)}))
        )
    (catch js/Error e
      (object/raise files-obj :files.open.error path)
      nil)
    (catch js/global.Error e
      (object/raise files-obj :files.open.error path)
      nil)
    ))

(defn save [path content & [cb]]
  (try
    (write-file path content)
    (object/raise files-obj :files.save path)
    (when cb (cb))
    (catch js/global.Error e
      (object/raise files-obj :files.save.error path e)
      (when cb (cb e))
      )
    (catch js/Error e
      (object/raise files-obj :files.save.error path e)
      (when cb (cb e))
      )))

(defn append [path content & [cb]]
  (try
    (append-file path content)
    (object/raise files-obj :files.save path)
    (when cb (cb))
    (catch js/global.Error e
      (object/raise files-obj :files.save.error path e)
      (when cb (cb e))
      )
    (catch js/Error e
      (object/raise files-obj :files.save.error path e)
      (when cb (cb e))
      )))

(defn parent [path]
	(.dirname fpath path))

(defn next-available-name [path]
  (if-not (exists? path)
    path
    (let [ext (ext path)
          name (without-ext (basename path))
          p (parent path)]
      (loop [x 1
             cur (join p (str name x "." ext))]
        (if-not (exists? cur)
          cur
          (recur (inc x) (join p (str name (inc x) (when ext (str "." ext))))))))))

(defn ls
  ([path] (ls path nil))
  ([path cb]
   (try
     (let [fs (map (partial ->file|dir path) (read-dir path))]
       (if cb
         (cb fs)
         fs))
     (catch js/global.Error e
       (when cb
         (cb nil))
       nil))))

(defn ls-sync [path opts]
  (try
    (let [fs (remove #(re-seq ignore-pattern %) (map (partial ->file|dir path) (read-dir path)))]
      (cond
       (:files opts) (filter #(file? (join path %)) fs)
       (:dirs opts) (filter #(dir? (join path %)) fs)
       :else fs))
    (catch js/global.Error e
      nil)))

(defn full-path-ls [path]
  (try
    (doall (map (partial join path) (read-dir path)))
    (catch js/Error e
      (js/lt.objs.console.error e))
    (catch js/global.Error e
      (js/lt.objs.console.error e))))

(defn dirs [path]
  (try
    (filter dir? (map (partial join path) (read-dir path)))
    (catch js/Error e)
    (catch js/global.Error e)))

(defn home
  ([] (home nil))
  ([path]
   (let [h (if (= js/process.platform "win32")
             js/process.env.USERPROFILE
             js/process.env.HOME)]
     (join h (or path separator)))))

(defn lt-home
  ([] pwd)
  ([path]
   (join pwd path)))

(defn lt-user-dir [path]
  (if js/process.env.LT_USER_DIR
    (join js/process.env.LT_USER_DIR (or path ""))
    (join data-path path)))

(defn walk-up-find [start find]
  (let [roots (get-roots)]
    (loop [cur start
           prev ""]
      (if (or (empty? cur)
              (roots cur)
              (= cur prev))
        nil
        (if (exists? (join cur find))
          (join cur find)
          (recur (parent cur) cur))))))

(defn relative [a b]
  (.relative fpath a b))

(defn ->name|path [f & [rel]]
  (let [path (if rel
               (relative rel f)
               f)]
    [(.basename fpath f) path]))

(defn path-segs [path]
  (let [segs (.split path separator)
        segs (if (or (.extname fpath (last segs))
                     (empty? (last segs)))
               (butlast segs)
               segs)]
    (vec (map #(str % separator) segs))))

(defn filter-walk [func path]
  (loop [to-walk (dirs path)
         found (filterv func (full-path-ls path))]
    (if-not (seq to-walk)
      found
      (let [cur (first to-walk)
            neue (filterv func (full-path-ls cur))]
        (recur (concat (rest to-walk) (dirs cur)) (concat found neue))))))


