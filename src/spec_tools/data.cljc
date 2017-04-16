(ns spec-tools.data
  (:require [spec-tools.core :as st]
            [spec-tools.impl :as impl]
            [spec-tools.form :as form]
            [clojure.spec :as s]))

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))

(defn opt? [x]
  (instance? OptionalKey x))

(defn req? [x]
  (not (opt? x)))

(defn wrapped-key? [x]
  (or (opt? x) (instance? RequiredKey x)))

(defn unwrap-key [x]
  (if (wrapped-key? x) (:k x) x))

;;
;; new
;;

(defn spec [x]
  (st/create-spec {:spec x}))

(declare data-spec)

(defn- -map-spec [n data]
  ;; predicate keys
  (if-let [[k' v'] (and (= 1 (count data))
                        (let [[k v] (first data)]
                          (and
                            (not
                              (or (keyword? k)
                                  (wrapped-key? k)))
                            [k v])))]
    (spec (impl/map-of-spec k' (data-spec n v')))
    ;; keyword keys
    (let [m (reduce-kv
              (fn [acc k v]
                (let [kv (unwrap-key k)
                      k1 (if (req? k) "req" "opt")
                      k2 (if-not (qualified-keyword? kv) "-un")
                      ak (keyword (str k1 k2))
                      [k' v'] (if (qualified-keyword? kv)
                                [kv (if (not= kv v) v)]
                                (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name (unwrap-key kv))))]
                                  [k' (data-spec k' v)]))]
                  (-> acc
                      (update ak (fnil conj []) k')
                      (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
              {}
              data)
          defs (::defs m)
          margs (apply concat (dissoc m ::defs))]
      (doseq [[k s] defs]
        (impl/register-spec! k s))
      (spec (impl/keys-spec margs)))))

(defn- -coll-spec [n v proto]
  (when-not (= 1 (count v))
    (throw
      (ex-info
        (str "only single maps allowed in nested " proto)
        {:k n :v v})))
  (let [pred (first v)
        form (form/resolve-form pred)]
    (spec (impl/coll-of-spec (data-spec n pred) form proto))))

(defn data-spec [name x]
  (cond
    (st/spec? x) x
    (s/regex? x) x
    (map? x) (-map-spec name x)
    (set? x) (-coll-spec name x #{})
    (vector? x) (-coll-spec name x [])
    :else (spec x)))
