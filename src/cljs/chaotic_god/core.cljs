(ns chaotic-god.core
  (:require [goog.dom :as dom]
            [goog.Timer :as timer]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.events.KeyCodes :as key-codes]
            [chaotic-god.utils :as utils]
            [cljs.core.async :refer [>! <! chan put! close! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(defn ^:export main
  []
  (let [keyboard (utils/listen js/window :keydown)
        mouse    (utils/listen js/window :mousedown)]
    (go
     (while true
       (let [[v sc] (alts! [keyboard mouse])]
         (condp = sc
           keyboard (.log js/console "keyboard pressed")
           mouse (.log js/console "mouse clicked!")))))))
