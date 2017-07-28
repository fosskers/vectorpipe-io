# Marks AWS as a resource provider.
provider "aws" {
  access_key = "${var.access_key}"
  secret_key = "${var.secret_key}"
  region     = "${var.region}"
}

# `aws_emr_cluster` is built-in to Terraform. We name ours `emrSparkCluster`.
resource "aws_emr_cluster" "emrSparkCluster" {
  name          = "emrVectorpipeOrcDemo"
  release_label = "emr-5.7.0"            # 2017 July

  # This it will work if only `Spark` is named here, but booting the cluster seems
  # to be much faster when `Hadoop` is included. Ingests, etc., will succeed
  # even if `Hadoop` is missing here.
  applications = ["Hadoop", "Spark"]

  ec2_attributes {
    instance_profile = "EMR_EC2_DefaultRole" # This seems to be the only necessary field.
  }

  # MASTER group must have an instance_count of 1.
  # `xlarge` seems to be the smallest type they'll allow (large didn't work).
  instance_group {
    bid_price      = "0.05"
    instance_count = 1
    instance_role  = "MASTER"
    instance_type  = "m3.xlarge"
    name           = "emrVectorPipeOrcDemo-MasterGroup"
  }

  instance_group {
    bid_price      = "0.05"
    instance_count = 2
    instance_role  = "CORE"
    instance_type  = "m3.xlarge"
    name           = "emrVectorPipeOrcDemo-CoreGroup"
  }

  # Location to dump logs
  log_uri = "${var.s3_uri}"

  # These can be altered freely, they don't affect the config.
  tags {
    name = "VectorPipe Demo Spark Cluster"
    role = "EMR_DefaultRole"
    env  = "env"
  }

  # This is the effect of `aws emr create-cluster --use-default-roles`.
  service_role = "EMR_DefaultRole"
}

# Pipable to other programs.
output "emrID" {
  value = "${aws_emr_cluster.emrSparkCluster.id}"
}
