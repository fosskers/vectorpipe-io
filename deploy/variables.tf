# From your `~/.aws/credentials
variable "access_key" {}

# From your `~/.aws/credentials
variable "secret_key" {}

# Can be overridden if necessary
variable "region" {
  default = "us-east-1"
}

# Path to your EC2 secret key
variable "pem_path" {}

# The name of your EC2 key
variable "key_name" {}

# Location to dump EMR logs
variable "s3_uri" {}
