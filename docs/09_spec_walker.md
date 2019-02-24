# Spec Walker

```clj
(require '[spec-tools.core :as st])
```

A multimethod `st/walk` taking a spec, value, accept function and options to walk over both specs and values. Used by `st/coerce`, which transforms specs values using spec transformers.
