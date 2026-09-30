# DNPM - Central Clinical Data Node 


DNPM Central Clinical Node for Model Project (MVH): Component to send data submission reports from DNPM to BfArM

Supposed to be deployed in the central-data-node-deployment project, where it's given a configuration directory.

## Building
Calling `sbt assembly` generates .jar files in the ./core and ./connectors folders respectively. 
These will be moved into the root folder and packaged and released as a Docker image through a GitHub action
defined in ./.github/workflows/release.yml when triggering that action in GitHub.


## Backup Encryption
To create a keypair run following commands: 
`openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -aes256 -out private.pem`
`openssl pkey -in private.pem -pubout -out public.pem`
By default, the CDN looks for the public key file in the config folder, next to config.json. The private key is needed
for decryption. You can find scripts to extract the backup in `https://github.com/dnpm-dip/dip-system-test/tree/main/docs/backup_extract`

