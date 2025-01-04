# Base image
FROM ubuntu:20.04

# Set non-interactive mode for apt-get to prevent prompts
ENV DEBIAN_FRONTEND=noninteractive

# Update and install necessary packages
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    x11-apps \
    libxext6 \
    libxrender1 \
    libxtst6 \
    && rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

# Copy the Java application jar file to the container
COPY out/artifacts/p2p2_jar/p2p2.jar /app/p2p2.jar

# container1 altındaki dosyaları kopyala
COPY container /app/shared_files

# Expose the X11 display environment variable
ENV DISPLAY=:0

# Command to run the Java application
CMD ["java", "-jar", "p2p2.jar"]



