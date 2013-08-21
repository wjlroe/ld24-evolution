(ns chaotic-god.core
  (:require [goog.dom :as dom]
            [goog.Timer :as timer]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.events.KeyCodes :as key-codes]
            [chaotic-god.utils :as utils]
            [cljs.core.async :refer [>! <! chan put! close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(def keyboard-events (utils/listen js/window :keydown))
(def mouse-events (utils/listen js/window :mousedown))
(def actions
  {key-codes/SPACE :pause})

(defn event-to-action
  [event]
  (let [action ((.-keyCode event) actions)]
    (when action
      (.log js/console "Action: " action))))

(defn click
  [event]
  (let [x (.-offsetX event)
        y (.-offsetY event)]
    (.log js/console "click in x:" x " and y: " y)))

(defn keypress
  [event]
  (-> event
      event-to-action))

(defn ^:export run-loop
  []
  (go
    (while true
      (let [[v sc] (alts! [keyboard-events mouse-events])]
        (condp = sc
          keyboard-events (keypress v)
          mouse-events (click v))))))
