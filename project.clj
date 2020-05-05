(defproject metosin/spec-tools "0.10.1"
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

  :scm {:name "git"
        :url "https://github.com/metosin/spec-tools"}

  :dependencies [[org.clojure/spec.alpha "0.2.187"]]

  :profiles {:dev {:plugins [[jonase/eastwood "0.3.5"]
                             [lein-tach "1.0.0"]
                             [lein-doo "0.1.11"]
                             [lein-cljsbuild "1.1.7"]
                             [lein-cloverage "1.1.1"]
                             [lein-codox "0.10.7"]]
                   :jvm-opts ^:replace ["-server"]
                   ;:global-vars {*warn-on-reflection* true}
                   :dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/clojurescript "1.10.758" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                                  [criterium "0.4.5"]
                                  [prismatic/schema "1.1.12"]
                                  [org.clojure/test.check "1.0.0"]
                                  [org.clojure/tools.namespace "1.0.0"]
                                  [com.gfredericks/test.chuck "0.2.10"]
                                  ; com.bhauman/spell-spec library doesn't get any updates, so it has to be copied here
                                  ; under spec-tools.spell-spec namespace in order to fix its bugs.
                                  ; If the library gets updated with fixes it would be desirable to switch back to it.
                                  ;[com.bhauman/spell-spec "0.1.1"]
                                  [expound "0.8.4"]
                                  [metosin/ring-swagger "0.26.2" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                                  [metosin/muuntaja "0.6.6"]
                                  [metosin/scjsv "0.6.0"]]}
             :perf {:jvm-opts ^:replace ["-server"]}}
  :aliases {"all" ["with-profile" "dev"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-clj" ["all" "do" ["test"] ["check"]]
            "test-phantom" ["doo" "phantom" "test"]
            "test-advanced" ["doo" "phantom" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}
  ;; Below, :process-shim false is workaround for <https://github.com/bensu/doo/pull/141>
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/out/test.js"
                                   :output-dir "target/out"
                                   :main spec-tools.doo-runner
                                   :optimizations :none
                                   :process-shim false}}
                       {:id "advanced-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/advanced_out/test.js"
                                   :output-dir "target/advanced_out"
                                   :main spec-tools.doo-runner
                                   :optimizations :advanced
                                   :process-shim false}}
                       ;; Node.js requires :target :nodejs, hence the separate
                       ;; build configuration.
                       {:id "node-test"
                        :source-paths ["src" "test/cljc" "test/cljs"]
                        :compiler {:output-to "target/node_out/test.js"
                                   :output-dir "target/node_out"
                                   :main spec-tools.doo-runner
                                   :optimizations :none
                                   :target :nodejs
                                   :process-shim false}}]})
