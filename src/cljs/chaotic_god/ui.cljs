(ns chaotic-god.ui
  (:require [goog.dom :as dom]))

(def tile-to-colour
  {\space [90 126 175]
   \d [87 53 1]})

(def tile-to-image
  {\M :mine
   \s :surface-dirt})

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
  [surface x y tile {:keys [sq-width sq-height]}]
  (fill-rect surface [x y sq-width sq-height] (tile tile-to-colour [0 0 0]))
  (stroke-rect surface [x y sq-width sq-height] 1 [0 0 0]))

(defn draw-selection
  [surface selection {:keys [sq-width sq-height]}]
  (when selection
    (stroke-rect surface [(* (:x selection) sq-width) (* (:y selection) sq-height) sq-width sq-height] 1 [43 197 0])))

(defn draw-bones
  [surface bones {:keys [sq-width sq-height]}]
  (let [[canvas] surface
        image (dom/getElement "bones")]
    (doseq [{:keys [x y]} bones]
      (.drawImage canvas image (* x sq-width) (* y sq-height)))))

(defn draw-paleontologist
  [surface {:keys [x y]} {:keys [sq-width sq-height]}]
  (let [[canvas] surface
        image (dom/getElement "paleontologist")]
    (.drawImage canvas image (* x sq-width) (* y sq-height))))

(defn draw-tile
  [surface tunnel-tile x y tile]
  (let [[canvas] surface]
    (if (contains? tile-to-colour tile)
      (draw-square surface x y tile)
      (let [image (if (contains? tile-to-image tile)
                    (tile tile-to-image)
                    tunnel-tile)
            image (dom/getElement (name image))]
        (.drawImage canvas image x y)))))

(defn draw-toolbar
  [surface place-bones {:keys [world-width sq-width sq-height] :as world-settings}]
  (fill-rect surface [(* world-width sq-width) 0 sq-width sq-height] [128 0 0])
  (when place-bones
    (stroke-rect surface [(* world-width sq-width) 0 sq-width sq-height] 1 [255 255 0]))
  (draw-bones surface [{:x world-width :y 0}] world-settings))

(defn draw-instructions-screen
  [surface {:keys [total-width total-height]}]
  (let [[canvas] surface
        instruction-image (dom/getElement "instructions")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (draw-toolbar surface true)
    (.drawImage canvas instruction-image 0 (- total-height 400))))

(defn draw-start-screen
  [surface {:keys [total-width total-height]}]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        title-image (dom/getElement "game-title")
        explanation-image (dom/getElement "explanation")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas title-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))
    (.drawImage canvas explanation-image 0 (- total-height 400))))

(defn win-screen
  [state surface {:keys [total-width total-height]}]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        win-image (dom/getElement "win")
        win-text-image (dom/getElement "win-text")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas win-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))
    (.drawImage canvas win-text-image 0 (- total-height 400))))

(defn lose-screen
  [state surface {:keys [total-width total-height]}]
  (let [[canvas] surface
        god-image (dom/getElement "god")
        lose-image (dom/getElement "lose")
        losing-text-image (dom/getElement "losing-text")]
    (fill-rect surface [0 0 total-width total-height] [102 204 255])
    (.drawImage canvas lose-image 0 0)
    (.drawImage canvas god-image (- total-width 300) (- total-height 300))
    (.drawImage canvas losing-text-image 0 (- total-height 400))))

(defn draw-game-world
  [{:keys [place-bones bones paleontologist selection]} surface world-tiles world-settings]
  (let [[_ width height] surface]
    (doseq [{:keys [tunnel-tile square x y]} world-tiles]
      (draw-tile surface tunnel-tile x y square))
    (draw-toolbar surface place-bones world-settings)
    (draw-bones surface bones world-settings)
    (draw-paleontologist surface paleontologist world-settings)
    (draw-selection surface selection world-settings)))
