#!/bin/bash

# Compile and start the first microservice
cd HW3-Micro-A
mvn clean install
mvn spring-boot:run &

# Compile and start the second microservice
cd HW3-Micro-B
mvn clean install 
mvn spring-boot:run &

# Compile and launch the main app
cd HW3-main
mvn clean install
java -jar target/hw2-0.0.1-SNAPSHOT.jar
