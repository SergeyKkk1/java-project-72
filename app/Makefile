GRADLEW := ./gradlew
GRADLE_HOME ?= $(CURDIR)/.gradle
GRADLE := GRADLE_USER_HOME=$(GRADLE_HOME) $(GRADLEW) --no-daemon

APP_PATH := ./build/install/app/bin/app

install:
	$(GRADLE) installDist
all: install
run-dist: install
	$(APP_PATH)

build:
	$(GRADLE) build
.PHONY: build
