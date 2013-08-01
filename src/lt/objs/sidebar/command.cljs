(ns lt.objs.sidebar.command
  (:require [lt.object :as object]
            [lt.objs.context :as ctx]
            [lt.objs.sidebar :as sidebar]
            [lt.objs.command :as cmd]
            [lt.objs.app :as app]
            [lt.objs.keyboard :as keyboard]
            [lt.util.load :as load]
            [lt.util.dom :as dom]
            [lt.util.cljs :refer [->dottedkw]]
            [clojure.string :as string]
            [crate.core :as crate]
            [crate.binding :refer [subatom bound map-bound computed]])
  (:require-macros [lt.macros :refer [defui]]))

(load/js "core/node_modules/lighttable/util/fuzzy.js" :sync)

;**********************************************************
;; options input
;;**********************************************************

(object/behavior* ::op-select!
                  :triggers #{:select!}
                  :reaction (fn [this idx]
                              (let [input (object/->content this)]
                                (object/raise this :select (dom/val input))
                                (object/raise this :selected))))

(object/behavior* ::op-clear!
                  :triggers #{:clear!}
                  :reaction (fn [this]
                              (let [input (object/->content this)]
                                (dom/val input "")
                                (object/raise this :change! ""))))

(object/behavior* ::op-focus!
                  :triggers #{:focus!}
                  :reaction (fn [this]
                              (let [input (object/->content this)]
                                (dom/focus input)
                                (.select input))))


(defn ->value [{:keys [value]}]
  (if-not value
    ""
    value))

(defn input->value [this]
  (dom/val (object/->content this)))

(defui op-input [this]
  [:input.option {:type "text" :placeholder (bound this :placeholder) :value (bound this ->value)}]
  :focus (fn [e]
           (println "in options")
           (ctx/in! :options-input this)
           (object/raise this :active))
  :blur (fn [e]
          (ctx/out! :options-input this)
          (object/raise this :inactive))
  :keyup (fn [e]
           (this-as me
                    (object/raise this :change! (dom/val me)))
           ))

(object/object* ::options-input
                :tags #{:options-input}
                :placeholder "search"
                :init (fn [this opts]
                        (object/merge! this opts)
                        (op-input this)))

(defn options-input [opts]
  (let [lst (object/create ::options-input opts)]
    (object/raise lst :refresh!)
    lst))

;;**********************************************************
;; filter list
;;**********************************************************

(defn input-val [this]
  (-> (dom/$ :input (object/->content this))
      (dom/val)))

(defn set-val [this v]
  (-> (dom/$ :input (object/->content this))
      (dom/val v)))

(defn set-and-select [this v]
  (set-val this v)
  (object/raise this :change! v))

(defn current-selected [this]
  (let [cur (indexed-results @this)
        cnt (count cur)
        idx (:selected @this)
        i (mod idx (if (> cnt (:size @this)) (:size @this) cnt))]
    (when (> cnt 0)
      (aget (aget cur i) 0))))

(object/behavior* ::move-selection
                  :triggers #{:move-selection}
                  :reaction (fn [this dir]
                              (object/update! this [:selected] + dir)
                              (object/raise this :refresh!)
                              ))

(object/behavior* ::set-selection!
                  :triggers #{:set-selection!}
                  :reaction (fn [this idx]
                              (object/merge! this {:selected idx})
                              (object/raise this :refresh!)
                              ))

(object/behavior* ::change!
                  :triggers #{:change!}
                  :reaction (fn [this v]
                              (when-not (= (:search @this) v)
                                (object/merge! this {:selected 0
                                                     :search v})
                                (object/raise this :refresh!))))

(object/behavior* ::escape!
                  :triggers #{:escape!}
                  :reaction (fn [this]
                              (object/raise this :inactive)
                              (exec! :close-sidebar)
                              (exec! :focus-last-editor)))

(object/behavior* ::options-escape!
                  :triggers #{:escape!}
                  :reaction (fn [this]
                              (println "escaping")
                              (object/raise sidebar-command :cancel!)))

(object/behavior* ::set-on-select
                  :triggers #{:select}
                  :reaction (fn [this thing]
                              (when (:set-on-select @this)
                                (set-val this ((:key @this) thing)))))

(object/behavior* ::select!
                  :triggers #{:select!}
                  :reaction (fn [this idx]
                              (let [cur (indexed-results @this)
                                    cnt (count cur)
                                    idx (or idx (:selected @this))
                                    i (mod idx (if (> cnt (:size @this)) (:size @this) cnt))]
                                (if (> cnt 0)
                                  (do
                                    (object/raise this :select (aget (aget cur i) 0))
                                    (object/raise this :selected))
                                  (object/raise this :select-unknown (input-val this)))
                                )))

(object/behavior* ::filter-active
                  :triggers #{:active}
                  :reaction (fn [this]
                              (ctx/in! :filter-list.input this)))

(object/behavior* ::filter-inactive
                  :triggers #{:inactive}
                  :reaction (fn [this]
                              (ctx/out! :filter-list.input)))

(object/behavior* ::clear!
                  :triggers #{:clear!}
                  :reaction (fn [this]
                              (let [input (dom/$ :input (object/->content this))]
                                (dom/val input "")
                                (object/raise this :change! ""))))

(object/behavior* ::focus!
                  :triggers #{:focus!}
                  :reaction (fn [this]
                              (let [input (dom/$ :input (object/->content this))]
                                (dom/focus input)
                                (.select input))))

(object/behavior* ::update-lis
                  :triggers #{:refresh!}
                  :reaction (fn [this]
                              (object/merge! this {:cur (indexed-results @this)})
                              (fill-lis @this
                                        (:cur @this))))


(defui input [this]
  [:input.search {:type "text" :placeholder (bound this :placeholder)}]
  :focus (fn [e]
           (object/raise this :active))
  :blur (fn [e]
          (object/raise this :inactive))
  :keyup (fn [e]
           (this-as me
                    (object/raise this :change! (dom/val me)))
           ))

(defn ->items [items]
  (cond
   (satisfies? IDeref items) @items
   (fn? items) (items)
   :else items))

(defn score-sort [x y]
  (- (aget y 3) (aget x 3)))

(defn score-sort2 [x y]
  (- (.-score (aget y 4)) (.-score (aget x 4))))

(defn indexed-results [{:keys [search size items key size]}]
  (let [items (apply array (->items items))
        map-func3 #(array % (key %) (js/fastScore (key %) search) nil nil)
        map-func #(do (aset % 3 (.score (aget % 1) search)) %)
        map-func2 #(do (aset % 4 (js/score (aget % 1) search)) %)
        has-score #(> (.-score (aget % 4)) 0)]
    (if-not (empty? search)
      (let [score0 (.. items (map map-func3) (filter #(aget % 2)))
            score1 (.. score0  (map map-func) (sort score-sort))
            score2 (.. score1 (slice 0 50) (map map-func2) (filter has-score) (sort score-sort2))]
        score2)
      (.. items (map #(array % (key %) nil nil)) (sort #(.localeCompare (second %) (second %2)))))))

(defui item [this x]
  [:li {:index x}]
  :click (fn []
           (object/raise this :set-selection! x)
           (object/raise this :select! x)))

(defn fill-lis [{:keys [lis size search selected key transform]} results]
  (let [cnt (count results)
        cur (mod selected (if (> cnt size)
                            size
                            cnt))
        transform (if transform
                    transform
                    identity)]
    (doseq [[i li res] (map vector (range) lis results)
            :when res]
      (dom/html li (transform (aget res 1) (aget res 4) (if-not (empty? search)

                                             (js/wrapMatch (aget res 1) (aget res 4))
                                             (aget res 1))
                              (aget res 0)))
      (dom/css li {:display "block"})
      (if (= i cur)
        (dom/add-class li :selected)
        (dom/remove-class li :selected)))
    (doseq [li (drop cnt lis)]
      (dom/css li {:display "none"}))))

(object/object* ::filter-list
                :tags #{:filter-list}
                :selected 0
                :placeholder "search"
                :items []
                :search ""
                :init (fn [this opts]
                        (let [opts (merge {:size 100} opts)
                              lis (for [x (range (:size opts))]
                                    (item this x))]
                          (object/merge! this (merge {:lis lis} opts))
                          [:div.filter-list
                           (input this)
                           [:ul
                            lis]
                           ])))

(object/tag-behaviors :filter-list [::move-selection ::clear! ::change! ::focus! ::update-lis ::select! ::set-on-select ::set-selection! ::filter-active ::filter-inactive])

(defn filter-list [opts]
  (let [lst (object/create ::filter-list opts)]
    (object/raise lst :refresh!)
    lst))

;;**********************************************************
;; Commands
;;**********************************************************

(object/behavior* ::select-command
                  :triggers #{:select}
                  :reaction (fn [this sel]
                              (when-let [cmd (by-id sel)]
                                (if (:options cmd)
                                  (do
                                    (object/merge! sidebar-command {:active cmd})
                                    (object/raise (:options cmd) :focus!))
                                  (do
                                    (object/raise sidebar-command :exec! cmd)
                                    (object/raise sidebar-command :selected-exec cmd)
                                    (object/merge! sidebar-command {:active nil})))
                                )
                              ))

(object/behavior* ::select-hidden
                  :triggers #{:select-unknown}
                  :reaction (fn [this v]
                              (when-let [cmd (by-id (keyword v))]
                                (object/raise this :select cmd))))

(object/behavior* ::post-select-pop
                  :triggers #{:selected-exec}
                  :reaction (fn [this]
                              (object/raise sidebar/rightbar :close!)
                              (exec! :focus-last-editor)))

(object/behavior* ::exec-command
                  :triggers #{:exec!}
                  :reaction (fn [this sel & args]
                              (let [cmd (by-id sel)]
                                (cond
                                 (not (:options cmd)) (apply exec! cmd args)
                                 (and (:options cmd) (seq args)) (apply (:exec cmd) args)
                                 :else (do (exec! :show-commandbar-transient)
                                         (object/raise (:selector @this) :select cmd))))))

(object/behavior* ::exec-active!
                  :triggers #{:exec-active!}
                  :reaction (fn [this args]
                              (let [cmd (:active @this)]
                                (apply (:exec cmd) args)
                                (object/raise this :selected-exec cmd)
                                (object/merge! this {:active nil}))))

(object/behavior* ::focus!
                  :triggers #{:focus!}
                  :reaction (fn [this]
                              (if-not (:active @this)
                                (let [input (dom/$ :.search (object/->content this))]
                                  (dom/focus input)
                                  (.select input))
                                (object/raise (-> @this :active :options) :focus!))))

(object/behavior* ::soft-focus!
                  :triggers #{:soft-focus!}
                  :reaction (fn [this]
                              (let [input (dom/$ :.search (object/->content this))]
                                (dom/focus input))))

(object/behavior* ::refresh!
                  :triggers #{:refresh!}
                  :reaction (fn [this]
                              (object/raise (:selector @this) :refresh!)))

(object/behavior* ::cancel!
                  :triggers #{:cancel!}
                  :reaction (fn [this]
                              (object/merge! this {:active nil})
                              (object/raise this :focus!)))

(defui header-button [this]
  [:h2 (bound this #(-> % :active :desc))]
  :click (fn []
           (object/raise this :cancel!)))

(defn ->options [this active]
  (when (:options active)
    (object/->content (:options active))))

(defn ->command-class [this]
  (str "command " (if (:active this)
                    "options"
                    "selector")
       (when (dom/has-class? (:content this) :active)
         " active")))

(defn command->display [orig scored highlighted item]
  (str "<p>" orig "<p>" (when-let [binding (first (keyboard/cmd->bindings (item :command)))]
                                 (str "<p class='binding'>" (second binding) "</p>"))))

(object/object* ::sidebar.command
                :tags #{:sidebar.command}
                :label "command"
                :active nil
                :order 3
                :init (fn [this]
                        (let [commands (subatom cmd/manager :commands)
                              f2 (computed [commands]
                                                 (fn [cmds]
                                                   (filter #(not (:hidden %)) (vals cmds))))
                              s2 (filter-list {:items f2
                                               :transform #(command->display % %2 %3 %4)
                                               :key :desc})]
                          (object/merge! this {:selector s2})
                          (object/add-tags s2 [:command.selector])
                          [:div {:class (bound this ->command-class)}
                           [:div.selector
                            (object/->content s2)]
                           [:div.options
                            (header-button this)
                            [:div
                             (bound (subatom this :active) #(->options this %))]]
                           ]
                          )))

(object/behavior* ::init-commands
                  :triggers #{:post-init}
                  :reaction (fn [app]
                              (object/raise sidebar-command :refresh!)))

(object/tag-behaviors :app [::init-commands])
(object/tag-behaviors :sidebar.command [::exec-command ::exec-active! ::focus! ::soft-focus! ::refresh! ::post-select-pop ::cancel!])
(object/tag-behaviors :command.selector [::select-command ::select-hidden ::escape!])
(object/tag-behaviors :command.options [::options-escape!])

(def sidebar-command (object/create ::sidebar.command))
(ctx/in! :commandbar sidebar-command)

(sidebar/add-item sidebar/rightbar sidebar-command)

(def command cmd/command)

(defn show-and-focus [opts]
  (object/raise sidebar/rightbar :toggle sidebar-command opts)
  (object/raise sidebar-command :focus!))

(defn pre-fill [v]
  (dom/val (dom/$ :.search (object/->content sidebar-command)) v))

(defn show-filled [fill opts]
  (pre-fill fill)
  (object/raise sidebar/sidebar :toggle sidebar-command (assoc opts :soft? true))
  (object/raise sidebar-command :soft-focus!))

(def by-id cmd/by-id)

(def exec! cmd/exec!)

(defn exec-active! [& args]
  (object/raise sidebar-command :exec-active! args))

(command {:command :show-commandbar
          :desc "Command: Show command bar"
          :hidden true
          :exec (fn []
                  (show-and-focus {})
                  )})

(command {:command :show-commandbar-transient
          :hidden true
          :desc "Command: Show command bar transiently"
          :exec (fn []
                  (show-and-focus {:transient? true}))})

(command {:command :quit
          :desc "Window: Quit Light Table"
          :exec (fn []
                  (app/close))})


(command {:command :passthrough
          :hidden true
          :desc "No-op key passthrough"
          :exec (fn []
                  (keyboard/passthrough))})

(command {:command :filter-list.input.move-selection
          :hidden true
          :desc "FilterList: move selection"
          :exec (fn [dir]
                  (object/raise (ctx/->obj :filter-list.input) :move-selection dir)
                  )})

(command {:command :filter-list.input.select!
          :hidden true
          :desc "FilterList: select"
          :exec (fn []
                  (object/raise (ctx/->obj :filter-list.input) :select!)
                  )})

(command {:command :filter-list.input.escape!
          :hidden true
          :desc "FilterList: escape"
          :exec (fn [force?]
                  (object/raise (ctx/->obj :filter-list.input) :escape! force?)
                  )})

(command {:command :options-input.select!
          :hidden true
          :desc "OptionsInput: select"
          :exec (fn []
                  (object/raise (ctx/->obj :options-input) :select!)
                  )})

(command {:command :options-input.escape!
          :hidden true
          :desc "OptionsInput: escape"
          :exec (fn []
                  (println "trying to escape")
                  (object/raise (ctx/->obj :options-input) :escape!)
                  )})