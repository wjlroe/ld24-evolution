(ns chaotic-god.game
  (:require [goog.dom :as dom]
            [goog.Timer :as timer]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.events.KeyCodes :as key-codes]))

;; Canvas size should fit the world and pixels below
(def sq-width 50)
(def sq-height 50)

(def initial-world
  [" M             "
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"])

(def tile-to-colour
  {\M [67 67 67]
   \space [124 122 255]
   \d [87 53 1]
   \t [127 128 106]})

(def tile-to-meaning
  {\M :mine
   \t :tunnel
   \d :dirt
   \space :sky})

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

(defn draw-square
  [surface x y tile]
  (fill-rect surface [x y sq-width sq-height] (tile tile-to-colour [0 0 0])))

(defn update-canvas
  [state surface]
  (let [[_ width height] surface
        {:keys [world]} state]
    (doseq [[y-index squares] (map-indexed vector world)
            [x-index square]  (map-indexed vector squares)]
      (draw-square surface (* x-index sq-width) (* y-index sq-height) square))))

(defn deconstruct-environment
  [world {:keys [x y]}]
  (let [[above below]      (split-at y world)
        [given-row below]  [(first below) (rest below)]
        [left right]       (split-at x given-row)
        [given-sq right]   [(first right) (rest right)]]
    {:above above
     :below below
     :left left
     :right right
     :given-row given-row
     :given-sq  given-sq}))

(defn dig-away-earth
  "Translate the given square coords into a tunnel square"
  [world {:keys [x y]}]
  (let [[above below]         (split-at y world)
        [dig-row-then below]  [(first below) (rest below)]
        [left right]          (split-at x dig-row-then)
        [dig-square right]    [(first right) (rest right)]
        dig-row-now           (apply str (concat left "t" right))]
    (js/console.log "world:" (pr-str world) "x:" x "y:" y)
    (js/console.log "above:" (pr-str above) "below:" (pr-str below))
    (js/console.log "left:" (pr-str left) "dig-row-then:" (pr-str dig-row-then) "right:" (pr-str right) "dig-row-now:" (pr-str dig-row-now))
    (concat above [dig-row-now] below)))

(defn decide-where-to-dig
  [world {:keys [x y] :as dig-coords}]
  (if (or (= y 0) (= y 1)) ;; dig down into earth 2 squares first
    {:x x :y (inc y)}
    (let [{:keys [above below left right given-row given-sq]}
          (deconstruct-environment world dig-coords)])))

(defn move-paleontologist
  [state]
  (let [{:keys [world paleontologist]} state
        new-paleontologist (decide-where-to-dig world paleontologist)
        new-world (dig-away-earth world new-paleontologist)]
    ;; start just digging down to get it working
    (assoc state
      :paleontologist new-paleontologist
      :world new-world)))

(defn game
  [timer state surface]
  (let [[_ width height] surface]
    (swap! state (fn [curr]
                   (update-canvas curr surface)
                   (-> curr
                       ;; Logic
                       (move-paleontologist)
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
        state (atom {:world initial-world :paleontologist {:x 1 :y 0}})]
    (update-canvas @state surface)
    (. timer (start))
    (events/listen timer goog.Timer/TICK #(game timer state surface))
    (events/listen js/window event-type/KEYPRESS #(keypress state %))
    (events/listen js/window event-type/TOUCHSTART #(keypress state %))
    (events/listen js/window event-type/CLICK #(click timer state surface %))))
