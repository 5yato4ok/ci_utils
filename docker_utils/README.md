# Docker cheat sheet

Usefull command and configurations for docker

## Move docker to custom storage

Helps to save a lot of space on system drive. First stop docker
```bash
systemctl stop docker
```
Then add following value to /etc/docker/daemon.json (create if not exists).
```json
{"data-root" : "/path/to/new/storage"}
```
Copy all data from previous location to new one and start docker.
```bash
mkdir /path/to/new/docker
cp -r /var/liv/docker /path/to/new/docker
mv -r /var/lib/docker /var/lib/docker.old
systemctl start docker
```
Check if docker OK, if so delete backup directory
```bash
rm -rf /var/lib/docker.old
```

## Setup local registry

This command launches local docker registry with backup and without SSL. 
```bash
docker run -d -p 5000:5000 --restart=always -v /local/storage/for/registry:/var/lib/registry --name registry registry:2
```
To allow connections without SSL, need to add following value to /etc/docker/daemon.json
```json
{"insecure-registries" : ["your_registry_ip:5000"]}
```
To pull/push image from/to registry need tto add IP of registry in the name of image. For example:
```bash
docker push regitstry_IP:5000/my_ubuntu
```
Docker registry supports REST-API. 
Get list of images in registry:
```bash
curl http:regitry_ip:5000/v2/_catalog
```

## Usefull commands

Run container in iteractive mode, with host network
```bash
docker run -it --network host ubuntu bash
```
Delete all stopped container, dangled images
```bash
docker system prune
```
Stop all running containers
```bash
docker stop $(docker ps -q)
```
Run docker container in iteractive mode, with mounted local directory.
```bash
docker run -it -v "/mnt/loca/path:/path/in/container" ubuntu bash
```
Run docker container in iteractive mode, with enabled permission to debug process inside container
```bash
docker run --cap-add=SYS_PTRACE --security-opt seccomp=unconfined ubuntu bash
```
Get docker configuration info
```bash
docker info
```
Save container as image
```bash
docker commit <container_id> <image_name>
```
Attach to already running container
```bash
docker exec -it <container_id> bash
```

## Hacks

There are often errors about lack of permission inside container for simple actions, like updating the apt repository, etc. Need to add following command to image to fix that:
```bash
RUN chmod 1777 /tmp
```