(defproject metosin/spec-tools "0.2.1-SNAPSHOT"
  :description "Clojure(Script) tools for clojure.spec"
  :url "https://github.com/metosin/spec-tools"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :test-paths ["test/clj" "test/cljc"]
  :deploy-repositories [["releases" :clojars]]
  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/spec-tools/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :dependencies [[org.clojure/spec.alpha "0.1.123"]]

  :profiles {:dev {:plugins [[jonase/eastwood "0.2.3"]
                             [lein-tach "0.3.0"]
                             [lein-doo "0.1.7"]
                             [lein-cljsbuild "1.1.6"]
                             [lein-cloverage "1.0.9"]
                             [lein-codox "0.10.3"]]
                   :jvm-opts ^:replace ["-server"]
                   ;:global-vars {*warn-on-reflection* true}
                   :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                                  [org.clojure/clojurescript "1.9.562"]
                                  [criterium "0.4.4"]
                                  [prismatic/schema "1.1.6"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.gfredericks/test.chuck "0.2.7"]
                                  [metosin/scjsv "0.4.0"]]}
             :perf {:jvm-opts ^:replace ["-server"]}}
  :aliases {"all" ["with-profile" "dev"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-clj" ["all" "do" ["test"] ["check"]]
            "test-phantom" ["doo" "phantom" "test"]
            "test-advanced" ["doo" "phantom" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main spec-tools.doo-runner
                                   :optimizations :none}}
                       {:id "advanced-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/advanced_out/test.js"
                                   :output-dir "target/advanced_out"
                                   :main spec-tools.doo-runner
                                   :optimizations :advanced}}
                       ;; Node.js requires :target :nodejs, hence the separate
                       ;; build configuration.
                       {:id "node-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/node_out/test.js"
                                   :output-dir "target/node_out"
                                   :main spec-tools.doo-runner
                                   :optimizations :none
                                   :target :nodejs}}]})
