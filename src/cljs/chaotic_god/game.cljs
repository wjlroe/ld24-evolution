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
  ["               "
   " M             "
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"])

(def world-width (dec (count (first initial-world))))
(def total-width (* sq-width (count (first initial-world))))

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
  (fill-rect surface [x y sq-width sq-height] (tile tile-to-colour [0 0 0]))
  (stroke-rect surface [x y sq-width sq-height] 1 [0 0 0]))

(defn update-canvas
  [state surface]
  (let [[_ width height] surface
        {:keys [world selection]} state]
    (doseq [[y-index squares] (map-indexed vector world)
            [x-index square]  (map-indexed vector squares)]
      (draw-square surface (* x-index sq-width) (* y-index sq-height) square))
    (js/console.log "selection:" (pr-str selection))
    (when selection
      (stroke-rect surface [(* (:x selection) sq-width) (* (:y selection) sq-height) sq-width sq-height] 1 [43 197 0]))))

(defn deconstruct-environment
  [world {:keys [x y]}]
  (let [[above below]      (split-at y world)
        [given-row below]  [(first below) (rest below)]
        [left right]       (split-at x given-row)
        [given-sq right]   [(first right) (rest right)]]
    {:above above
     :below below
     :left (apply str left)
     :right (apply str right)
     :given-row given-row
     :given-sq  given-sq}))

(defn edge-left?
  [x y _]
  (= x 1))

(defn edge-right?
  [x y _]
  (= x (- world-width 1)))

(defn tunnel-to-left?
  [x y {:keys [left]}]
  (= \t (last left)))

(defn tunnel-to-right?
  [x y {:keys [right]}]
  (= \t (first right)))

(defn tunnel-above?
  [x y {:keys [above]}]
  (= \t (nth (last above) x)))

(defn tunnel-2-above?
  [x y {:keys [above]}]
  (= \t (nth (second (reserve above)) x)))

(defn tunnel-below?
  [x y {:keys [below]}]
  (= \t (nth (first below) x)))

(defn tunnel-depth
  [x y {:keys [above]}]
  (count (take-while #(contains? #{\t \M} %) (reverse (map #(nth % x) above)))))

(defn hit-bottom?
  [x y {:keys [below]}]
  (= 0 (count below)))

(defn decide-where-to-dig
  [world {:keys [x y] :as dig-coords}]
  (if (or (= y 0) (= y 1)) ;; dig down into earth 2 squares first
    {:x x :y (inc y)}
    (let [env (deconstruct-environment world dig-coords)]
      (js/console.log "env:" (pr-str env) "world-width:" world-width "dig-coords:" (pr-str dig-coords))
      (cond
       (hit-bottom? x y env)
       dig-coords
       (and (edge-left? x y env)
            (< (tunnel-depth x y env) 2))
       {:x x :y (inc y)}
       (and (or (edge-left? x y env)
                (not (edge-right? x y env)))
            (not (tunnel-to-right? x y env)))
       {:x (inc x) :y y}
       (and (edge-right? x y env)
            (tunnel-to-left? x y env))
       {:x x :y (inc y)}
       (and (edge-right? x y env)
            (< (tunnel-depth x y env) 2))
       {:x x :y (inc y)}
       (edge-right? x y env)
       {:x (dec x) :y y}
       (and (not (edge-right? x y env))
            (not (edge-left? x y env))
            (tunnel-to-right? x y env))
       {:x (dec x) :y y}
       true
       dig-coords))))

(defn dig-away-earth
  "Translate the given square coords into a tunnel square"
  [world coords]
  (let [{:keys [above below left right]}
        (deconstruct-environment world coords)

        dig-row-now (apply str (concat left "t" right))]
    (concat above [dig-row-now] below)))

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

(defn abs-pos-to-coords
  [x y]
  [(- (int (Math/ceil (/ x sq-width))) 1)
   (- (int (Math/ceil (/ y sq-height))) 1)])

(defn toggle-timer
  [timer]
  (if (not (.-enabled timer))
    (. timer (start))
    (. timer (stop))))

(defn click
  [timer state surface event]
  (swap! state (fn [curr]
                 (let [abs-x (.-offsetX event)
                       abs-y (.-offsetY event)
                       [x y] (abs-pos-to-coords abs-x abs-y)]
                   (assoc curr :selection {:x x :y y})))))

(defn keypress
  [timer state e]
  (let [browser-event (.getBrowserEvent e)]
   (do
     (.log js/console "event:" e)
     (.log js/console "br event:" browser-event))
   (cond
    (= (.-charCode browser-event) key-codes/SPACE)
    (toggle-timer timer))))

(defn ^:export main
  []
  (let [surface (surface)
        [_ width _] surface
        timer (goog.Timer. 300)
        state (atom {:world initial-world
                     :paleontologist {:x 1 :y 1}
                     :selection nil})]
    (update-canvas @state surface)
    (. timer (start))
    (events/listen timer goog.Timer/TICK #(game timer state surface))
    (events/listen js/window event-type/KEYPRESS #(keypress timer state %))
    (events/listen js/window event-type/TOUCHSTART #(keypress timer state %))
    (events/listen js/window event-type/CLICK #(click timer state surface %))))
