(defproject chaotic-god "1.0.0-SNAPSHOT"
  :description "A game"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2280"]]
  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [lein-simpleton "1.3.0"]]
  :cljsbuild {:builds
              [{:source-paths ["src/cljs"],
                :compiler {:optimizations :whitespace
                           :source-map "resources/public/game.js.map"
                           :output-dir "resources/public"
                           :output-to "resources/public/game.js",
                           :pretty-print true}}]})
