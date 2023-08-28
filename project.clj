(defproject metosin/spec-tools "0.10.6"
  :description "Clojure(Script) tools for clojure.spec"
  :url "https://github.com/metosin/spec-tools"
  :license {:name "Eclipse Public License", :url "https://www.eclipse.org/legal/epl-2.0/"}
  :test-paths ["test/clj" "test/cljc"]

  :deploy-repositories [["releases" {:url "https://repo.clojars.org/"
                                     :username "metosinci"
                                     :password :env
                                     :sign-releases false}]]

  :codox {:output-path "doc"
          :source-uri "https://github.com/metosin/spec-tools/{version}/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}

  :scm {:name "git"
        :url "https://github.com/metosin/spec-tools"}

  :dependencies [[org.clojure/spec.alpha "0.3.218"]]

  :profiles {:dev {:plugins [[jonase/eastwood "1.4.0"]
                             [lein-tach "1.1.0"]
                             [lein-doo "0.1.11"]
                             [lein-cljsbuild "1.1.8"]
                             [lein-cloverage "1.2.4"]
                             [lein-codox "0.10.8"]
                             [lein-pprint "1.3.2"]]
                   :jvm-opts ^:replace ["-server"]
                   ;:global-vars {*warn-on-reflection* true}
                   :dependencies [[org.clojure/clojure "1.11.1"]
                                  [org.clojure/clojurescript "1.11.60"]
                                  [criterium "0.4.6"]
                                  [prismatic/schema "1.4.1"]
                                  [org.clojure/test.check "1.1.1"]
                                  [org.clojure/tools.namespace "1.4.4"]
                                  [com.gfredericks/test.chuck "0.2.14"]
                                  ; com.bhauman/spell-spec library doesn't get any updates, so it has to be copied here
                                  ; under spec-tools.spell-spec namespace in order to fix its bugs.
                                  ; If the library gets updated with fixes it would be desirable to switch back to it.
                                  ;[com.bhauman/spell-spec "0.1.1"]
                                  [expound "0.9.0"]
																																		[metosin/muuntaja "0.6.8"]
                                  [metosin/ring-swagger "0.26.2"]
                                  [metosin/jsonista "0.3.7"]
                                  [metosin/scjsv "0.6.2"]]}
             :perf {:jvm-opts ^:replace ["-server"]}}
  :aliases {"all" ["with-profile" "dev"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-clj" ["all" "do" ["test"] ["check"]]
            "test-browser" ["doo" "chrome-headless" "test"]
            "test-advanced" ["doo" "chrome-headless" "advanced-test"]
            "test-node" ["doo" "node" "node-test"]}
  :doo {:paths {:karma "node_modules/karma-cli/bin/karma"}}
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
