#!/usr/bin/env bash
# app 서버에서 딱 한 번 실행하는 초기 세팅 스크립트.
#   scp -i ~/.ssh/delipot-app.pem infra/bootstrap-app.sh ec2-user@<APP_HOST>:/tmp/
#   ssh -i ~/.ssh/delipot-app.pem ec2-user@<APP_HOST> 'sudo bash /tmp/bootstrap-app.sh'
#
# 하는 일: 배포 디렉토리 생성, nginx 설치/설정, 백엔드 systemd 유닛 등록.
# JDK 21은 이미 설치돼 있다고 가정한다 (없으면 아래 JAVA 확인에서 멈춘다).
set -euo pipefail

SERVICE_USER=delipot
BACKEND_DIR=/opt/delipot/backend
FRONTEND_DIR=/var/www/delipot
ENV_FILE=/etc/delipot/backend.env

if [[ $EUID -ne 0 ]]; then
	echo "sudo 로 실행해라." >&2
	exit 1
fi

echo "== java 확인"
JAVA_BIN=$(command -v java || true)
if [[ -z "$JAVA_BIN" ]]; then
	echo "java 가 PATH 에 없다. JDK 21 을 먼저 설치해라." >&2
	exit 1
fi
"$JAVA_BIN" -version

echo "== 서비스 계정/디렉토리"
# 백엔드를 돌리는 계정(delipot)과 배포하는 계정(CD_USER)을 분리한다.
CD_USER=${SUDO_USER:-ec2-user}
id -u "$SERVICE_USER" >/dev/null 2>&1 || useradd --system --shell /sbin/nologin "$SERVICE_USER"
mkdir -p "$BACKEND_DIR/releases" "$FRONTEND_DIR/releases" /etc/delipot

# 배포 계정이 sudo 없이 파일을 넣을 수 있게: 소유자는 배포 계정, 그룹은 서비스 계정
chown -R "$CD_USER:$SERVICE_USER" /opt/delipot
chmod -R 2775 /opt/delipot
chown -R "$CD_USER:$CD_USER" "$FRONTEND_DIR"
chmod -R 755 "$FRONTEND_DIR"

echo "== 환경변수 파일 (없을 때만 템플릿 생성)"
if [[ ! -f "$ENV_FILE" ]]; then
	cat > "$ENV_FILE" <<'ENV'
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<DB_HOST>:3306/delipot?characterEncoding=UTF-8&serverTimezone=Asia/Seoul
DB_USERNAME=delipot
DB_PASSWORD=<변경할것>
CORS_ALLOWED_ORIGINS=http://<APP_PUBLIC_HOST>
ENV
	chmod 600 "$ENV_FILE"
	echo "  → $ENV_FILE 을 직접 채워라 (DB 접속정보). CI 는 이 파일을 건드리지 않는다."
fi

echo "== nginx 설치"
if ! command -v nginx >/dev/null 2>&1; then
	if command -v dnf >/dev/null 2>&1; then dnf install -y nginx
	elif command -v yum >/dev/null 2>&1; then yum install -y nginx
	else apt-get update && apt-get install -y nginx
	fi
fi

echo "== nginx 설정"
NGINX_CONF_DIR=/etc/nginx/conf.d
mkdir -p "$NGINX_CONF_DIR"
cat > "$NGINX_CONF_DIR/delipot.conf" <<'NGINX'
server {
	listen 80 default_server;
	server_name _;

	# 프론트 정적 파일 (CD 가 releases/<sha> 를 만들고 current 심링크를 바꾼다)
	root /var/www/delipot/current;
	index index.html;

	# SPA: 새로고침해도 index.html 로 떨어지게
	location / {
		try_files $uri $uri/ /index.html;
	}

	# 해시 붙은 정적 자산은 오래 캐시
	location /assets/ {
		expires 1y;
		add_header Cache-Control "public, immutable";
	}

	# API 는 같은 오리진으로 백엔드에 넘긴다 → 브라우저 CORS 없음
	location /api/ {
		proxy_pass http://127.0.0.1:8080;
		proxy_http_version 1.1;
		proxy_set_header Host $host;
		proxy_set_header X-Real-IP $remote_addr;
		proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
		proxy_set_header X-Forwarded-Proto $scheme;
		proxy_read_timeout 60s;

		# SSE 쓸 때 버퍼링 때문에 이벤트가 늦게 나가는 것 방지
		proxy_buffering off;
	}
}
NGINX

# 첫 배포 전에도 nginx 가 뜨도록 빈 current 를 만들어 둔다
if [[ ! -e "$FRONTEND_DIR/current" ]]; then
	mkdir -p "$FRONTEND_DIR/releases/bootstrap"
	echo '<!doctype html><title>delipot</title>아직 배포 전이다.' > "$FRONTEND_DIR/releases/bootstrap/index.html"
	ln -sfn "$FRONTEND_DIR/releases/bootstrap" "$FRONTEND_DIR/current"
	chown -R "$CD_USER:$CD_USER" "$FRONTEND_DIR"
fi

echo "== 백엔드 systemd 유닛"
cat > /etc/systemd/system/delipot-backend.service <<UNIT
[Unit]
Description=Delipot backend (Spring Boot)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
EnvironmentFile=$ENV_FILE
ExecStart=$JAVA_BIN -jar $BACKEND_DIR/current.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

echo "== CD 계정 sudo 권한 (딱 필요한 명령만)"
# 파일 배치는 소유권으로 해결했으므로 sudo 는 서비스 제어/로그 조회에만 준다.
SYSTEMCTL=$(command -v systemctl)
JOURNALCTL=$(command -v journalctl)
cat > /etc/sudoers.d/delipot-deploy <<SUDOERS
$CD_USER ALL=(root) NOPASSWD: $SYSTEMCTL restart delipot-backend, $SYSTEMCTL status delipot-backend, $SYSTEMCTL reload nginx, $JOURNALCTL -u delipot-backend *
SUDOERS
chmod 440 /etc/sudoers.d/delipot-deploy

systemctl daemon-reload
systemctl enable nginx delipot-backend
systemctl restart nginx

echo
echo "완료. 다음으로 할 일:"
echo "  1) $ENV_FILE 의 DB 접속정보를 채운다"
echo "  2) GitHub Secrets(EC2_HOST/EC2_USER/EC2_SSH_KEY)를 등록한다"
echo "  3) main 에 머지하면 CD 가 돈다 (백엔드 jar 은 첫 배포 때 생긴다)"
