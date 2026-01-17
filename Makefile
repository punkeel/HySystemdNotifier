.PHONY: all fmt build clean

all: fmt build

# Format code using Spotless
fmt:
	./gradlew spotlessApply

# Build the project
build:
	./gradlew build

# Clean build artifacts
clean:
	./gradlew clean