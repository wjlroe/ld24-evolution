(ns chaotic-god.game
  (:require [goog.dom :as dom]
            [goog.Timer :as timer]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.events.KeyCodes :as key-codes]
            [chaotic-god.ui :as ui]))

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
(def min-num-bones 8)
(def bone-selection-square {:x 14 :y 0})
(def world-settings
  {:sq-width sq-width
   :sq-height sq-height
   :world-width world-width
   :world-height world-height
   :total-width total-width
   :total-height total-height
   :bone-selection-square bone-selection-square})

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

(defn donothing [])

(defn empty-event-handlers
  "Hack hack hack hack"
  []
  (events/removeAll))

(defn wire-up-key-events
  [handler]
  (events/listen js/window event-type/KEYPRESS handler)
  (events/listen js/window event-type/KEYUP handler)
  (events/listen js/window event-type/KEYDOWN handler))

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
  (tunnel? (nth (second (reverse above)) x)))

(defn tunnel-below?
  [x y {:keys [below]}]
  (tunnel? (nth (first below) x)))

(defn tunnel-depth
  [x y {:keys [above]}]
  (count (take-while tunnel? (reverse (map #(nth % x) above)))))

(defn hit-bottom?
  [x y {:keys [below]}]
  (= 0 (count below)))

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

(defn world-tiles
  [world]
  (for [[y-index squares] (map-indexed vector world)
        [x-index square]  (map-indexed vector squares)]
    (let [coords {:x x-index :y y-index}
          env (deconstruct-environment world coords)
          tunnel-tile (which-tunnel-tile env coords)]
      {:tunnel-tile tunnel-tile
       :square square
       :x (* x-index sq-width)
       :y (* y-index sq-height)})))

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

(defn discover-bones
  [state]
  (let [{:keys [paleontologist bones bones-examined]} state]
    (let [examined (if (contains? bones paleontologist)
                     (conj bones-examined paleontologist)
                     bones-examined)]
      (assoc state :bones-examined examined))))

(defn check-win-condition
  [state]
  (let [{:keys [bones-examined]} state]
    (if (= (count bones-examined) min-num-bones)
      (assoc state :stage :win)
      state)))

(defn pause-play-game
  ([forced]
     (reset! running? forced))
  ([]
     (swap! running? not)))

(defn end-game?
  [{:keys [paleontologist] :as state}]
  (do
    (= (:y paleontologist) (dec world-height))))

(defn win-game?
  [state]
  (= :win (:stage state)))

(defn game
  [state surface]
  (let [[_ width height] surface
        {:keys [world selection paleontologist bones place-bones]} @state
        world-tiles (world-tiles world)]
    (when @running?
      (swap! state (fn [curr]
                     (-> curr
                         (move-paleontologist)
                         (discover-bones)
                         (check-win-condition))))
      (ui/draw-game-world @state surface world-tiles world-settings)
      (when (win-game? @state)
        (js/console.log "WIN!!!!")
        (pause-play-game false)
        (ui/win-screen state surface world-settings))
      (when (end-game? @state)
        (pause-play-game false)
        (ui/lose-screen state surface world-settings)))))

(defn abs-pos-to-coords
  [x y]
  [(- (int (Math/ceil (/ x sq-width))) 1)
   (- (int (Math/ceil (/ y sq-height))) 1)])

(defn game-click
  [state surface event]
  (swap! state (fn [curr]
                 (let [abs-x (.-offsetX event)
                       abs-y (.-offsetY event)
                       [x y] (abs-pos-to-coords abs-x abs-y)
                       {:keys [bones place-bones]} curr]
                   (js/console.log "bones:" (pr-str bones) "place-bones:" (pr-str place-bones))
                   (if (= [x y] [(:x bone-selection-square) (:y bone-selection-square)])
                     (assoc curr :place-bones true)
                     (if (:place-bones curr)
                       (assoc curr
                         :bones (conj (:bones curr) {:x x :y y})
                         :place-bones false)
                       curr))))))

(defn game-keypress
  [state e]
  (let [browser-event (.getBrowserEvent e)
        key-code (.-keyCode browser-event)]
    (.log js/console "event:" e)
    (.log js/console "br event:" browser-event)
    (when (= key-code key-codes/SPACE)
      (.preventDefault e))
    (when (= (.-type e) event-type/KEYUP)
      ;; handle the actual events
      (case key-code
        key-codes/SLASH
        (pause-play-music)
        key-codes/SPACE
        (pause-play-game)))))

(defn start-game
  [state surface]
  (let [timer (goog.Timer. 300)]
    (. timer (start))
    (empty-event-handlers)
    (events/listen timer goog.Timer/TICK #(game state surface))
    ;; Need to handle all keyboard events
    (wire-up-key-events #(game-keypress state %))

    ;; Touch? No...
    (events/listen js/window event-type/TOUCHSTART #(game-keypress state %))

    ;; Mousy mousy
    (events/listen js/window event-type/MOUSEDOWN #(game-click state surface %))))

(defn start-keypress
  [state surface event]
  (let [browser-event (.getBrowserEvent event)
        key-code (.-keyCode browser-event)]
    (when (= key-code key-codes/SPACE)
      (.preventDefault event))
    ;; Ignore everything but KEYUPs
    (when (= (.-type event) event-type/KEYUP)
      (if (= (.-keyCode browser-event) key-codes/SLASH)
        (pause-play-music)
        (do
          (swap! state (fn [curr]
                         (if (= (:stage curr) :start)
                           (assoc curr :stage :instructions)
                           (assoc curr :stage :play))))
          (if (= :instructions (:stage @state))
            (ui/draw-instructions-screen surface world-settings)
            (start-game state surface)))))))

(defn boot-game
  [state surface]
  (ui/draw-start-screen surface world-settings)
  (empty-event-handlers)
  (wire-up-key-events #(start-keypress state surface %)))

(let [surface (ui/surface)
      state (atom {:world initial-world
                   :stage :start
                   :paleontologist {:x 1 :y 1}
                   :bones-examined []
                   :place-bones false
                   :bones #{} ;; where the bones at?
                   :selection nil})]
  (boot-game state surface))
