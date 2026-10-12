#!/bin/sh
# SPIKE: recompile only this module against target/libs (the reactor build made them). JDK 25.
cd "$(dirname "$0")" && cp -r src/main/resources/. target/classes/ && javac -nowarn --release 25 -parameters -proc:none -cp "target/libs/*" -d target/classes $(find src/main/java -name '*.java')
