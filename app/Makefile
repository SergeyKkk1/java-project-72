GRADLEW := ./gradlew

APP_PATH := ./build/install/app/bin/app

install:
	$(GRADLEW) installDist
all: install
run-dist: install
	$(APP_PATH)

build:
	./gradlew build
.PHONY: build