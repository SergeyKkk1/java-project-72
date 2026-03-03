APP_DIR := app
GRADLE_HOME := $(CURDIR)/$(APP_DIR)/.gradle

.PHONY: build
build:
	GRADLE_USER_HOME=$(GRADLE_HOME) $(MAKE) -C $(APP_DIR) build
