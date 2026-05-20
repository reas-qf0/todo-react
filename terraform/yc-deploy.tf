# Объявление переменных для конфиденциальных параметров

locals {
  zone             = "ru-central1-a"
  ssh_key_path     = "/home/yoo/.ssh/ssh-key-1779088009475.pub"
  target_folder_id = "b1gni3u9dk10jvj2gbpo"
  registry_name    = "todo-react"
  sa_name          = "yoo"
  network_name     = "network"
  subnet_name      = "subnet"
  vm_name          = "todo-react"
  image_id         = "fd8jqd7hb16epiac8lla"
}

# Настройка провайдера

terraform {
  required_providers {
    yandex    = {
      source  = "yandex-cloud/yandex"
      version = ">= 0.47.0"
    }
  }
}

provider "yandex" {
  zone = local.zone
}

# Создание репозитория Сontainer Registry

resource "yandex_container_registry" "my-registry" {
  name       = local.registry_name
  folder_id  = local.target_folder_id
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
  size     = "10"
  image_id = local.image_id
}

# Создание ВМ

resource "yandex_compute_instance" "docker-vm" {
  name               = local.vm_name
  platform_id        = "standard-v3"
  zone               = local.zone
  service_account_id = "${yandex_iam_service_account.registry-sa.id}"

  resources {
    cores  = 2
    memory = 2
  }

  boot_disk {
    disk_id = yandex_compute_disk.boot-disk.id
  }

  network_interface {
    subnet_id = "${yandex_vpc_subnet.docker-vm-network-subnet-a.id}"
    nat       = true
  }

  metadata = {
    user-data = "${file("user-data.yaml")}"
  }
}