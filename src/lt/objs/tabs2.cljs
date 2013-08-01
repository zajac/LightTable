(ns lt.objs.tabs
  (:require [lt.object :refer [object* behavior*] :as object]
            [lt.objs.editor :as editor]
            [lt.objs.canvas :as canvas]
            [lt.objs.command :as cmd]
            [lt.objs.animations :as anim]
            [lt.objs.context :as ctx]
            [lt.objs.menu :as menu]
            [lt.util.load :as load]
            [lt.util.dom :refer [$ append] :as dom]
            [lt.util.style :refer [->px]]
            [lt.util.js :refer [now]]
            [crate.core :as crate]
            [crate.binding :refer [bound map-bound subatom]])
  (:require-macros [lt.macros :refer [defui]]))

(load/js "core/node_modules/lighttable/ui/dragdrop.js" :sync)

(defn ensure-visible [idx tabset]
  (when-let [cur (aget (dom/$$ ".list li" (object/->content tabset)) idx)]
    (let [left (.-offsetLeft cur)
          width (.-clientWidth cur)
          right (+ left width)
          gp (dom/parent (dom/parent cur))
          pwidth (.-clientWidth gp)
          pleft (.-scrollLeft gp)
          pright (+ pleft pwidth)
          inside (and (>= left pleft)
                      (<= right pright))]
      (when-not inside
        (if (> pleft left)
          (set! (.-scrollLeft gp) (- left 50))
          (set! (.-scrollLeft gp) (+ (- right pwidth) 50)))
        ))))

(object/behavior* ::on-destroy-remove
                  :triggers #{:destroy}
                  :reaction (fn [this]
                              (rem! this)
                              ))

(object/behavior* ::active-tab-num
                  :triggers #{:tab}
                  :reaction (fn [this num]
                              (let [objs (@this :objs)]
                                (if (< num (count objs))
                                  (active! (get objs num))
                                  (active! (get objs (dec (count objs))))))
                              ))

(object/behavior* ::prev-tab
                  :triggers #{:tab.prev}
                  :throttle 100
                  :reaction (fn [this]
                              (let [objs (@this :objs)
                                    idx (->index (:active-obj @this))]
                                (if (> idx 0)
                                  (active! (get objs (dec idx)))
                                  (active! (get objs (dec (count objs))))))
                              ))

(object/behavior* ::next-tab
                  :triggers #{:tab.next}
                  :throttle 100
                  :reaction (fn [this]
                              (let [objs (@this :objs)
                                    idx (inc (->index (:active-obj @this)))]
                                (if (< idx (count objs))
                                  (active! (get objs idx))
                                  (active! (get objs 0))))
                              ))

(object/behavior* ::tab-close
                  :triggers #{:tab.close}
                  :reaction (fn [this]
                              (try
                                (let [orig (:active-obj @this)]
                                  (object/raise orig :close))
                                (catch js/Error e
                                  (js/lt.objs.console.error e))
                                (catch js/global.Error e
                                  (js/lt.objs.console.error e)))))

(defn ->index [obj]
  (when (and obj @obj (::tabset @obj))
    (first (first (filter #(= obj (second %)) (map-indexed vector (:objs @(::tabset @obj))))))))

(defn wrap-multi-behaviors [e]
  (object/add-behavior! e ::on-destroy-remove))

(defn ->name [e]
  (or
   (-> @e :info :name)
   (@e :name)
   "unknown"))

(defn ->path [e]
  (or
   (-> @e :info :path)
   (@e :path)
   ""))

(defn active? [c e multi]
  (str c (when (= (@multi :active-obj) e)
           " active")))

(defn dirty? [c e]
  (str c (when (:dirty @e)
           " dirty")))

(defui item [multi e pos]
  [:li {:class (-> " "
                   (active? e multi)
                   (dirty? e))
        :draggable "true"
        :title (->path e)
        :obj-id (object/->id e)
        :pos pos}
   (->name e)]
  :click (fn []
           (active! e))
  :contextmenu (fn [ev]
                 (menu! e ev)
                 (dom/prevent ev)
                 (dom/stop-propagation ev))
  )

(defn update-tab-order [multi children]
  (let [ser (if (vector? children)
              children
              (map #(dom/attr % :pos) children))
        prev-active (:active-obj @multi)]
    (object/merge! multi {:objs (mapv (:objs @multi) ser)
                          :active-obj nil})
    (active! prev-active)
    ))

(defn move-tab [multi elem]
  (let [id (dom/attr elem :obj-id)
        idx (dom/index elem)
        obj (object/by-id (js/parseInt id))
        cnt (-> @multi :objs count)]
    (rem! obj)
    (add! obj multi)
    (if (> cnt 0)
      (update-tab-order multi (vec (concat (range idx) [cnt] (range idx cnt)))))
    (active! obj)))

(defn objs-list [multi objs]
  (let [item (crate/html
              [:ul
               (for [[idx o] (map vector (range) objs)
                     :when @o]
                 (item multi o idx))])]
    (js/sortable item (js-obj "axis" "x" "distance" 10  "scroll" false "opacity" 0.9 "connectWith" ".list"))
    (dom/on item "contextmenu" (fn [e]
                                 (object/raise multi :menu! e)
                                 (dom/prevent e)
                                 (dom/stop-propagation e)))
    (dom/on item "moved" (fn [e] (move-tab multi (.-opts e)) ))
    (dom/on item "sortupdate" (fn [e] (update-tab-order multi (.-opts e))))
    item))

(behavior* ::on-destroy-objs
           :triggers #{:destroy}
           :reaction (fn [this]
                       (doseq [e (:objs @this)]
                         (object/destroy! e))
                       ))

(behavior* ::repaint-tab-updated
           :triggers #{:tab.updated}
           :reaction (fn [this]
                       (object/update! this [:count] inc)))

(defui tabbed-item [active item]
  [:div.content {:style {:display (bound active #(if (= % @item)
                                                   ""
                                                   "none"))}}
   (bound item #(when % (object/->content %)))])

(defui vertical-grip [this]
  [:div.vertical-grip {:draggable "true"}]
  :dragstart (fn [e]
               (set! (.-dataTransfer.dropEffect e) "move")
               (.dataTransfer.setData e "text/plain" nil)
               (object/raise this :start-drag e)
               )
  :dragend (fn [e]
             (object/raise this :end-drag e)
             )
  :drag (fn [e]
          (set! (.-dataTransfer.dropEffect e) "move")
          (object/raise this :width! e)))

(defn ->perc [x]
  (if x
    (str x "%")
    "0"))

(defn floored [x]
  (cond
   (< x 0) 0
   (> x 100) 100
   :else x))

(defn to-perc [width x]
  (* (/ x width) 100))

(defn next-tabset [t]
  (let [ts (@multi :tabsets)]
    (second (drop-while #(not= t %) ts))
    ))

(defn prev-tabset [t]
  (let [ts (@multi :tabsets)]
    (-> (take-while #(not= t %) ts)
        (last))))

(defn previous-tabset-width [cur]
  (let [ts (@multi :tabsets)]
    (reduce + 0 (map (comp :width deref) (take-while #(not= cur %) ts)))
    ))

(defn spawn-tabset []
  (let [ts (object/create ::tabset)
        width (- 100 (reduce + (map (comp :width deref) (@multi :tabsets))))]
    (object/merge! ts {:width width})
    (add-tabset ts)))

(defn equalize-tabset-widths []
  (let [tss (:tabsets @multi)
        width (/ 100.0 (count tss))]
    (doseq [ts tss]
      (object/merge! ts {:width width}))))

(object/behavior* ::no-anim-on-drag
                  :triggers #{:start-drag}
                  :reaction (fn [this]
                              (anim/off)))

(object/behavior* ::reanim-on-drop
                  :triggers #{:end-drag}
                  :reaction (fn [this]
                              (anim/on)))

(object/behavior* ::set-dragging
                  :triggers #{:start-drag}
                  :reaction (fn [this]
                              (dom/add-class (dom/$ :body) :dragging)
                              ))

(object/behavior* ::unset-dragging
                  :triggers #{:end-drag}
                  :reaction (fn [this]
                              (dom/remove-class (dom/$ :body) :dragging)
                              ))

(object/behavior* ::set-width-final!
                  :triggers #{:end-drag}
                  :reaction (fn [this e]
                              (let [width (dom/width (object/->content multi))
                                    left (:left @multi)
                                    cx (.-clientX e)
                                   new-loc (- (+ width left) cx)
                                    new-perc (floored (int (- 100 (previous-tabset-width this) (to-perc width new-loc))))
                                    prev-width (:width @this)
                                    ts (next-tabset this)
                                    new-perc (if (>= new-perc (+ (:width @ts) prev-width))
                                               (+ (:width @ts) prev-width)
                                               new-perc)
                                    next-width (floored
                                                    (if-not ts
                                                      1
                                                      (+ (:width @ts) (- prev-width new-perc))))]
                                (cond
                                 (= new-perc 0) (rem-tabset this)
                                 (= next-width 0) (rem-tabset ts :prev)
                                 :else
                                 (when-not (= cx 0)
                                   (if (or (< new-perc 0) )
                                     (object/merge! this {:width 100})
                                     (when (and (not= cx 0)
                                                (>= new-perc 0)
                                                (>= next-width 0))
                                       (object/merge! this {:width new-perc})
                                       (if ts
                                         (object/merge! ts {:width next-width})
                                         (spawn-tabset)))))))))

(defn temp-width [ts w]
  (dom/css (object/->content ts) {:width (->perc w)
                                  :border-width (if (= 0 w)
                                                  0
                                                  "")}))

(object/behavior* ::width!
                  :triggers #{:width!}
                  :reaction (fn [this e]
                              (let [width (dom/width (object/->content multi))
                                    left (:left @multi)
                                    cx (.-clientX e)
                                    new-loc (- (+ width left) cx)
                                    new-perc (floored (int (- 100 (previous-tabset-width this) (to-perc width new-loc))))
                                    prev-width (:width @this)
                                    ts (next-tabset this)
                                    new-perc (if (and ts
                                                      (>= new-perc (+ (:width @ts) prev-width)))
                                               (+ (:width @ts) prev-width)
                                               new-perc)
                                    next-width (floored
                                                    (if-not ts
                                                      1
                                                      (+ (:width @ts) (- prev-width new-perc))))]
                                ;(println (.-clientX e) (.-clientY e) (.-screenX e) (.-screenY e) (js->clj (js/Object.keys e)))
                                (when-not (= cx 0)
                                (if (or (< new-perc 0) )
                                  (temp-width this 100)
                                  (when (and (not= cx 0)
                                             (>= new-perc 0)
                                             (>= next-width 0))
                                    (temp-width this new-perc)
                                    (if ts
                                      (temp-width ts next-width)
                                      (spawn-tabset))))))
                              (set! start (now))
                              ))

(object/behavior* ::on-active-active-tabset
                  :triggers #{:active}
                  :reaction (fn [this]
                              (ctx/in! :tabset (::tabset @this))))

(object/behavior* ::tabset-active
                  :triggers #{:active}
                  :reaction (fn [this]
                              (ctx/in! :tabset this)
                              (when-let [active (:active-obj @this)]
                                (object/raise active :focus!))))

(object/behavior* ::tabset-menu
                  :triggers #{:menu!}
                  :reaction (fn [this ev]
                              (-> (menu/menu [{:label "New tabset"
                                               :click (fn [] (cmd/exec! :tabset.new))}
                                              {:label "Close tabset"
                                               :click (fn [] (rem-tabset this))}
                                              ])
                                  (menu/show-menu (.-clientX ev) (.-clientY ev)))
                              ))

(defui tabset-ui [this]
  [:div.tabset {:style {:width (bound (subatom this :width) ->perc)}}
   [:div.list
    (bound this #(objs-list this (:objs %)))]
   [:div.items
    (map-bound (partial tabbed-item (subatom this :active-obj)) this {:path [:objs]})]
   (vertical-grip this)]
  :click (fn []
           (object/raise this :active)))

(object/object* ::tabset
                :objs []
                :active-obj (atom {})
                :count 0
                :tags #{:tabset}
                :width 100
                :init (fn [this]
                        (tabset-ui this)
                        ))

(defn ->tabsets [tabs]
  (for [k tabs]
    (object/->content k)))

(object/behavior* ::left!
                  :triggers #{:left!}
                  :reaction (fn [this v]
                              (object/update! this [:left] + v)
                              (object/raise this :resize)))

(object/behavior* ::right!
                  :triggers #{:right!}
                  :reaction (fn [this v]
                              (object/update! this [:right] + v)
                              (object/raise this :resize)))

(object/behavior* ::bottom!
                  :triggers #{:bottom!}
                  :reaction (fn [this v]
                              (object/update! this [:bottom] + v)
                              (object/raise this :resize)))

(def multi-def (object* ::multi-editor2
                        :tags #{:tabs}
                        :tabsets []
                        :left 0
                        :right 0
                        :bottom 0
                        :init (fn [this]
                                (let [tabsets (crate/html [:div.tabsets])]
                                  (object/merge! this {:tabsets-elem tabsets})
                                (ctx/in! :tabs this)
                                [:div#multi {:style {:left (bound (subatom this :left) ->px)
                                                     :right (bound (subatom this :right) ->px)
                                                     :bottom (bound (subatom this :bottom) ->px)}}
                                 tabsets]
                                ))))

(def multi (object/create multi-def))

(def tabset (object/create ::tabset))


(defn add-tabset [ts]
  (object/update! multi [:tabsets] conj ts)
  (dom/append (:tabsets-elem @multi) (object/->content ts))
  (object/raise ts :active))

(defn rem-tabset [ts prev?]
  (let [to-ts (if prev?
                (or (prev-tabset ts) (next-tabset ts))
                (or (next-tabset ts) (prev-tabset ts)))]
    (when to-ts
      (object/merge! to-ts {:width (floored (+ (:width @to-ts) (:width @ts)))})
      (dom/remove (object/->content ts))
      (doseq [t (:objs @ts)]
        (add! t to-ts))
      (object/update! multi [:tabsets] #(vec (remove #{ts} %)))
      (object/destroy! ts)
      (object/raise to-ts :active))))

(defn menu! [obj ev]
  (-> (menu/menu [{:label "Close tab"
                   :click (fn [] (object/raise obj :close))}])
      (menu/show-menu (.-clientX ev) (.-clientY ev))))

(defn rem! [obj]
  (when (and obj @obj (::tabset @obj))
    (let [cur-tabset (::tabset @obj)
          idx (->index obj)
          active (:active-obj @cur-tabset)
          aidx (->index active)]
      (remove-watch obj :tabs)
      (object/merge! obj {::tabset nil})
      (object/merge! cur-tabset {:objs (vec (remove #(= obj %) (@cur-tabset :objs)))})
      (if (= obj active)
        (object/raise cur-tabset :tab idx)
        (when (not= aidx (->index active))
          (object/merge! cur-tabset {:active-obj nil})
          (active! active))
        ))))

(defn add! [obj & [ts]]
  (when-let [cur-tabset (or ts (ctx/->obj :tabset))]
    (object/add-tags obj [:tabset.tab])
    (wrap-multi-behaviors obj)
    (object/update! cur-tabset [:objs] conj obj)
    (object/merge! obj {::tabset cur-tabset})
    (add-watch (subatom obj [:dirty]) :tabs (fn [_ _ _ cur]
                                              (object/raise cur-tabset :tab.updated)
                                              ))
    obj))

(defn refresh! [obj]
  (when-let [ts (::tabset @obj)]
    (object/raise ts :tab.updated)))

(defn in-tab? [obj]
  (@obj ::tabset))

(defn add-or-focus! [obj]
  (if (in-tab? obj)
    (active! obj)
    (do
      (add! obj)
      (active! obj))))

(defn active! [obj]
  (when (and obj
             (::tabset @obj))
    (object/merge! (::tabset @obj) {:active-obj obj})
    (object/raise obj :show)
    (ensure-visible (->index obj) (::tabset @obj))
    (when-let [e (@obj :ed)]
      (editor/focus e)
      (editor/refresh e)
      )))

(defn num-tabs []
  (reduce (fn [res cur]
            (+ res (count (:objs @cur))))
          0
          (:tabsets @multi)))

(num-tabs)

(append (object/->content canvas/canvas) (:content @multi))

(cmd/command {:command :tabs.move-next-tabset
              :desc "Tab: Move tab to next tabset"
              :exec (fn []
                      (when-let [ts (ctx/->obj :tabset)]
                        (let [cur (-> @ts :active-obj)
                              next (or (next-tabset ts) (prev-tabset ts))]
                        (when (and cur @cur next (not= next ts))
                          (rem! cur)
                          (add! cur next)
                          (active! cur)))))})

(cmd/command {:command :tabs.move-prev-tabset
              :desc "Tab: Move tab to previous tabset"
              :exec (fn []
                      (when-let [ts (ctx/->obj :tabset)]
                        (let [cur (-> @ts :active-obj)
                              next (or (prev-tabset ts) (next-tabset ts))]
                        (when (and cur @cur next (not= next ts))
                          (rem! cur)
                          (add! cur next)
                          (active! cur)))))})

(cmd/command {:command :tabs.next
              :desc "Tab: Next tab"
              :exec (fn []
                      (object/raise (ctx/->obj :tabset) :tab.next))})

(cmd/command {:command :tabs.prev
              :desc "Tab: Previous tab"
              :exec (fn []
                      (object/raise (ctx/->obj :tabset) :tab.prev))})

(cmd/command {:command :tabs.close
              :desc "Tab: Close current tab"
              :exec (fn []
                      (when (= 0 (num-tabs))
                        (cmd/exec! :window.close))
                      (when-let [ts (ctx/->obj :tabset)]
                        (when (and (:active-obj @ts)
                                 @(:active-obj @ts))
                          (object/raise ts :tab.close)))
                      )})

(cmd/command {:command :tabs.goto
              :hidden true
              :desc "Tab: Goto tab #"
              :exec (fn [x]
                      (object/raise (ctx/->obj :tabset) :tab x))})

(cmd/command {:command :tabset.next
              :desc "Tabset: Next tabset"
              :exec (fn []
                      (if-let [n (next-tabset (ctx/->obj :tabset))]
                        (object/raise n :active)
                        (if-let [n (get (:tabsets @multi) 0)]
                          (object/raise n :active))))})

(cmd/command {:command :tabset.prev
              :desc "Tabset: Previous tabset"
              :exec (fn []
                      (if-let [n (prev-tabset (ctx/->obj :tabset))]
                        (object/raise n :active)
                        (if-let [n (last (:tabsets @multi))]
                          (object/raise n :active))))})

(cmd/command {:command :tabset.close
              :desc "Tabset: Remove active tabset"
              :exec (fn [ts]
                      (rem-tabset (ctx/->obj :tabset)))})

(cmd/command {:command :tabset.new
              :desc "Tabset: Add a tabset"
              :exec (fn []
                      (spawn-tabset)
                      (equalize-tabset-widths))})

(object/behavior* ::init-sortable
                  :triggers #{:pre-init}
                  :reaction (fn [app]
                              (js/initSortable js/window)))

(object/tag-behaviors :app [::init-sortable])

(object/behavior* ::init
                  :triggers #{:init}
                  :reaction (fn [this]
                              (add-tabset tabset)
                              (object/raise tabset :active)
                              ))