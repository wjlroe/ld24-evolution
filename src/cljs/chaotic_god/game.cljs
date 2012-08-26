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
   "sssssssssssssss"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"
   "ddddddddddddddd"])

(def world-width (dec (count (first initial-world))))
(def world-height (count initial-world))
(def total-width (* sq-width (count (first initial-world))))
(def total-height (* sq-height world-height))
(def running? (atom true))

(def tile-to-colour
  {\space [90 126 175]
   \d [87 53 1]})

(def tile-to-image
  {\M :mine
   \s :surface-dirt})

(defn tunnel?
  [tile]
  (or (= \t tile)
      (= \M tile)))

(def tile-to-meaning
  {\M :mine
   \t :tunnel
   \d :dirt
   \s :surface
   \space :sky})

(defn grid-to-dimentions
  "Take grid coords, [0 0], [0 2] and make coords out of them for the squares"
  [[nx ny]]
  [(* sq-width nx)
   (* sq-height ny)])

(defn pause-play-music
  []
  (let [audio (dom/getElement "soundtrack")]
    (if (.-paused audio)
      (.play audio)
      (.pause audio))))

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

(defn which-tunnel-tile
  [env {:keys [x y]}]
  (cond
   (and (tunnel-to-left? x y env)
        (tunnel-to-right? x y env))
   :tunnel-horizontal ;; the horizontal tunnel tile
   (and (not (tunnel-to-left? x y env))
        (not (tunnel-to-right? x y env))
        (or (tunnel-above? x y env)
            (tunnel-below? x y env)))
   :tunnel-vertical ;; vertical tunnel
   (and (tunnel-above? x y env)
        (tunnel-to-right? x y env)
        (not (tunnel-to-left? x y env))
        (not (tunnel-below? x y env)))
   :tunnel-corner-l ;; tunnel L piece
   (and (tunnel-above? x y env)
        (tunnel-to-left? x y env)
        (not (tunnel-to-right? x y env))
        (not (tunnel-below? x y env)))
   :tunnel-corner-j
   (and (tunnel-to-right? x y env)
        (tunnel-below? x y env)
        (not (tunnel-to-left? x y env))
        (not (tunnel-above? x y env)))
   :tunnel-corner-r
   (and (tunnel-to-left? x y env)
        (tunnel-below? x y env)
        (not (tunnel-above? x y env))
        (not (tunnel-to-right? x y env)))
   :tunnel-corner-z
   true
   :tunnel-vertical ;; first tunnel tile placed
   ))

(defn draw-tile
  [surface env coords x y tile]
  (let [[canvas] surface]
    (if (contains? tile-to-colour tile)
      (draw-square surface x y tile)
      (let [image (if (contains? tile-to-image tile)
                    (tile tile-to-image)
                    (which-tunnel-tile env coords))
            image (dom/getElement (name image))]
        (.drawImage canvas image x y)))))

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

(defn draw-game-world
  [state surface]
  (let [[_ width height] surface
        {:keys [world selection paleontologist]} state]
    (doseq [[y-index squares] (map-indexed vector world)
            [x-index square]  (map-indexed vector squares)]
      (let [coords {:x x-index :y y-index}
            env (deconstruct-environment world coords)]
        (draw-tile surface env coords (* x-index sq-width) (* y-index sq-height) square)))
    (fill-rect surface [(* (:x paleontologist) sq-width) (* (:y paleontologist) sq-height) sq-width sq-height] [254 190 88])
    (when selection
      (stroke-rect surface [(* (:x selection) sq-width) (* (:y selection) sq-height) sq-width sq-height] 1 [43 197 0]))))

(defn edge-left?
  [x y _]
  (= x 1))

(defn edge-right?
  [x y _]
  (= x (- world-width 1)))

(defn tunnel-to-left?
  [x y {:keys [left]}]
  (tunnel? (last left)))

(defn tunnel-to-right?
  [x y {:keys [right]}]
  (tunnel? (first right)))

(defn tunnel-above?
  [x y {:keys [above]}]
  (tunnel? (nth (last above) x)))

(defn tunnel-2-above?
  [x y {:keys [above]}]
  (tunnel? (nth (second (reserve above)) x)))

(defn tunnel-below?
  [x y {:keys [below]}]
  (tunnel? (nth (first below) x)))

(defn tunnel-depth
  [x y {:keys [above]}]
  (count (take-while tunnel? (reverse (map #(nth % x) above)))))

(defn hit-bottom?
  [x y {:keys [below]}]
  (= 0 (count below)))

(defn decide-where-to-dig
  [world {:keys [x y] :as dig-coords}]
  (if (or (= y 0) (= y 1)) ;; dig down into earth 2 squares first
    {:x x :y (inc y)}
    (let [env (deconstruct-environment world dig-coords)]
      ;;(js/console.log "env:" (pr-str env) "world-width:" world-width "dig-coords:" (pr-str dig-coords))
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
  (let [{:keys [above below left right] :as env}
        (deconstruct-environment world coords)
        tunnel-tile \t
        dig-row-now (apply str (concat left [tunnel-tile] right))]
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

(defn pause-play-game
  ([forced]
     (reset! running forced))
  ([]
     (swap! running? not)))

(defn end-game?
  [{:keys [paleontologist] :as state}]
  (do
    (when (= (:y paleontologist) world-height)
      (pause-play-game false))
    state))

(defn game
  [state surface]
  (let [[_ width height] surface]
    (when @running?
     (swap! state (fn [curr]
                    (draw-game-world curr surface)
                    (-> curr
                        (move-paleontologist)
                        (end-game?)))))))

(defn abs-pos-to-coords
  [x y]
  [(- (int (Math/ceil (/ x sq-width))) 1)
   (- (int (Math/ceil (/ y sq-height))) 1)])

(defn game-click
  [state surface event]
  (swap! state (fn [curr]
                 (let [abs-x (.-offsetX event)
                       abs-y (.-offsetY event)
                       [x y] (abs-pos-to-coords abs-x abs-y)]
                   (assoc curr :selection {:x x :y y})))))

(defn game-keypress
  [state e]
  (let [browser-event (.getBrowserEvent e)]
   (do
     (.log js/console "event:" e)
     (.log js/console "br event:" browser-event))
   (cond
    (= (.-charCode browser-event) key-codes/SPACE)
    (pause-play-game)
    (= (.-keyCode browser-event) key-codes/SLASH)
    (pause-play-music))))

(defn start-game
  [state surface]
  (let [timer (goog.Timer. 300)]
    (draw-game-world @state surface)
    (. timer (start))
    (events/listen timer goog.Timer/TICK #(game state surface))
    (events/listen js/window event-type/KEYDOWN #(game-keypress state %))
    (events/listen js/window event-type/TOUCHSTART #(game-keypress state %))
    (events/listen js/window event-type/CLICK #(game-click state surface %))))

(defn draw-start-screen
  [surface]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        title-image (dom/getElement "game-title")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas title-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))))

(defn start-keypress
  [state surface event]
  (let [browser-event (.getBrowserEvent event)]
    (if (= (.-keyCode browser-event) key-codes/SLASH)
      (pause-play-music)
      (do
        (swap! state (fn [curr]
                       (assoc curr :stage :play)))
        (start-game state surface)))))

(defn boot-game
  [state surface]
  (draw-start-screen surface)
  (events/listen js/window event-type/KEYPRESS #(start-keypress state surface %)))

(defn ^:export main
  []
  (let [surface (surface)
        state (atom {:world initial-world
                     :stage :start
                     :paleontologist {:x 1 :y 1}
                     :selection nil})]
    (boot-game state surface)))
