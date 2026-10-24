#!/usr/bin/env bash
# Oracle Cloud Ubuntu(ARM) 서버 첫 설정: Docker 설치, 방화벽(80·443) 열기, 스왑 추가
# 실행: bash setup-server.sh
set -euo pipefail

echo "== Docker 설치"
if ! command -v docker >/dev/null; then
	curl -fsSL https://get.docker.com | sudo sh
	sudo usermod -aG docker "$USER"
fi

echo "== 서버 내부 방화벽에서 80·443 열기 (Oracle Ubuntu 이미지는 기본으로 막혀 있음)"
for port in 80 443; do
	if ! sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; then
		sudo iptables -I INPUT 6 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
	fi
done
sudo apt-get install -y netfilter-persistent >/dev/null
sudo netfilter-persistent save

echo "== 스왑 4GB (빌드 중 메모리 부족 방지)"
if [ ! -f /swapfile ]; then
	sudo fallocate -l 4G /swapfile
	sudo chmod 600 /swapfile
	sudo mkswap /swapfile
	sudo swapon /swapfile
	echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "== 시간대 서울"
sudo timedatectl set-timezone Asia/Seoul

echo "완료. 다시 로그인한 뒤 docker 명령을 쓸 수 있습니다."
