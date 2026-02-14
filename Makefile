APP_DIR := app

.PHONY: build
build:
	$(MAKE) -C $(APP_DIR) build
