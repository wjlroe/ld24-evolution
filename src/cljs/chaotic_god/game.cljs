(ns chaotic-god.game
  (:require [goog.dom :as dom]
            [goog.Timer :as timer]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.events.KeyCodes :as key-codes]))

(enable-console-print!)

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

(defn empty-event-handlers
  "Hack hack hack hack"
  []
  (events/removeAll))

(defn wire-up-key-events
  [handler]
  (events/listen js/window event-type/KEYPRESS handler)
  (events/listen js/window event-type/KEYUP handler)
  (events/listen js/window event-type/KEYDOWN handler))

(defn fill-rect
  [[surface] [x y width height] [r g b]]
  (set! (. surface -fillStyle) (str "rgb(" r "," g "," b ")"))
  (.fillRect surface x y width height))

(defn win-screen
  [state surface]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        win-image (dom/getElement "win")
        win-text-image (dom/getElement "win-text")]
    (empty-event-handlers)
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas win-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))
    (.drawImage canvas win-text-image 0 (- total-height 400))))

(defn lose-screen
  [state surface]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        lose-image (dom/getElement "lose")
        losing-text-image (dom/getElement "losing-text")]
    (empty-event-handlers)
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas lose-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))
    (.drawImage canvas losing-text-image 0 (- total-height 400))))

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
  (fill-rect surface [x y sq-width sq-height] (get tile-to-colour tile [0 0 0]))
  (stroke-rect surface [x y sq-width sq-height] 1 [0 0 0]))

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
   ;; first tunnel tile placed
   true
   :tunnel-vertical))

(defn draw-tile
  [surface env coords x y tile]
  (let [[canvas] surface]
    (if (contains? tile-to-colour tile)
      (draw-square surface x y tile)
      (let [image (if (contains? tile-to-image tile)
                    (get tile-to-image tile)
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

(defn draw-paleontologist
  [surface {:keys [x y]}]
  (let [[canvas] surface
        image (dom/getElement "paleontologist")]
    (.drawImage canvas image (* x sq-width) (* y sq-height))))

(defn draw-bones
  [surface bones]
  (let [[canvas] surface
        image (dom/getElement "bones")]
    (doseq [{:keys [x y]} bones]
      (.drawImage canvas image (* x sq-width) (* y sq-height)))))

(defn draw-toolbar
  [surface place-bones]
  (fill-rect surface [(* world-width sq-width) 0 sq-width sq-height] [128 0 0])
  (when place-bones
    (stroke-rect surface [(* world-width sq-width) 0 sq-width sq-height] 1 [255 255 0]))
  (draw-bones surface [{:x world-width :y 0}]))

(defn draw-game-world
  [state surface]
  (let [[_ width height] surface
        {:keys [world selection paleontologist bones place-bones]} state]
    (doseq [[y-index squares] (map-indexed vector world)
            [x-index square]  (map-indexed vector squares)]
      (let [coords {:x x-index :y y-index}
            env (deconstruct-environment world coords)]
        (draw-tile surface env coords (* x-index sq-width) (* y-index sq-height) square)))
    (draw-toolbar surface place-bones)
    (draw-bones surface bones)
    (draw-paleontologist surface paleontologist)
    (when selection
      (stroke-rect surface [(* (:x selection) sq-width) (* (:y selection) sq-height) sq-width sq-height] 1 [43 197 0]))))

(defn edge-left?
  [x y _]
  (= x 1))

(defn edge-right?
  [x y _]
  (= x (- world-width 1)))

(defn hit-bottom?
  [x y {:keys [below]}]
  (= 0 (count below)))

(defn decide-where-to-dig
  [world {:keys [x y] :as dig-coords}]
  (if (or (= y 0) (= y 1)) ;; dig down into earth 2 squares first
    {:x x :y (inc y)}
    (let [env (deconstruct-environment world dig-coords)]
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
  (let [[_ width height] surface]
    (when @running?
      (swap! state (fn [curr]
                     (-> curr
                         (move-paleontologist)
                         (discover-bones)
                         (check-win-condition))))
      (draw-game-world @state surface)
      (when (win-game? @state)
        (println "WIN!!!!")
        (pause-play-game false)
        (win-screen state surface))
      (when (end-game? @state)
        (pause-play-game false)
        (lose-screen state surface)))))

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
                   (println "bones:" (pr-str bones) "place-bones:" (pr-str place-bones))
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
    (println "event:" e)
    (println "br event:" browser-event)
    (when (= key-code key-codes/SPACE)
      (.preventDefault e))
    (when (= (.-type e) event-type/KEYUP)
      ;; handle the actual events
      (case key-code
        key-codes/SLASH
        (pause-play-music)
        key-codes/SPACE
        (pause-play-game)
        ::no-action))))

(defn start-game
  [state surface]
  (let [timer (goog.Timer. 300)]
    (draw-game-world @state surface)
    (. timer (start))
    (empty-event-handlers)
    (events/listen timer goog.Timer/TICK #(game state surface))
    ;; Need to handle all keyboard events
    (wire-up-key-events #(game-keypress state %))

    ;; Touch? No...
    (events/listen js/window event-type/TOUCHSTART #(game-keypress state %))

    ;; Mousy mousy
    (events/listen js/window event-type/MOUSEDOWN #(game-click state surface %))))

(defn draw-instructions-screen
  [surface]
  (let [[canvas] surface
        instruction-image (dom/getElement "instructions")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (draw-toolbar surface true)
    (.drawImage canvas instruction-image 0 (- total-height 400))))

(defn draw-start-screen
  [surface]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        title-image (dom/getElement "game-title")
        explanation-image (dom/getElement "explanation")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas title-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))
    (.drawImage canvas explanation-image 0 (- total-height 400))))

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
            (draw-instructions-screen surface)
            (start-game state surface)))))))

(defn boot-game
  [state surface]
  (draw-start-screen surface)
  (empty-event-handlers)
  (wire-up-key-events #(start-keypress state surface %)))

(defn ^:export main
  []
  (let [surface (surface)
        state (atom {:world initial-world
                     :stage :start
                     :paleontologist {:x 1 :y 1}
                     :bones-examined []
                     :place-bones false
                     :bones #{} ;; where the bones at?
                     :selection nil})]
    (boot-game state surface)))
