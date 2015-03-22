(ns lt.objs.clients.ws
  (:refer-clojure :exclude [send])
  (:require [cljs.reader :as reader]
            [lt.object :as object]
            [lt.objs.files :as files]
            [lt.objs.clients :as clients]
            [lt.util.load :as load]
            [lt.util.cljs :refer [js->clj]]
            [clojure.string :as string])
  (:use [lt.util.js :only [wait ->clj]])
  (:require-macros [lt.macros :refer [behavior]]))

(def port 8023)
(def sockets (atom {}))
(def io (load/node-module "socket.io"))
(def net (js/require "net"))

(defn send-to [sock data]
  (if sock
    (.emit sock (-> data second) data)
    ;;TODO: some system-wide error reporting
    (.log js/console (str "No such client: " sock))))

(defn ->client [data]
  (let [d (js->clj data :keywordize-keys true)]
    (assoc d
           :type :ws)))

(defn store-client! [socket data]
  (let [data (js->clj data :keywordize-keys true)
        client (clients/by-name (:name data))
        data (if-not (:tags data)
               (assoc data :tags [:ws.client])
               (assoc data :tags (map keyword (:tags data))))]
    (.on socket "disconnect" (fn []
                               (when-let [cur (clients/by-name (:name data))]
                                 (when (= socket (:socket @cur))
                                   (clients/rem! cur)))))
    (if (clients/available? client)
      (object/merge! client {:socket socket})
      (clients/handle-connection! (assoc data :socket socket :type :websocket)))))

(defn on-result [socket data]
  (object/raise clients/clients :message (js->clj data :keywordize-keys true)))

(defn on-connect [socket]
  (.log js/console "on-connect: " socket)

  (.on socket "result" #(on-result socket %))
  (.on socket "init" (partial store-client! socket)))

(behavior ::send!
          :triggers #{:send!}
          :reaction (fn [this msg]
                      (send-to (:socket @this) (array (:cb msg) (:command msg) (-> msg :data clj->js)))))

(def server
  (try
    (let [ ws (io port)]
      (.log js/console "server creation")
      (.on ws "connection" on-connect)
      (.on ws "error" #((.log js/console "server error " %)))
      (.path ws (files/lt-home "core/node_modules"))
      ws)
    ;;TODO: warn the user that they're not connected to anything
    (catch js/Error e
      (.log js/console e (.-stack e))
      )
    (catch js/global.Error e
      (.log js/console e (.-stack e))
      )))

(behavior ::kill-on-closed
                  :triggers #{:closed}
                  :reaction (fn [app]
                              (try
                                (.close server)
                                (catch js/Error e)
                                (catch js/global.Error e))))
