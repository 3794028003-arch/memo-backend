$ErrorActionPreference = "Stop"

$image = "liuxiaolong2/memo-backend:latest"       #我要部署哪个 Docker 镜像。
$container = "memo-backend-test"
$network = "memo-backend_default"

Write-Host "1. Pull latest Docker image..."  #去 Docker Hub 下载最新版本。
docker pull $image

Write-Host "2. Remove old backend container..."
docker rm -f $container 2>$null        #把旧版本后端容器删掉。

Write-Host "3. Start new backend container..."
docker run -d `
  --name $container `
  --network $network `
  -p 8080:8080 `
  $image                 #用最新镜像重新启动一个后端容器。

Write-Host "4. Check running containers..."
docker ps

Write-Host "Deployment finished."
