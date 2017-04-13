[![Build Status](https://travis-ci.org/gama-platform/gama.svg?branch=master)](https://travis-ci.org/ANRGenstar/genstar)
[![Language](http://img.shields.io/badge/language-java-brightgreen.svg)](https://www.java.com/)

# Genstar
## Generation of Connected and Spatialized Synthetic Populations

- Core abstractions are define in Core module: IPopulation, IEntity, IAttribute and IValue, together with their direct implemented abtractions 
- Population generation algorithm could be find in Gospl module (generation of synthetic population library)
- Spatialization algorithm could be find in Spll module (Synthetic population localisation library)
- Network algorithm could be find in Spin module (Synthetic population interaction network)

## Maven setup

- just clone the repository
- import as Maven project in your favorite IDE

That's done, all dependencies are handle by maven

- If you want to add dependencies, please insert the corresponding Maven import into the pom.xml of current project you are working on (main pom if it concerns all project)

## Maven compile and deploy

- Run `mvn clean compile` into root folder to build the different Gen* jar (see in `/target` folder)
- If you have a settings.xml correctly configured with [bintray](https://bintray.com/anrgenstar) credentials, you can deploy the Gen* jar on Bintray using `mvn deploy` command
