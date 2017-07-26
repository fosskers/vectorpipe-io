# Marks AWS as a resource provider.
provider "aws" {
  access_key = "${var.access_key}"
  secret_key = "${var.secret_key}"
  region     = "${var.region}"
}

# `aws_emr_cluster` is built-in to Terraform. We name ours `emrSparkCluster`.
resource "aws_emr_cluster" "emrSparkCluster" {
  name          = "emrVectorpipeOrcDemo"
  release_label = "emr-5.7.0"         # 2017 July
  applications  = ["Hadoop", "Spark", "Hive"]  # TODO Is Hive really needed here?

  ec2_attributes {
    # key_name         = "${var.key_name}"
    # subnet_id        = "subnet-c5fefdb1"
    instance_profile = "EMR_EC2_DefaultRole"
  }

  instance_group {
    bid_price = "0.05"
    instance_count = 1
    instance_role = "MASTER"
    instance_type = "m3.xlarge"
    name = "emrVectorPipeOrcDemo-MasterGroup"
  }

  instance_group {
    bid_price = "0.05"
    instance_count = 2
    instance_role = "CORE"
    instance_type = "m3.xlarge"
    name = "emrVectorPipeOrcDemo-CoreGroup"
  }

  # Location to dump logs
  log_uri              = "${var.s3_uri}"

  # These can be altered freely, they don't affect the config.
  tags {
    name = "VectorPipe Demo Spark Cluster"
    role = "EMR_DefaultRole"
    env  = "env"
  }

  # configurations = "spark-env.json"  # TODO Unsure if needed.

  # This is the effect of `aws emr create-cluster --use-default-roles`.
  service_role = "EMR_DefaultRole"

  # provisioner "remote-exec" {

  #   # Necessary to massage settings the way AWS wants them.
  #   connection {
  #     type        = "ssh"
  #     user        = "hadoop"
  #     host        = "${aws_emr_cluster.emrSparkCluster.master_public_dns}"
  #     private_key = "${file("${var.pem_path}")}"
  #   }
  # }
}

# Pipable to other programs.
output "emrDNS" {
  value = "${aws_emr_cluster.emrSparkCluster.master_public_dns}"
}
