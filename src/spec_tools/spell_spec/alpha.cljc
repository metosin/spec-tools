(ns spec-tools.spell-spec.alpha
  (:refer-clojure :exclude [keys])
  (:require
    [#?(:clj  clojure.spec.alpha
        :cljs cljs.spec.alpha)
     :as s]
    #?(:cljs [goog.string]))
  #?(:cljs (:require-macros [spec-tools.spell-spec.alpha :refer [keys warn-keys strict-keys warn-strict-keys]])))

(def ^:dynamic *value* {})

(def ^:dynamic *warn-only* false)

(def default-warning-handler #(some->> % ::warning-message
                                       (str "SPEC WARNING: ")
                                       println))

(def ^:dynamic *warning-handler* default-warning-handler)

;; this is a simple step function to determine the threshold
;; no need to figure out the numeric function
(defn length->threshold [len]
  (condp #(<= %2 %1) len
    4 0
    5 1
    6 2
    11 3
    20 4
    (int (* 0.2 len))))

(def ^:dynamic *length->threshold* length->threshold)

;; ----------------------------------------------------------------------
;; similar keywords

(defn- next-row
  [previous current other-seq]
  (reduce
    (fn [row [diagonal above other]]
      (let [update-val (if (= other current)
                         diagonal
                         (inc (min diagonal above (peek row))))]
        (conj row update-val)))
    [(inc (first previous))]
    (map vector previous (next previous) other-seq)))

(defn- levenshtein
  "Compute the levenshtein distance between two [sequences]."
  [sequence1 sequence2]
  (peek
    (reduce (fn [previous current] (next-row previous current sequence2))
            (map #(identity %2) (cons nil sequence2) (range))
            sequence1)))

(defn- similar-key* [thresh ky ky2]
  (let [dist (levenshtein (str ky) (str ky2))]
    (when (<= dist thresh)
      dist)))

(defn- similar-key [ky ky2]
  (let [starts-with? #?(:clj (fn [a b] (.startsWith a b)) :cljs (fn [a b] (goog.string/startsWith a b)))
        min-len (apply min (map (comp count #(if (starts-with? % ":") (subs % 1) %) str) [ky ky2]))]
    (similar-key* (#?(:clj  *length->threshold*
                      :cljs length->threshold)
                    min-len) ky ky2)))

;; a tricky part is is that a keyword is not considered misspelled
;; if its substitute is already present in the original map
(defn likely-misspelled [known-keys]
  (fn [key]
    (when-not (known-keys key)
      (->> known-keys
           (filter #(similar-key % key))
           (remove (set (#?(:clj  clojure.core/keys
                            :cljs cljs.core/keys)
                          *value*)))
           not-empty))))

(defn not-misspelled [known-keys] (complement (likely-misspelled known-keys)))

(defn- most-similar-to [key known-keys]
  (->> ((likely-misspelled known-keys) key)
       (map (juxt #(levenshtein (str %) (str key)) identity))
       (filter first)
       (sort-by first)
       (map second)
       not-empty))

;; ----------------------------------------------------------------------
;; Warning only spec
;; ----------------------------------------------------------------------
;; specs that check but only print warnings

(defmulti warning-message* (fn [a _] (:expound.spec.problem/type a)))

(defmethod warning-message* :default [{:keys [val pred]} value]
  (str "Value " (pr-str val) " failed predicate " (pr-str pred) " in "
       (binding [*print-level* 1]
         (pr-str value))))

(defmethod warning-message* ::misspelled-key [{:keys [val ::misspelled-key ::likely-misspelling-of] :as prob} value]
  (str "possible misspelled map key "
       (pr-str misspelled-key)
       " should probably be "
       (if (= 1 (count likely-misspelling-of))
         (pr-str (first likely-misspelling-of))
         (str "one of " (pr-str (take 3 likely-misspelling-of))))
       " in "
       (binding [*print-level* 1]
         (pr-str value))))

(defmethod warning-message* ::unknown-key [{:keys [val ::unknown-key] :as prob} value]
  (str "unknown map key "
       (pr-str unknown-key)
       " in "
       (binding [*print-level* 1]
         (pr-str value))))

(defn- problem-warnings [value problems]
  (#?@(:clj  [binding [*out* *err*]]
       :cljs [do])
    (doseq [prob problems]
      (*warning-handler*
        (assoc prob
          ::value value
          ::warning-message (warning-message* prob value))))))

(defn warning-spec
  "Wraps a spec and will behave just like the wrapped spec but if
  `spec-tools.spell-spec.alpha/*warn-only*` is bound to `true` around spec
  validation calls, this will print warnings instead of failing the
  validation.

  Bind the `spec-tools.spell-spec.alpha/*warning-handler*` if you want to handle
  the emmitted warnings."
  [wspec]
  (reify
    s/Specize
    (specize* [s] s)
    (specize* [s _] s)
    s/Spec
    (conform* [_ x]
      (binding [*value* x]
        (let [result (s/conform* wspec x)]
          (if (not= ::s/invalid result)
            result
            (if *warn-only*
              (do (problem-warnings x (::s/problems (s/explain-data wspec x)))
                  x)
              ::s/invalid)))))
    (unform* [_ x] (s/unform* wspec x))
    (explain* [_ path via in x]
      (binding [*value* x]
        (when-let [problems (not-empty (s/explain* wspec path via in x))]
          (if *warn-only*
            (problem-warnings x problems)
            problems))))
    (gen* [_ a b c]
      (s/gen* wspec a b c))
    (with-gen* [_ gfn]
      (s/with-gen* wspec gfn))
    (describe* [_] (s/describe wspec))))

;; ----------------------------------------------------------------------
;; CLJS compatibility helpers
;; ----------------------------------------------------------------------

#?(:clj
   (defn in-cljs-compile? []
     ;; use a variable that isn't commonly bound when reloading clojure files
     (when-let [v (resolve 'cljs.analyzer/*cljs-static-fns*)]
       (thread-bound? v))))

#?(:clj
   (defn spec-ns-var [var-sym]
     (symbol
       (if (in-cljs-compile?)
         "cljs.spec.alpha"
         "clojure.spec.alpha")
       (name var-sym))))

;; ----------------------------------------------------------------------
;; Misspelled and Unknown-keys
;; ----------------------------------------------------------------------

(defn map-explain
  "A spec wrapper that takes a function and a spec, and returns a spec
  that will map a function over the spec problems emmitted by the call
  to `clojure.spec.alpha/explain*` on that spec.

  Useful for enhancing the spec problems with extra data."
  [f aspec]
  (reify
    s/Specize
    (specize* [s] s)
    (specize* [s _] s)
    s/Spec
    (conform* [_ x] (s/conform* aspec x))
    (unform* [_ x] (s/unform* aspec x))
    (explain* [_ path via in x]
      (not-empty (map f (s/explain* aspec path via in x))))
    (gen* [_ a b c]
      (s/gen* aspec a b c))
    (with-gen* [_ gfn]
      (s/with-gen* aspec gfn))
    (describe* [_] (s/describe aspec))))

(defn enhance-problem [{:keys [pred val] :as prob}]
  (if-let [sim (when-let [known-keys (cond
                                       (set? pred) pred
                                       (= 'spec-tools.spell-spec.alpha/not-misspelled (first pred))
                                       (second pred)
                                       :else nil)]
                 (most-similar-to val known-keys))]
    (assoc prob
      :expound.spec.problem/type ::misspelled-key
      ;; limiting the predicate to the matches
      ;; makes the default expound errors pretty good
      ;; but could be confusing in other circumstances
      :pred (set sim)
      ::misspelled-key val
      ::likely-misspelling-of sim)
    (assoc prob
      :expound.spec.problem/type ::unknown-key
      ::unknown-key val)))

#?(:clj
   (defmacro not-misspelled-spec
     "A spec that verifies that a keyword is not a near misspelling of
     the provided set of keywords. Will produce problems with a
     `:expound.spec.problem/type` of `:spec-tools.spell-spec.alpha/misspelled-key`"
     [known-keys]
     (assert (and (set? known-keys)
                  (every? keyword? known-keys))
             "Must provide a set of keywords.")
     `(map-explain enhance-problem (~(spec-ns-var 'spec) (not-misspelled ~known-keys)))))

#?(:clj
   (defmacro known-keys-spec
     "A spec that verifies that a keyword is a member of the provided set
     of keywords. Will produce problems with a
     `:expound.spec.problem/type` of both
     `:spec-tools.spell-spec.alpha/misspelled-key` and
     `:spec-tools.spell-spec.alpha/unknown-key`"
     [known-keys]
     (assert (and (set? known-keys)
                  (every? keyword? known-keys))
             "Must provide a set of keywords.")
     `(map-explain enhance-problem (~(spec-ns-var 'spec) ~known-keys))))


(defn- get-known-keys [{:keys [req opt req-un opt-un]}]
  (let [key-specs (into (set (filterv keyword? (flatten req))) opt)
        un-key-specs (into (set (filterv keyword? (flatten req-un))) opt-un)]
    (assert (every? #(and (keyword? %) (namespace %)) (concat key-specs un-key-specs))
            "all keys must be namespace-qualified keywords")
    (into key-specs
          (mapv #(-> % name keyword) un-key-specs))))

(defn pre-check
  "Similar to `clojure.spec.alpha/and` but treats the all the specs
  except the last one as pre-conditions for validity purposes but behaves
  like a proxy to the last spec provided for everything else."
  [& specs]
  (let [pre (butlast specs)
        spec (last specs)]
    (reify
      s/Specize
      (specize* [s] s)
      (specize* [s _] s)
      s/Spec
      (conform* [_ x]
        (if (every? #(s/valid? % x) pre)
          (s/conform* spec x)
          ::s/invalid))
      (unform* [_ x] (s/unform* spec x))
      (explain* [_ path via in x]
        (if-let [problems (some #(s/explain* % path via in x) pre)]
          problems
          (s/explain* spec path via in x)))
      (gen* [_ a b c]
        (s/gen* spec a b c))
      (with-gen* [_ gfn]
        (s/with-gen* spec gfn))
      (describe* [_] (s/describe spec)))))

;; ----------------------------------------------------------------------
;; Main API specs
;; ----------------------------------------------------------------------

#?(:clj
   (defmacro keys
     "Use `spec-tools.spell-spec.alpha/keys` the same way that you would use
  `clojure.spec.alpha/keys` keeping in mind that the spec it creates
  will fail for keys that are misspelled.

  `spec-tools.spell-spec.alpha/keys` is a spec macro that has the same signature and
  behavior as clojure.spec.alpha/keys. In addition to performing the
  same checks that `clojure.spec.alpha/keys` does, it checks to see if
  there are unknown keys present which are also close misspellings of
  the specified keys.

  An important aspect of this behavior is that the map is left open to
  other keys that are not close misspellings of the specified
  keys. Keeping maps open is an important pattern in Clojure which
  allows one to simply add behavior to a program by adding extra data
  to maps that flow through functions. spec-tools.spell-spec.alpha/keys keeps
  this in mind and is fairly conservative in its spelling checks."
     [& args]
     `(pre-check
        (warning-spec (~(spec-ns-var 'map-of)
                        (not-misspelled-spec ~(get-known-keys args)) any?))
        (~(spec-ns-var 'keys) ~@args))))

#?(:clj
   (defmacro strict-keys
     "`strict-keys` is very similar to `spec-tools.spell-spec.alpha/keys` except
  that the map is closed to keys that are not specified.

  `strict-keys` will produce two types of validation problems: one for
  misspelled keys and one for unknown keys.

  This spec macro violates the Clojure idiom of keeping maps open. However,
  there are some situations where this behavior is warranted. I
  strongly advocate for the use of `spec-tools.spell-spec.alpha/keys` over
  `strict-keys`"
     [& args]
     `(pre-check ;~(spec-ns-var 'and)
        (warning-spec (~(spec-ns-var 'map-of)
                        (known-keys-spec ~(get-known-keys args)) any?))
        (~(spec-ns-var 'keys) ~@args))))

;; ----------------------------------------------------------------------
;; Warning only specs
;; ----------------------------------------------------------------------

(defn warn-only-impl
  "A spec wrapper that forces warn only behavior."
  [spec]
  (reify
    s/Specize
    (specize* [s] (s/specize* spec))
    (specize* [s _] (s/specize* spec))
    s/Spec
    (conform* [_ x]
      (binding [*warn-only* true]
        (s/conform* spec x)))
    (unform* [_ x] (s/unform* spec x))
    (explain* [_ path via in x]
      (binding [*warn-only* true]
        (s/explain* spec path via in x)))
    (gen* [_ a b c] (s/gen* spec a b c))
    (with-gen* [_ gfn] (s/with-gen* spec gfn))
    (describe* [_] (s/describe* spec))))

#?(:clj
   (defmacro warn-keys
     "This macro is the same as `spec-tools.spell-spec.alpha/keys` macro except
  it will print warnings instead of failing when misspelled keys are discovered."
     [& args]
     `(spec-tools.spell-spec.alpha/warn-only-impl (spec-tools.spell-spec.alpha/keys ~@args))))

#?(:clj
   (defmacro warn-strict-keys
     "This macro is similar to `spec-tools.spell-spec.alpha/strict-keys` macro
  except that it will print warnings for unknown and misspelled keys
  instead of failing."
     [& args]
     `(spec-tools.spell-spec.alpha/warn-only-impl (spec-tools.spell-spec.alpha/strict-keys ~@args))))
