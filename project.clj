(defproject chaotic-god "2.0.0-SNAPSHOT"
  :description "A game"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1853"]
                 [io.rkn/core.async "0.1.0-SNAPSHOT"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :repositories {"sonatype-staging"
                 "https://oss.sonatype.org/content/groups/staging/"}
  :source-path "src/clj"
  :cljsbuild {:builds [{
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/game.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]})
