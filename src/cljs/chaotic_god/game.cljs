(ns chaotic-god.game
  (:require [goog.dom :as dom]
            [goog.Timer :as timer]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.events.KeyCodes :as key-codes]))

(def sq-width 30)
(def sq-height 30)

(defn grid-to-dimentions
  "Take grid coords, [0 0], [0 2] and make coords out of them for the squares"
  [[nx ny]]
  [(* sq-width nx)
   (* sq-height ny)])

(defn surface
  []
  (let [surface (dom/getElement "surface")]
    [(.getContext surface "2d")
     (. surface -width)
     (. surface -height)]))

(defn fill-rect
  [[surface] [x y width height] [r g b]]
  (set! (. surface -fillStyle) (str "rgb(" r "," g "," b ")"))
  (.fillRect surface x y width height))

(defn stroke-rect
  [[surface] [x y width height] line-width [r g b]]
  (set! (. surface -strokeStyle) (str "rgb(" r "," g "," b ")"))
  (set! (. surface -lineWidth) line-width)
  (.strokeRect surface x y width height))

(defn fill-circle
  [[surface] coords [r g b]]
  (let [[x y d] coords]
    (set! (. surface -fillStyle) (str "rgb(" r "," g "," b ")"))
    (. surface (beginPath))
    (.arc surface x y d 0 (* 2 Math/PI) true)
    (. surface (closePath))
    (. surface (fill))))

(defn update-canvas
  [world surface]
  (let [[_ width height] surface]
    (fill-rect surface [0 0 width height] [10 10 10])
    (stroke-rect surface [0 0 width height] 2 [0 0 0])))

(defn game
  [timer state surface]
  (let [[_ width height] surface]
    (swap! state (fn [curr]
                   (update-canvas curr surface)
                   (-> curr
                       ;; Logic
                       )))))

(defn click
  [timer state surface event]
  (if (not (.-enabled timer))
    (. timer (start))
    (. timer (stop))))

(defn keypress
  [state e]
  (let [browser-event (.getBrowserEvent e)]
   (do
     (.log js/console "event:" e)
     (.log js/console "br event:" browser-event))))

(defn ^:export main
  []
  (let [surface (surface)
        [_ width _] surface
        timer (goog.Timer. 500)
        state (atom {})]
    (update-canvas @state surface)
    (. timer (start))
    (events/listen timer goog.Timer/TICK #(game timer state surface))
    (events/listen js/window event-type/KEYPRESS #(keypress state %))
    (events/listen js/window event-type/TOUCHSTART #(keypress state %))
    (events/listen js/window event-type/CLICK #(click timer state surface %))))
