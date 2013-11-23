(ns com.gfredericks.git-git.io
  (:refer-clojure :exclude [println])
  (:require [clojure.string :as s]
            [me.raynes.fs :as fs]
            [me.raynes.conch :refer [programs with-programs let-programs]]
            [robert.hooke :refer [add-hook]]))

(def ^:dynamic *dry-run?* false)
(def ^:dynamic *quiet?* false)

(defn ^:private println
  [& args]
  (when-not *quiet?*
    (apply clojure.core/println args)))

(defn ^:private conch-fn-hook
  "Hooks a conch function so that it throws on error on failure and
  returns stdout otherwise."
  [f & args]
  (let [{:keys [exit-code stderr stdout]}
        ;; do we need to check if the last arg is a map??
        (apply f (concat args [{:verbose true}]))]
    (when (pos? @exit-code)
      (throw (ex-info "Subprocess failed!"
                      {:command-args args
                       :stderr stderr
                       :command (or (-> f meta :command-name) (str f))})))
    stdout))

(let-programs [git* "git"]
  (def ^:private git* (vary-meta git* assoc :command-name "git")))
(add-hook #'git* ::conch conch-fn-hook)

(defn git
  [& args]
  (apply println "git" (take-while string? args))
  (when-not *dry-run?* (apply git* args)))

(defn git-RO
  "Like git but runs even when *dry-run?* is true."
  [& args]
  (apply git* args))

(defn git-repo?
  "Checks if the given directory has a .git directory in it."
  [dir]
  (and (fs/directory? dir)
       (fs/directory? (fs/file dir ".git"))))

(defn read-remotes
  [dir]
  (-> (git-RO "remote" "-v" :dir dir)
      (s/split #"\n")
      (->> (map #(s/split % #"\s"))
           (filter #(= "(fetch)" (last %)))
           (map #(vec (take 2 %)))
           (into {}))))

(defn branch->sha
  [repo-dir branch-name]
  (.trim ^String (slurp (fs/file repo-dir
                                 ".git/refs/heads"
                                 branch-name))))

(defn read-branches
  [dir]
  (into {}
        (for [branch-name (fs/list-dir (fs/file dir ".git/refs/heads"))]
          [branch-name (branch->sha dir branch-name)])))

(defn git-clone
  [origin target-dir]
  ;; I don't think we need to set :dir here
  (git "clone" origin (str target-dir)))

(defn git-fetch
  [remote-name cwd]
  (git "fetch" remote-name :dir cwd))

(defn git-add-remote
  [remote-name url cwd]
  (git "remote" "add" remote-name url :dir cwd))

(with-programs [git]
  (defn git-repo-has-commit?
    "Checks if the git repo has the object"
    [repo-dir sha-to-check]
    (let [{:keys [stdout stderr], :as data}
          (git "cat-file" "-t" sha-to-check
               :dir repo-dir
               {:verbose true})]
      (cond (= "commit\n" stdout) true

            ;; it gives this response for sha prefixes
            (.contains ^String stderr "Not a valid object name") false
            ;; and this one for full-length SHAs
            (.contains ^String stderr "unable to find") false

            :else (throw (ex-info "Confusing response from git-cat-file"
                                  {:response data
                                   :repo-dir repo-dir
                                   :sha-to-check sha-to-check}))))))

(defn git-branch-contains?
  "Checks if the branch in the given repo contains the given SHA."
  [repo-dir branch-name sha-to-check]
  (and (git-repo-has-commit? repo-dir sha-to-check)
       (let [branchlist (git-RO "branch" "--contains" sha-to-check :dir repo-dir)
             lines (s/split branchlist #"\n")]
         (boolean (some #{(str "* " branch-name)} lines)))))

(defn git-branch
  [repo-dir branch-name start-sha]
  (git "branch" branch-name start-sha :dir repo-dir))

(defn fast-forward?
  "Checks if the given branch can be fast-forwarded to the given SHA."
  [repo-dir branch-name commit-sha]
  (let [branch-sha (branch->sha repo-dir branch-name)
        ^String merge-base-sha (git-RO "merge-base"
                                       branch-name
                                       commit-sha
                                       :dir repo-dir)]
    (.startsWith merge-base-sha branch-sha)))

(defn branch-checked-out?
  [repo-dir branch-name]
  (let [s (.trim ^String (slurp (fs/file repo-dir ".git/HEAD")))]
    ;; I think there might be extreme edge cases where this gives a
    ;; false positive but who cares
    (.endsWith s (str "/" branch-name))))
