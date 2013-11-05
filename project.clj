(defproject com.gfredericks/git-git "0.1.0-SNAPSHOT"
  :description "Managing your swarm of git repositories."
  :url "https://github.com/fredericksgary/git-git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[me.raynes/conch "0.5.2"]
                 [me.raynes/fs "1.4.5"]
                 [org.clojure/clojure "1.5.1"]
                 [robert/hooke "1.3.0"]]

  ;; building
  :aot :all
  :main com.gfredericks.git-git
  :uberjar-name "git-git.jar")
