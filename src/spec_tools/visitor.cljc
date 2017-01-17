(ns spec-tools.visitor
  "Tools for walking spec definitions."
  (:require [clojure.spec :as s]
            [clojure.set :as set]))

(defn strip-fn-if-needed [form]
  (let [head (first form)]
    ;; Deal with the form (clojure.core/fn [%] (foo ... %))
    ;; We should just use core.match...
    (if (and (= (count form) 3) (= head 'clojure.core/fn))
      (nth form 2)
      form)))

(defn- normalize-symbol [kw]
  (case (and (symbol? kw) (namespace kw))
    "cljs.core" (symbol "clojure.core" (name kw))
    "cljs.spec" (symbol "clojure.spec" (name kw))
    kw))

(defn- formize [spec] (if (seq? spec) spec (s/form spec)))

(defn- spec-dispatch
  [spec accept]
  (cond
    (or (s/spec? spec) (s/regex? spec) (keyword? spec))
    (let [form (s/form spec)]
      (if (not= form :clojure.spec/unknown)
        (if (seq? form)
          (normalize-symbol (first form))
          (spec-dispatch form accept))
        spec))
    (set? spec) ::set
    (seq? spec) (normalize-symbol (first (strip-fn-if-needed spec)))
    :else (normalize-symbol spec)))

(defmulti visit
  "Walk a spec definition. Takes two arguments, the spec and the accept
  function, and returns the result of calling the accept function.

  The accept function is called with three arguments: the dispatch term for the
  spec (see below), the spec itself and the vector with the results of
  recursively walking the children of the spec.

  The dispatch term is one of the following
  * if the spec is a function call: a fully qualified symbol for the function
    with the following exceptions:
    - cljs.core symbols are converted to clojure.core symbols
    - cljs.spec symbols are converted to clojure.spec symbols
  * if the spec is a set: :spec-tools.visitor/set
  * otherwise: the spec itself"
  spec-dispatch :default ::default)

(defmethod visit ::set [spec accept]
  (accept ::set spec (vec (if (keyword? spec) (s/form spec) spec))))

(defmethod visit 'clojure.spec/every [spec accept]
  (let [[_ inner-spec & kwargs] (formize spec)]
    (accept 'clojure.spec/every spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/tuple [spec accept]
  (let [[_ & inner-specs] (formize spec)]
    (accept 'clojure.spec/tuple spec (mapv #(visit % accept) inner-specs))))

(defmethod visit 'clojure.spec/* [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/* spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/+ [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/+ spec [(visit inner-spec accept)])))

(defmethod visit 'clojure.spec/keys [spec accept]
  (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)]
    (accept 'clojure.spec/keys spec (mapv #(visit % accept) (concat req req-un opt opt-un)))))

(defmethod visit 'clojure.spec/or [spec accept]
  (let [[_ & {:as inner-spec-map}] (s/form spec)]
    (accept 'clojure.spec/or spec (mapv #(visit % accept) (vals inner-spec-map)))))

(defmethod visit 'clojure.spec/and [spec accept]
  (let [[_ & inner-specs] (s/form spec)]
    (accept 'clojure.spec/and spec (mapv #(visit % accept) inner-specs))))

(defmethod visit 'clojure.spec/nilable [spec accept]
  (let [[_ inner-spec] (s/form spec)]
    (accept 'clojure.spec/nilable spec [(visit inner-spec accept)])))

(defmethod visit ::default [spec accept]
  (accept (spec-dispatch spec accept) spec nil))
