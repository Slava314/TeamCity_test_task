# Test task for TeamCity internship

### Technologies
Kotlin, Gradle, TeamCity

### Description

After the start script add new project in TeamCity with local git repository as vcs-root. Make default build and run 100 builds with last commits in batches by 10 builds. After it collect statistics of these builds and write it in `statistics.txt` and `build.txt`. 

### How to run project

* run teamcity locally or provide link in `TEAMCITY_PATH` variable
* in `src/main/resources/secrets/bearer_auth.txt` add auth_token to use TeamCity API
* `./gradlew run` to run script