# DNPM - Central Clinical Data Node 


DNPM Central Clinical Node for Model Project (MVH): Component to send data submission reports from DNPM to BfArM

Supposed to be deployed in the central-data-node-deployment project, where it's given a configuration directory.

## Building
Calling `sbt assembly` generates .jar files in the ./core and ./connectors folders respectively. 
These will be moved into the root folder and packaged and released as a docker image through a github action
defined in ./.github/workflows/release.yml when triggering that action in github.