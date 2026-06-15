#!/bin/zsh
./gradlew :icure-cli:assemble
./gradlew :lib:assemble
rm -rf scratch
mkdir -p scratch
cd scratch
unzip ../icure-cli/build/distributions/icure-cli-1.2-SNAPSHOT.zip
icure-cli-1.2-SNAPSHOT/bin/icure-cli
