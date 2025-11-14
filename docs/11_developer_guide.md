# Developer guide

## Running tests

```sh
./scripts/test.sh clj
./scripts/test.sh cljs
```


## Creating a release

1. Update `CHANGELOG.md`
2. Set the appropriate version in `project.clj` and commit and push.
3. Create a release on github: https://github.com/metosin/spec-tools/releases

The actual building of the release artifact is done by the CI service.
