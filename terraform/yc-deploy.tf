# Объявление переменных для конфиденциальных параметров

locals {
  zone              = "ru-central1-a"
  target_folder_id  = "b1gni3u9dk10jvj2gbpo"
  registry_name     = "todo-react"
  sa_name           = "yoo"
  network_name      = "network"
  subnet_name       = "subnet"
  vm_name           = "todo-react"
  image_id          = "fd8jqd7hb16epiac8lla"
  postgres_user     = "user"
  postgres_password = file("postgres-password.txt")

  k8s_version           = "1.33"
  zone_a_v4_cidr_blocks = "10.1.0.0/16" # CIDR block for the subnet in the ru-central1-a availability zone
  master_v4_cidr_blocks = "10.2.0.0/16" # CIDR block for the cluster
  node_v4_cidr_blocks   = "10.3.0.0/16" # CIDR block for the cluster node group
}

# Настройка провайдера

terraform {
  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = ">= 0.47.0"
    }
    kubernetes = {
      source = "hashicorp/kubernetes"
    }
  }
  required_version = ">= 0.14.8"
}

provider "yandex" {
  service_account_key_file = "sa-key.json"
  zone                     = local.zone
}

# Создание репозитория Сontainer Registry

resource "yandex_container_registry" "my-registry" {
  name      = local.registry_name
  folder_id = local.target_folder_id
}

# Создание сервисного аккаунта

resource "yandex_iam_service_account" "registry-sa" {
  name      = local.sa_name
  folder_id = local.target_folder_id
}

# Назначение роли сервисному аккаунту

resource "yandex_resourcemanager_folder_iam_member" "registry-sa-role-images-puller" {
  folder_id = local.target_folder_id
  role      = "container-registry.images.puller"
  member    = "serviceAccount:${yandex_iam_service_account.registry-sa.id}"
}

# Создание облачной сети

resource "yandex_vpc_network" "docker-vm-network" {
  name = local.network_name
}

# Создание подсети

resource "yandex_vpc_subnet" "docker-vm-network-subnet-a" {
  name           = local.subnet_name
  zone           = local.zone
  v4_cidr_blocks = ["192.168.1.0/24"]
  network_id     = yandex_vpc_network.docker-vm-network.id
}

# Создание загрузочного диска

resource "yandex_compute_disk" "boot-disk" {
  name     = "bootvmdisk"
  type     = "network-hdd"
  zone     = local.zone
  size     = "20"
  image_id = local.image_id
}

# Создание ВМ

resource "yandex_compute_instance" "docker-vm" {
  name                      = local.vm_name
  platform_id               = "standard-v3"
  zone                      = local.zone
  service_account_id        = yandex_iam_service_account.registry-sa.id
  allow_stopping_for_update = true

  resources {
    cores  = 4
    memory = 4
  }

  boot_disk {
    disk_id = yandex_compute_disk.boot-disk.id
  }

  network_interface {
    subnet_id = yandex_vpc_subnet.docker-vm-network-subnet-a.id
    nat       = true
  }

  metadata = {
    user-data = "${file("user-data.yaml")}"
  }
}

# Cоздание БД PostgreSQL

resource "yandex_mdb_postgresql_database" "my_db" {
  cluster_id = yandex_mdb_postgresql_cluster.my_cluster.id
  name       = "testdb"
  owner      = yandex_mdb_postgresql_user.my_user.name
  lc_collate = "en_US.UTF-8"
  lc_type    = "en_US.UTF-8"
  extension {
    name = "uuid-ossp"
  }
  extension {
    name = "xml2"
  }
}

resource "yandex_mdb_postgresql_user" "my_user" {
  cluster_id = yandex_mdb_postgresql_cluster.my_cluster.id
  name       = local.postgres_user
  password   = local.postgres_password
}

resource "yandex_mdb_postgresql_cluster" "my_cluster" {
  name        = "test"
  environment = "PRESTABLE"
  network_id  = yandex_vpc_network.docker-vm-network.id
  folder_id   = local.target_folder_id

  config {
    version = 18
    resources {
      resource_preset_id = "s2.micro"
      disk_type_id       = "network-ssd"
      disk_size          = 16
    }
  }

  host {
    zone             = "ru-central1-a"
    subnet_id        = yandex_vpc_subnet.docker-vm-network-subnet-a.id
    assign_public_ip = true
  }
}

# Создание бакета для Object Storage

resource "yandex_storage_bucket" "uploads_bucket" {
  folder_id = local.target_folder_id
  bucket    = "uploads-bucket"
}

# Создание сервисного аккаунта для Object Storage

resource "yandex_iam_service_account" "uploader-sa" {
  name      = "uploader"
  folder_id = local.target_folder_id
}

resource "yandex_resourcemanager_folder_iam_member" "uploader-sa-role-storage-editor" {
  folder_id = local.target_folder_id
  role      = "storage.editor"
  member    = "serviceAccount:${yandex_iam_service_account.uploader-sa.id}"
}


# Создание кластера Kubernetes

resource "yandex_vpc_subnet" "k8s-subnet" {
  description    = "Subnet in ru-central1-a availability zone"
  name           = "k8s-subnet"
  zone           = "ru-central1-a"
  network_id     = yandex_vpc_network.docker-vm-network.id
  v4_cidr_blocks = [local.zone_a_v4_cidr_blocks]
  folder_id      = local.target_folder_id
}

resource "yandex_vpc_security_group" "k8s-cluster-nodegroup-traffic" {
  folder_id   = local.target_folder_id
  name        = "k8s-cluster-nodegroup-traffic"
  description = "Правила группы разрешают служебный трафик для кластера и групп узлов. Примените ее к кластеру и группам узлов."
  network_id  = yandex_vpc_network.docker-vm-network.id
  ingress {
    description       = "Правило для проверок состояния сетевого балансировщика нагрузки."
    from_port         = 0
    to_port           = 65535
    protocol          = "TCP"
    predefined_target = "loadbalancer_healthchecks"
  }
  ingress {
    description       = "Правило для входящего служебного трафика между мастером и узлами."
    from_port         = 0
    to_port           = 65535
    protocol          = "ANY"
    predefined_target = "self_security_group"
  }
  ingress {
    description    = "Правило для проверки работоспособности узлов с помощью ICMP-запросов из подсетей внутри Yandex Cloud."
    protocol       = "ICMP"
    v4_cidr_blocks = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
  }
  egress {
    description       = "Правило для исходящего служебного трафика между мастером и узлами."
    from_port         = 0
    to_port           = 65535
    protocol          = "ANY"
    predefined_target = "self_security_group"
  }
}

resource "yandex_vpc_security_group" "k8s-nodegroup-traffic" {
  folder_id   = local.target_folder_id
  name        = "k8s-nodegroup-traffic"
  description = "Правила группы разрешают служебный трафик для групп узлов. Примените ее к группам узлов."
  network_id  = yandex_vpc_network.docker-vm-network.id
  ingress {
    description    = "Правило для входящего трафика, разрешающее передачу трафика между подами и сервисами."
    from_port      = 0
    to_port        = 65535
    protocol       = "ANY"
    v4_cidr_blocks = [local.master_v4_cidr_blocks, local.node_v4_cidr_blocks]
  }
  egress {
    description    = "Правило для исходящего трафика, разрешающее узлам в группе узлов подключаться к внешним ресурсам."
    from_port      = 0
    to_port        = 65535
    protocol       = "ANY"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "yandex_vpc_security_group" "k8s-services-access" {
  folder_id   = local.target_folder_id
  name        = "k8s-services-access"
  description = "Правила группы разрешают подключение к сервисам из интернета. Примените ее к группам узлов."
  network_id  = yandex_vpc_network.docker-vm-network.id
  ingress {
    description    = "Правило для входящего трафика, разрешающее подключение к сервисам."
    from_port      = 30000
    to_port        = 32767
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "yandex_vpc_security_group" "k8s-ssh-access" {
  folder_id   = local.target_folder_id
  name        = "k8s-ssh-access"
  description = "Правила группы разрешают подключение к узлам по SSH. Примените ее к группам узлов."
  network_id  = yandex_vpc_network.docker-vm-network.id
  ingress {
    description    = "Правило для входящего трафика, разрешающее подключение к узлам по SSH."
    port           = 22
    protocol       = "TCP"
    v4_cidr_blocks = ["85.32.32.22/32"]
  }
}

resource "yandex_vpc_security_group" "k8s-cluster-traffic" {
  folder_id   = local.target_folder_id
  name        = "k8s-cluster-traffic"
  description = "Правила группы разрешают трафик для кластера. Примените ее к кластеру."
  network_id  = yandex_vpc_network.docker-vm-network.id
  ingress {
    description    = "Правило для входящего трафика, разрешающее доступ к API Kubernetes (порт 443)."
    port           = 443
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description    = "Правило для входящего трафика, разрешающее доступ к API Kubernetes (порт 6443)."
    port           = 6443
    protocol       = "TCP"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    description    = "Правило для исходящего трафика, разрешающее передачу трафика между мастером и подами metric-server."
    port           = 4443
    protocol       = "TCP"
    v4_cidr_blocks = [local.master_v4_cidr_blocks]
  }
  egress {
    description    = "Правило для исходящего трафика, разрешающее подключение мастера к NTP-серверам для синхронизации времени."
    port           = 123
    protocol       = "UDP"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "yandex_iam_service_account" "k8s-sa" {
  description = "Service account for Kubernetes cluster"
  name        = "k8s-service-account"
  folder_id   = local.target_folder_id
}

# Assign "editor" role to Kubernetes service account
resource "yandex_resourcemanager_folder_iam_binding" "editor" {
  folder_id = local.target_folder_id
  role      = "editor"
  members = [
    "serviceAccount:${yandex_iam_service_account.k8s-sa.id}"
  ]
}

# Assign "container-registry.images.puller" role to Kubernetes service account
resource "yandex_resourcemanager_folder_iam_binding" "images-puller" {
  folder_id = local.target_folder_id
  role      = "container-registry.images.puller"
  members = [
    "serviceAccount:${yandex_iam_service_account.k8s-sa.id}"
  ]
}

resource "yandex_kubernetes_cluster" "k8s-cluster" {
  description        = "Managed Service for Kubernetes cluster"
  name               = "k8s-cluster"
  network_id         = yandex_vpc_network.docker-vm-network.id
  cluster_ipv4_range = local.master_v4_cidr_blocks
  service_ipv4_range = local.node_v4_cidr_blocks
  folder_id          = local.target_folder_id

  master {
    version = local.k8s_version
    master_location {
      zone      = yandex_vpc_subnet.k8s-subnet.zone
      subnet_id = yandex_vpc_subnet.k8s-subnet.id
    }

    public_ip = true

    security_group_ids = [
      yandex_vpc_security_group.k8s-cluster-nodegroup-traffic.id,
      yandex_vpc_security_group.k8s-cluster-traffic.id
    ]
  }
  service_account_id      = yandex_iam_service_account.k8s-sa.id # Cluster service account ID
  node_service_account_id = yandex_iam_service_account.k8s-sa.id # Node group service account ID
  depends_on = [
    yandex_resourcemanager_folder_iam_binding.editor,
    yandex_resourcemanager_folder_iam_binding.images-puller
  ]
}

resource "yandex_kubernetes_node_group" "k8s-node-group" {
  description = "Node group for the Managed Service for Kubernetes cluster"
  name        = "k8s-node-group"
  cluster_id  = yandex_kubernetes_cluster.k8s-cluster.id
  version     = local.k8s_version

  scale_policy {
    auto_scale {
      min     = 1
      max     = 5
      initial = 1
    }
  }

  allocation_policy {
    location {
      zone = yandex_vpc_subnet.k8s-subnet.zone
    }
  }

  instance_template {
    platform_id = "standard-v2" # Intel Cascade Lake

    metadata = {
      "ssh-keys" = file("/home/yoo/.ssh/ssh-key-1779088009475.pub")
    }

    network_interface {
      nat        = true
      subnet_ids = [yandex_vpc_subnet.k8s-subnet.id]
      security_group_ids = [
        yandex_vpc_security_group.k8s-cluster-nodegroup-traffic.id,
        yandex_vpc_security_group.k8s-nodegroup-traffic.id,
        yandex_vpc_security_group.k8s-services-access.id,
        yandex_vpc_security_group.k8s-ssh-access.id
      ]
    }

    resources {
      memory = 2 # RAM quantity in GB
      cores  = 2 # Number of CPU cores
    }

    boot_disk {
      type = "network-hdd"
      size = 40 # Disk size in GB
    }
  }
}